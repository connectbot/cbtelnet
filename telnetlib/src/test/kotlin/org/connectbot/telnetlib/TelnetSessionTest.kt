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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TelnetSessionTest {
    @Test
    fun `session exposes socket endpoint after closing`() =
        runTest {
            val address = InetSocketAddress("127.0.0.1", 12345)
            val session = openTelnetSession(backgroundScope, FakeTransport(address))
            assertEquals(address, session.getLocalSocketAddress())
            session.close().value()
            assertEquals(address, session.getLocalSocketAddress())
        }

    @Test
    fun `session without socket metadata exposes null`() =
        runTest {
            val session = openTelnetSession(backgroundScope, FakeTransport())
            assertNull(session.getLocalSocketAddress())
            session.close().value()
        }

    @Test
    fun `close times out a stalled writer and completes accepted callers`() =
        runTest {
            val backend = FakeTransport().also { it.writeGate = CompletableDeferred() }
            val session = openTelnetSession(backgroundScope, backend, TelnetConfig(closeTimeoutMillis = 100))
            val writing = async { session.write(bytes(65)) }
            runCurrent()
            val closing = async { session.close() }
            runCurrent()
            assertFalse(closing.isCompleted)
            advanceTimeBy(100)
            runCurrent()
            closing.await().value()
            assertEquals(TelnetFailureCode.CLOSED, assertIs<TelnetOutcome.Failure>(writing.await()).error.code)
            assertTrue(backend.closed)
        }

    @Test
    fun `close without draining peer input completes the owning scope`() =
        runTest {
            val backend = FakeTransport()
            val parent = Job()
            val session = openTelnetSession(CoroutineScope(coroutineContext + parent), backend, TelnetConfig(maxPendingEvents = 16384))
            backend.input.send(bytes(65))
            backend.input.send(null)
            runCurrent()
            assertEquals(TelnetStatus.PEER_EOF, session.state.value.status)
            session.close().value()
            runCurrent()
            parent.complete()
            parent.join()
            assertNull(session.receive().value())
        }

    @Test
    fun `cancelled receive preserves an offered input event`() =
        runTest {
            val backend = FakeTransport()
            val session = openTelnetSession(backgroundScope, backend)
            val receiving = launch { session.receive() }
            runCurrent()
            backend.input.send(bytes(65))
            // Reader/owner can offer a value before the receiver resumes.
            receiving.cancelAndJoin()
            assertContentEquals(bytes(65), assertIs<TelnetInput.Data>(session.receive().value()).bytes)
            session.close().value()
        }

    @Test
    fun `resize coalescing retains carriage return and does not cross flush`() =
        runTest {
            val backend = FakeTransport()
            val session = openTelnetSession(backgroundScope, backend)
            backend.input.send(bytes(255, 253, 31))
            runCurrent()
            backend.operations.clear()
            backend.writeGate = CompletableDeferred()
            val blocker = async { session.write(bytes(65, 13)) }
            runCurrent()
            val first = async { session.resize(10, 24) }
            val second = async { session.resize(20, 24) }
            val third = async { session.resize(30, 24) }
            val barrier = async { session.flush() }
            val fourth = async { session.resize(40, 24) }
            runCurrent()
            backend.writeGate!!.complete(Unit)
            listOf(blocker, first, second, third, barrier, fourth).forEach { it.await().value() }
            assertEquals(
                listOf(
                    "write:65",
                    "write:13,0",
                    "write:255,250,31,0,10,0,24,255,240",
                    "write:255,250,31,0,30,0,24,255,240",
                    "flush",
                    "write:255,250,31,0,40,0,24,255,240",
                ),
                backend.operations,
            )
            session.close().value()
        }

    @Test
    fun `writes are acknowledged by backend and flush is an ordered barrier`() =
        runTest {
            val backend = FakeTransport()
            backend.writeGate = CompletableDeferred()
            val session = openTelnetSession(backgroundScope, backend)
            val source = bytes(0, 65, 66, 0)
            val write = async { session.write(source, 1, 2) }
            runCurrent()
            source.fill(90)
            assertFalse(write.isCompleted)
            val flush = async { session.flush() }
            runCurrent()
            assertFalse(flush.isCompleted)
            backend.writeGate!!.complete(Unit)
            assertIs<TelnetOutcome.Success<Unit>>(write.await())
            assertIs<TelnetOutcome.Success<Unit>>(flush.await())
            assertEquals(listOf("write:65,66", "flush"), backend.operations)
            session.close().value()
            assertEquals(TelnetStatus.LOCAL_CLOSED, session.state.value.status)
        }

    @Test
    fun `receive orders data echo and record boundaries and drains EOF`() =
        runTest {
            val backend = FakeTransport()
            val session = openTelnetSession(backgroundScope, backend, TelnetConfig(consumeRecords = true))
            backend.input.send("old".toByteArray() + bytes(255, 251, 1, 255, 251, 25) + "new".toByteArray() + bytes(255, 239))
            backend.input.send(null)
            assertContentEquals("old".toByteArray(), assertIs<TelnetInput.Data>(session.receive().value()).bytes)
            assertEquals(TelnetInput.EchoChanged(false), session.receive().value())
            assertContentEquals("new".toByteArray(), assertIs<TelnetInput.Data>(session.receive().value()).bytes)
            assertEquals(TelnetInput.EndOfRecord, session.receive().value())
            assertNull(session.receive().value())
            assertEquals(TelnetStatus.PEER_EOF, session.state.value.status)
            assertTrue(backend.closed)
        }

    @Test
    fun `complete application data precedes protocol failure`() =
        runTest {
            val backend = FakeTransport()
            val session = openTelnetSession(backgroundScope, backend)
            backend.input.send("before".toByteArray() + bytes(255, 250, 24, 1, 255, 250))
            assertContentEquals("before".toByteArray(), assertIs<TelnetInput.Data>(session.receive().value()).bytes)
            assertEquals(TelnetFailureCode.PROTOCOL_ERROR, assertIs<TelnetOutcome.Failure>(session.receive()).error.code)
            assertEquals(TelnetStatus.FAILED, session.state.value.status)
            assertTrue(backend.closed)
        }

    @Test
    fun `programming errors throw and operational failures leave session usable`() =
        runTest {
            val backend = FakeTransport()
            val session = openTelnetSession(backgroundScope, backend)
            assertFailsWith<IllegalArgumentException> { session.write(bytes(65), -1, 1) }
            assertFailsWith<IllegalArgumentException> { session.resize(65536, 24) }
            assertEquals(
                TelnetFailureCode.UNSUPPORTED_OPERATION,
                assertIs<TelnetOutcome.Failure>(session.sendCommand(TelnetCommand.DATA_MARK)).error.code,
            )
            assertEquals(TelnetFailureCode.NON_ASCII_DATA, assertIs<TelnetOutcome.Failure>(session.write(bytes(200))).error.code)
            session.write(bytes(65)).value()
            assertEquals(TelnetStatus.OPEN, session.state.value.status)
            session.close().value()
            assertEquals(TelnetFailureCode.CLOSED, assertIs<TelnetOutcome.Failure>(session.write(bytes(65))).error.code)
            assertFailsWith<IllegalArgumentException> { TelnetConfig(columns = -1) }
            assertFailsWith<IllegalArgumentException> { TelnetConfig(inputBudgetBytes = 1) }
        }

    @Test
    fun `concurrent receive throws and cancellation does not hang the session`() =
        runTest {
            val backend = FakeTransport()
            val session = openTelnetSession(backgroundScope, backend)
            val first = async { session.receive() }
            runCurrent()
            assertFailsWith<IllegalStateException> { session.receive() }
            first.cancelAndJoin()
            backend.input.send(bytes(65))
            assertContentEquals(bytes(65), assertIs<TelnetInput.Data>(session.receive().value()).bytes)
            session.close().value()
        }

    @Test
    fun `byte budgets backpressure both directions without blocking close`() =
        runTest {
            val backend = FakeTransport()
            backend.writeGate = CompletableDeferred()
            val config = TelnetConfig(inputBudgetBytes = 16384, outputBudgetBytes = 32768)
            val session = openTelnetSession(backgroundScope, backend, config)
            val paste = async { session.write(ByteArray(100_000) { 65 }) }
            repeat(8) { backend.input.send(ByteArray(TelnetEngine.CHUNK_SIZE) { 66 }) }
            runCurrent()
            assertFalse(paste.isCompleted)
            // Read worker retains at most one channel element plus its current chunk.
            assertTrue(backend.readCount <= 4, "Read count ${backend.readCount} exceeds bounded backpressure")
            val closing = async { session.close() }
            runCurrent()
            backend.writeGate!!.complete(Unit)
            closing.await().value()
            assertIs<TelnetOutcome.Failure>(paste.await())
            assertTrue(backend.closed)
        }

    @Test
    fun `parent cancellation interrupts blocked read and write and releases callers`() =
        runTest {
            val parent = Job()
            val scope = CoroutineScope(coroutineContext + parent)
            val backend = FakeTransport().also { it.writeGate = CompletableDeferred() }
            val session = openTelnetSession(scope, backend)
            val writing = async { session.write(bytes(65)) }
            runCurrent()
            parent.cancel()
            runCurrent()
            assertTrue(backend.closed)
            assertEquals(TelnetStatus.CANCELLED, session.state.value.status)
            assertIs<TelnetOutcome.Failure>(writing.await())
            session.close().value()
        }

    @Test
    fun `cancellation before acceptance sends nothing`() =
        runTest {
            val backend = FakeTransport().also { it.writeGate = CompletableDeferred() }
            val config = TelnetConfig(outputBudgetBytes = 16386, controlBudgetBytes = 1024)
            val session = openTelnetSession(backgroundScope, backend, config)
            val accepted = async { session.write(ByteArray(8192) { 65 }) }
            runCurrent()
            val waiting = launch { session.write(bytes(66)) }
            runCurrent()
            waiting.cancelAndJoin()
            backend.writeGate!!.complete(Unit)
            accepted.await().value()
            session.flush().value()
            assertEquals(1, backend.operations.count { it.startsWith("write:") })
            session.close().value()
        }

    @Test
    fun `write failures complete accepted and budget-waiting producers`() =
        runTest {
            val backend = FakeTransport().also { it.failWrite = true }
            val session = openTelnetSession(backgroundScope, backend)
            val calls = List(32) { async { session.write(bytes(65)) } }
            calls.forEach { assertEquals(TelnetFailureCode.TRANSPORT_ERROR, assertIs<TelnetOutcome.Failure>(it.await()).error.code) }
            assertEquals(TelnetFailureCode.TRANSPORT_ERROR, assertIs<TelnetOutcome.Failure>(session.receive()).error.code)
            assertTrue(backend.closed)
        }

    @Test
    fun `CHARSET suspends text and times out using coroutine clock`() =
        runTest {
            val backend = FakeTransport()
            val session = openTelnetSession(backgroundScope, backend, TelnetConfig(charset = "UTF-8", charsetTimeoutMillis = 100))
            backend.input.send(bytes(255, 253, 0, 255, 251, 0, 255, 253, 42))
            runCurrent()
            assertTrue(session.state.value.charsetPending)
            val writing = async { session.write(bytes(65)) }
            runCurrent()
            assertFalse(writing.isCompleted)
            advanceTimeBy(101)
            runCurrent()
            assertEquals(TelnetFailureCode.CHARSET_TIMEOUT, assertIs<TelnetOutcome.Failure>(writing.await()).error.code)
            assertEquals(TelnetFailureCode.CHARSET_TIMEOUT, assertIs<TelnetOutcome.Failure>(session.receive()).error.code)
        }

    @Test
    fun `CHARSET response resumes text and reconnect starts clean`() =
        runTest {
            val backend = FakeTransport()
            val config = TelnetConfig(charset = "UTF-8")
            val session = openTelnetSession(backgroundScope, backend, config)
            backend.input.send(bytes(255, 253, 0, 255, 251, 0, 255, 253, 42))
            runCurrent()
            val writing = async { session.write(bytes(65)) }
            runCurrent()
            backend.input.send(bytes(255, 250, 42, 2) + "UTF-8".toByteArray() + bytes(255, 240))
            writing.await().value()
            assertEquals(TelnetInput.CharsetAgreed("UTF-8"), session.receive().value())
            session.close().value()
            val fresh = openTelnetSession(backgroundScope, FakeTransport(), config)
            assertEquals(TelnetSnapshot(), fresh.state.value)
            fresh.close().value()
        }
}

internal fun <T> TelnetOutcome<T>.value(): T = assertIs<TelnetOutcome.Success<T>>(this).value

private class FakeTransport(
    private val localEndpoint: InetSocketAddress? = null,
) : TelnetTransport {
    override fun getLocalSocketAddress(): InetSocketAddress? = localEndpoint

    val input = Channel<ByteArray?>(Channel.UNLIMITED)
    val operations = mutableListOf<String>()
    var writeGate: CompletableDeferred<Unit>? = null
    var failWrite = false
    var closed = false
    var readCount = 0

    override suspend fun read(
        destination: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        val chunk = input.receive() ?: return -1
        chunk.copyInto(destination, offset)
        readCount++
        return chunk.size
    }

    override suspend fun write(
        source: ByteArray,
        offset: Int,
        length: Int,
    ) {
        writeGate?.await()
        if (failWrite) error("Scripted backend failure")
        operations += "write:" + source.copyOfRange(offset, offset + length).joinToString(",") { (it.toInt() and 255).toString() }
    }

    override suspend fun flush() {
        operations += "flush"
    }

    override suspend fun close() {
        closed = true
        input.close()
        writeGate?.cancel()
    }
}
