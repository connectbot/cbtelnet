/*
 * Copyright 2026 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.connectbot.telnetlib

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/**
 * A duplex byte transport. Positive reads make progress; -1 is EOF, zero is only
 * legal for a zero-length request. Write must finish the entire slice. Close must
 * promptly interrupt a suspended read/write and be safe to call repeatedly.
 */
interface TelnetTransport {
    /** Local socket endpoint, or null when this transport has no socket metadata. */
    fun getLocalSocketAddress(): java.net.InetSocketAddress? = null

    suspend fun read(
        destination: ByteArray,
        offset: Int,
        length: Int,
    ): Int

    suspend fun write(
        source: ByteArray,
        offset: Int,
        length: Int,
    )

    suspend fun flush()

    suspend fun close()
}

/** Ordered, single-consumer input and concurrent suspend output operations. */
interface TelnetSession {
    /** Local socket endpoint captured when the session opens. */
    fun getLocalSocketAddress(): java.net.InetSocketAddress? = null

    val state: StateFlow<TelnetSnapshot>

    /** @throws IllegalStateException when another receive call is in progress. */
    suspend fun receive(): TelnetOutcome<TelnetInput?>

    /** @throws IllegalArgumentException when the byte slice is invalid. */
    suspend fun write(
        data: ByteArray,
        offset: Int = 0,
        length: Int = data.size - offset,
    ): TelnetOutcome<Unit>

    suspend fun sendCommand(command: TelnetCommand): TelnetOutcome<Unit>

    suspend fun carriageReturn(): TelnetOutcome<Unit>

    suspend fun requestOption(
        option: TelnetOption,
        direction: TelnetDirection,
        enable: Boolean,
    ): TelnetOutcome<Unit>

    /** @throws IllegalArgumentException when either dimension is outside 0..65535. */
    suspend fun resize(
        columns: Int,
        rows: Int,
    ): TelnetOutcome<Unit>

    suspend fun flush(): TelnetOutcome<Unit>

    suspend fun close(): TelnetOutcome<Unit>
}

/**
 * Opens a child session over an already connected transport. The session owns
 * and closes that transport. Each large write is accepted in independent bounded
 * chunks, allowing negotiation and competing operations between chunks.
 */
fun openTelnetSession(
    scope: CoroutineScope,
    transport: TelnetTransport,
    config: TelnetConfig = TelnetConfig(),
    workerContext: CoroutineContext = scope.coroutineContext.minusKey(Job),
): TelnetSession {
    scope.coroutineContext.ensureActive()
    return CoroutineTelnetSession(scope, transport, config, workerContext)
}

/** A weighted queue budget. Reservations include producer copies and in-flight output. */
private class ByteBudget(
    private val capacity: Int,
) {
    private var used = 0
    private val changed = MutableStateFlow(0L)

    fun tryAcquire(size: Int): Lease? =
        synchronized(this) {
            if (size > capacity - used) return@synchronized null
            used += size
            Lease {
                synchronized(this) {
                    used -= size
                    changed.value++
                }
            }
        }

    suspend fun acquire(size: Int): Lease {
        require(size <= capacity)
        while (true) {
            currentCoroutineContext().ensureActive()
            val version = changed.value
            tryAcquire(size)?.let { return it }
            changed.first { it != version }
        }
    }
}

private class Lease(
    private val onRelease: () -> Unit,
) {
    private val released = AtomicBoolean()

    fun release() {
        if (released.compareAndSet(false, true)) onRelease()
    }
}

private sealed interface Operation {
    data class Write(
        val bytes: ByteArray,
    ) : Operation

    data class Command(
        val command: TelnetCommand,
    ) : Operation

    data object CarriageReturn : Operation

    data class Option(
        val option: TelnetOption,
        val direction: TelnetDirection,
        val enable: Boolean,
    ) : Operation

    data class Resize(
        val columns: Int,
        val rows: Int,
    ) : Operation

    data object Flush : Operation
}

private class Request(
    val operation: Operation,
    val lease: Lease,
) {
    val accepted = AtomicInteger(0) // 0 waiting, 1 accepted, 2 cancelled before acceptance
    val done = CompletableDeferred<Unit>()
}

private class ReceiveRequest {
    val result = CompletableDeferred<TelnetInput?>()
    val consumed = AtomicBoolean()
    var offered = false
    var input: TelnetInput? = null
}

private data class WriteTask(
    val frames: List<ByteArray>,
    val lease: Lease,
    val done: CompletableDeferred<Unit>? = null,
    val flush: Boolean = false,
    val closing: Boolean = false,
    val resize: Boolean = false,
)

private sealed interface WorkerEvent {
    data class Written(
        val task: WriteTask,
        val failure: Throwable? = null,
    ) : WorkerEvent

    data class CharsetTimeout(
        val generation: Long,
    ) : WorkerEvent

    data object CloseTimeout : WorkerEvent
}

private sealed interface WireMessage {
    data class Data(
        val bytes: ByteArray,
    ) : WireMessage

    data object End : WireMessage

    data class Failure(
        val failure: Throwable,
    ) : WireMessage
}

private class CoroutineTelnetSession(
    scope: CoroutineScope,
    private val transport: TelnetTransport,
    private val config: TelnetConfig,
    workerContext: CoroutineContext,
) : TelnetSession {
    private val localEndpoint = transport.getLocalSocketAddress()

    override fun getLocalSocketAddress(): java.net.InetSocketAddress? = localEndpoint

    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val sessionScope = CoroutineScope(scope.coroutineContext + job)
    private val engine = TelnetEngine(config)
    private val mutableState = MutableStateFlow(engine.snapshot)
    override val state: StateFlow<TelnetSnapshot> = mutableState.asStateFlow()
    private val outputBudget = ByteBudget(config.outputBudgetBytes)
    private val controlBudget = ByteBudget(config.controlBudgetBytes)
    private val operations = Channel<Request>(Channel.RENDEZVOUS)
    private val receives = Channel<ReceiveRequest>(Channel.RENDEZVOUS)
    private val receiveCompleted = Channel<ReceiveRequest>(1)
    private val wire = Channel<WireMessage>(1)
    private val writes = Channel<WriteTask>(Channel.UNLIMITED)
    private val events = Channel<WorkerEvent>(Channel.UNLIMITED)
    private val closeSignal = Channel<Unit>(Channel.CONFLATED)
    private val closeRequested = AtomicBoolean()
    private val receiving = AtomicBoolean()
    private val shutdown = CompletableDeferred<Unit>()
    private val pending = ArrayDeque<TelnetInput>()
    private var pendingBytes = 0
    private val inFlight = mutableSetOf<WriteTask>()
    private val waitingForCharset = ArrayDeque<Request>()

    @Volatile private var finished = false
    private var terminal: TelnetSnapshot? = null
    private var receiver: ReceiveRequest? = null
    private var charsetTimer: Job? = null
    private var charsetGeneration = 0L
    private var closeTimer: Job? = null

    private val writer =
        sessionScope.launch(workerContext.minusKey(Job)) {
            var next: WriteTask? = null
            while (true) {
                val task = next ?: writes.receiveCatching().getOrNull() ?: break
                next = null
                val batch = mutableListOf(task)
                if (task.resize) {
                    while (true) {
                        val following = writes.tryReceive().getOrNull() ?: break
                        if (!following.resize) {
                            next = following
                            break
                        }
                        batch.add(following)
                    }
                }
                try {
                    batch.last().frames.forEach { transport.write(it, 0, it.size) }
                    if (task.flush) transport.flush()
                    batch.forEach { events.send(WorkerEvent.Written(it)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    batch.forEach { events.send(WorkerEvent.Written(it, e)) }
                    break
                }
            }
        }
    private val reader =
        sessionScope.launch(workerContext.minusKey(Job)) {
            try {
                val scratch = ByteArray(TelnetEngine.CHUNK_SIZE)
                while (true) {
                    val count = transport.read(scratch, 0, scratch.size)
                    if (count == -1) {
                        wire.send(WireMessage.End)
                        break
                    }
                    if (count !in 1..scratch.size) throw TelnetTransportException("Transport returned an invalid read count: $count")
                    wire.send(WireMessage.Data(scratch.copyOf(count)))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                wire.send(WireMessage.Failure(e))
            }
        }

    init {
        sessionScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                process(engine.start())
                ownerLoop()
            } catch (e: CancellationException) {
                terminate(TelnetStatus.CANCELLED, null)
                throw e
            } catch (e: Exception) {
                terminate(TelnetStatus.FAILED, asTelnetFailure(e))
            } finally {
                finished = true
                operations.cancel()
                receives.cancel()
                withContext(NonCancellable) {
                    reader.cancel()
                    writer.cancel()
                    runCatching { transport.close() }
                    charsetTimer?.cancel()
                    closeTimer?.cancel()
                    val failure = mutableState.value.failure?.let(::TelnetException) ?: TelnetClosedException("Session closed")
                    inFlight.forEach {
                        it.lease.release()
                        it.done?.completeExceptionally(failure)
                    }
                    waitingForCharset.forEach {
                        it.lease.release()
                        it.done.completeExceptionally(failure)
                    }
                    receiver?.let { completeTerminal(it.result) }
                    shutdown.complete(Unit)
                }
                job.complete()
            }
        }
    }

    override suspend fun receive(): TelnetOutcome<TelnetInput?> {
        if (!receiving.compareAndSet(
                false,
                true,
            )
        ) {
            error("Only one receive consumer is permitted")
        }
        return try {
            boundary {
                currentCoroutineContext().ensureActive()
                if (finished) return@boundary terminalInput()
                val request = ReceiveRequest()
                try {
                    try {
                        receives.send(request)
                    } catch (e: CancellationException) {
                        currentCoroutineContext().ensureActive()
                        return@boundary terminalInput()
                    }
                    val value = request.result.await()
                    request.consumed.set(true)
                    value
                } finally {
                    if (request.result.isActive) request.result.cancel()
                    receiveCompleted.trySend(request)
                }
            }
        } finally {
            receiving.set(false)
        }
    }

    override suspend fun write(
        data: ByteArray,
        offset: Int,
        length: Int,
    ): TelnetOutcome<Unit> {
        checkSlice(data.size, offset, length)
        return boundary {
            ensureOpen()
            var position = offset
            val end = offset + length
            while (position < end) {
                val count = minOf(TelnetEngine.CHUNK_SIZE, end - position)
                submit(maxOf(256, 2 * count + 2)) { Operation.Write(data.copyOfRange(position, position + count)) }
                position += count
            }
        }
    }

    override suspend fun sendCommand(command: TelnetCommand): TelnetOutcome<Unit> {
        if (command ==
            TelnetCommand.DATA_MARK
        ) {
            return failure(TelnetFailureCode.UNSUPPORTED_OPERATION, "TCP urgent-data SYNCH is not supported")
        }
        return boundary { submit { Operation.Command(command) } }
    }

    override suspend fun carriageReturn(): TelnetOutcome<Unit> = boundary { submit { Operation.CarriageReturn } }

    override suspend fun requestOption(
        option: TelnetOption,
        direction: TelnetDirection,
        enable: Boolean,
    ): TelnetOutcome<Unit> {
        if (enable &&
            !when (option) {
                TelnetOption.ECHO -> direction == TelnetDirection.REMOTE
                TelnetOption.TTYPE, TelnetOption.NAWS -> direction == TelnetDirection.LOCAL
                TelnetOption.EOR -> direction == TelnetDirection.REMOTE && config.consumeRecords
                TelnetOption.CHARSET -> config.charset != null
                else -> true
            }
        ) {
            return failure(TelnetFailureCode.UNSUPPORTED_OPERATION, "Unsupported option direction or missing configuration")
        }
        return boundary { submit { Operation.Option(option, direction, enable) } }
    }

    override suspend fun resize(
        columns: Int,
        rows: Int,
    ): TelnetOutcome<Unit> {
        checkDimensions(columns, rows)
        return boundary { submit { Operation.Resize(columns, rows) } }
    }

    override suspend fun flush(): TelnetOutcome<Unit> = boundary { submit { Operation.Flush } }

    override suspend fun close(): TelnetOutcome<Unit> =
        boundary {
            if (closeRequested.compareAndSet(false, true)) closeSignal.trySend(Unit)
            shutdown.await()
            state.value.failure?.let { throw TelnetException(it) }
        }

    private fun failure(
        code: TelnetFailureCode,
        message: String,
    ): TelnetOutcome.Failure = TelnetOutcome.Failure(TelnetFailure(code, message))

    private suspend fun <T> boundary(block: suspend () -> T): TelnetOutcome<T> =
        try {
            TelnetOutcome.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: TelnetException) {
            TelnetOutcome.Failure(e.error)
        } catch (e: Exception) {
            failure(TelnetFailureCode.INTERNAL_ERROR, e.message ?: "Unexpected library failure")
        }

    private suspend fun submit(
        reservation: Int = 1024,
        operation: () -> Operation,
    ) {
        currentCoroutineContext().ensureActive()
        ensureOpen()
        val lease = outputBudget.acquire(reservation)
        val request =
            try {
                Request(operation(), lease)
            } catch (e: Throwable) {
                lease.release()
                throw e
            }
        try {
            ensureOpen()
            operations.send(request)
            request.done.await()
        } catch (e: Throwable) {
            if (request.accepted.compareAndSet(0, 2)) lease.release()
            if (e is CancellationException) {
                currentCoroutineContext().ensureActive()
                ensureOpen()
            }
            throw e
        }
    }

    private fun ensureOpen() {
        if (finished || closeRequested.get() || state.value.status != TelnetStatus.OPEN) {
            throw state.value.failure?.let(::TelnetException) ?: TelnetClosedException("Session is ${state.value.status}")
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun ownerLoop() {
        var closing = false
        while (true) {
            receiver?.let { current ->
                if (!current.offered && current.result.isActive) {
                    if (pending.isNotEmpty()) {
                        current.offered = true
                        current.input = pending.first()
                        current.result.complete(current.input)
                    } else if (terminal != null) {
                        current.offered = true
                        completeTerminal(current.result)
                    }
                }
            }
            if (terminal != null && pending.isEmpty() && receiver == null) return
            if (!engine.snapshot.charsetPending && waitingForCharset.isNotEmpty() && !closing && terminal == null) {
                acceptOperation(waitingForCharset.removeFirst())
                continue
            }
            try {
                select<Unit> {
                    receiveCompleted.onReceive { completed ->
                        if (completed === receiver) {
                            if (completed.consumed.get() && completed.input != null) {
                                val input = pending.removeFirst()
                                pendingBytes -= (input as? TelnetInput.Data)?.bytes?.size ?: 0
                            }
                            receiver = null
                        }
                    }
                    if (receiver == null) receives.onReceive { if (!it.result.isCancelled) receiver = it }
                    if (!closing) {
                        closeSignal.onReceive {
                            closing = true
                            if (terminal != null) {
                                discardPendingExceptOffered()
                            } else {
                                reader.cancel()
                                while (waitingForCharset.isNotEmpty()) {
                                    val req = waitingForCharset.removeFirst()
                                    req.lease.release()
                                    req.done.completeExceptionally(TelnetClosedException("Closed during CHARSET negotiation"))
                                }
                                val result = engine.flush()
                                enqueue(
                                    result,
                                    controlBudget.tryAcquire(1024) ?: throw TelnetProtocolException("Control output saturated"),
                                    closing = true,
                                    flush = true,
                                )
                                closeTimer =
                                    sessionScope.launch {
                                        delay(config.closeTimeoutMillis)
                                        events.send(WorkerEvent.CloseTimeout)
                                    }
                            }
                        }
                    }
                    if (terminal == null && !closing) {
                        operations.onReceive { req ->
                            if (req.accepted.compareAndSet(0, 1)) {
                                if (engine.snapshot.charsetPending &&
                                    (
                                        req.operation is Operation.Write || req.operation == Operation.CarriageReturn ||
                                            req.operation == Operation.Flush
                                    )
                                ) {
                                    waitingForCharset.addLast(req)
                                } else {
                                    acceptOperation(req)
                                }
                            }
                        }
                        if (pendingBytes <= config.inputBudgetBytes - TelnetEngine.CHUNK_SIZE - 1 &&
                            pending.size <= config.maxPendingEvents - TelnetEngine.CHUNK_SIZE
                        ) {
                            wire.onReceive { message ->
                                when (message) {
                                    is WireMessage.Data -> {
                                        process(engine.feed(message.bytes))
                                    }

                                    WireMessage.End -> {
                                        process(engine.finish())
                                        terminate(TelnetStatus.PEER_EOF, null)
                                    }

                                    is WireMessage.Failure -> {
                                        terminate(TelnetStatus.FAILED, asTelnetFailure(message.failure))
                                    }
                                }
                            }
                        }
                    }
                    if (terminal == null) {
                        events.onReceive { event ->
                            when (event) {
                                WorkerEvent.CloseTimeout -> {
                                    terminate(TelnetStatus.LOCAL_CLOSED, null)
                                }

                                is WorkerEvent.CharsetTimeout -> {
                                    if (event.generation == charsetGeneration) process(engine.timeoutCharset())
                                }

                                is WorkerEvent.Written -> {
                                    inFlight.remove(event.task)
                                    event.task.lease.release()
                                    if (event.failure != null) {
                                        val error = asTelnetFailure(event.failure)
                                        event.task.done?.completeExceptionally(error)
                                        terminate(TelnetStatus.FAILED, error)
                                    } else {
                                        event.task.done?.complete(Unit)
                                        if (event.task.closing) terminate(TelnetStatus.LOCAL_CLOSED, null)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                terminate(TelnetStatus.FAILED, asTelnetFailure(e))
            }
        }
    }

    private suspend fun acceptOperation(request: Request) {
        try {
            val result =
                when (val op = request.operation) {
                    is Operation.Write -> engine.write(op.bytes)
                    is Operation.Command -> engine.sendCommand(op.command)
                    Operation.CarriageReturn -> engine.carriageReturn()
                    is Operation.Option -> engine.requestOption(op.option, op.direction, op.enable)
                    is Operation.Resize -> engine.resize(op.columns, op.rows)
                    Operation.Flush -> engine.flush()
                }
            if (result.failure != null && engine.snapshot.status != TelnetStatus.FAILED) {
                request.lease.release()
                request.done.completeExceptionally(TelnetException(result.failure))
                return
            }
            enqueue(
                result,
                request.lease,
                request.done,
                flush = request.operation == Operation.Flush,
                resize = request.operation is Operation.Resize,
            )
            result.failure?.let { terminate(TelnetStatus.FAILED, TelnetException(it)) }
        } catch (e: Exception) {
            request.lease.release()
            request.done.completeExceptionally(e)
            if (e !is IllegalArgumentException) terminate(TelnetStatus.FAILED, asTelnetFailure(e))
        }
    }

    private suspend fun process(result: TelnetResult) {
        val frames = result.effects.filterIsInstance<TelnetEffect.Outbound>()
        val size = frames.sumOf { it.bytes.size }
        val lease =
            if (frames.isEmpty()) {
                Lease {}
            } else {
                controlBudget.tryAcquire(maxOf(64, size))
                    ?: throw TelnetProtocolException(
                        "Negotiation output exceeded the reserved control budget",
                        TelnetFailureCode.BUFFER_LIMIT,
                    )
            }
        enqueue(result, lease)
        result.failure?.let { terminate(TelnetStatus.FAILED, TelnetException(it)) }
    }

    private fun enqueue(
        result: TelnetResult,
        lease: Lease,
        done: CompletableDeferred<Unit>? = null,
        flush: Boolean = false,
        closing: Boolean = false,
        resize: Boolean = false,
    ) {
        val frames = ArrayList<ByteArray>()
        result.effects.forEach { effect ->
            when (effect) {
                is TelnetEffect.Input -> {
                    pending.addLast(effect.value)
                    pendingBytes += (effect.value as? TelnetInput.Data)?.bytes?.size ?: 0
                }

                is TelnetEffect.Outbound -> {
                    frames.add(effect.bytes)
                }

                is TelnetEffect.StateChanged -> {
                    mutableState.value = effect.snapshot
                }
            }
        }
        mutableState.value = engine.snapshot
        if (engine.snapshot.charsetPending) {
            if (charsetTimer == null) {
                val generation = ++charsetGeneration
                charsetTimer =
                    sessionScope.launch {
                        delay(config.charsetTimeoutMillis)
                        events.send(WorkerEvent.CharsetTimeout(generation))
                    }
            }
        } else {
            charsetTimer?.cancel()
            charsetTimer = null
            charsetGeneration++
        }
        if (frames.isEmpty() && done == null && !flush && !closing) {
            lease.release()
        } else {
            val sizeOnly =
                resize && frames.size == 1 &&
                    frames[0].let {
                        it.size >= 3 && it[0] == 255.toByte() && it[1] == 250.toByte() && it[2] == 31.toByte()
                    }
            val task = WriteTask(frames, lease, done, flush, closing, sizeOnly)
            inFlight.add(task)
            check(writes.trySend(task).isSuccess)
        }
    }

    private suspend fun terminate(
        status: TelnetStatus,
        failure: TelnetException?,
    ) {
        if (terminal != null) return
        terminal = engine.snapshot.copy(status = status, failure = failure?.error)
        mutableState.value = terminal!!
        reader.cancel()
        writer.cancel()
        charsetTimer?.cancel()
        closeTimer?.cancel()
        val closeError = withContext(NonCancellable) { runCatching { transport.close() }.exceptionOrNull() }
        if (closeError != null && failure == null && status != TelnetStatus.CANCELLED) {
            terminal = terminal!!.copy(status = TelnetStatus.FAILED, failure = asTelnetFailure(closeError).error)
            mutableState.value = terminal!!
        }
        if (status == TelnetStatus.LOCAL_CLOSED) discardPendingExceptOffered()
        val closed = mutableState.value.failure?.let(::TelnetException) ?: TelnetClosedException("Session is $status")
        inFlight.forEach {
            it.lease.release()
            it.done?.completeExceptionally(closed)
        }
        inFlight.clear()
        waitingForCharset.forEach {
            it.lease.release()
            it.done.completeExceptionally(closed)
        }
        waitingForCharset.clear()
        shutdown.complete(Unit)
    }

    private fun discardPendingExceptOffered() {
        val offered = receiver?.input
        pending.clear()
        pendingBytes = 0
        if (offered != null) {
            pending.addLast(offered)
            pendingBytes = (offered as? TelnetInput.Data)?.bytes?.size ?: 0
        }
    }

    private fun asTelnetFailure(error: Throwable): TelnetException =
        error as? TelnetException ?: TelnetTransportException("Telnet transport failed", error)

    private fun completeTerminal(target: CompletableDeferred<TelnetInput?>) {
        val failure = mutableState.value.failure
        if (failure != null) target.completeExceptionally(TelnetException(failure)) else target.complete(null)
    }

    private fun terminalInput(): TelnetInput? {
        if (state.value.status == TelnetStatus.CANCELLED) throw CancellationException("Telnet session cancelled")
        state.value.failure?.let { throw TelnetException(it) }
        return null
    }
}
