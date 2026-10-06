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
package org.connectbot.telnetlib.ktor

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import org.connectbot.telnetlib.TelnetConfig
import org.connectbot.telnetlib.TelnetFailure
import org.connectbot.telnetlib.TelnetFailureCode
import org.connectbot.telnetlib.TelnetOutcome
import org.connectbot.telnetlib.TelnetSession
import org.connectbot.telnetlib.TelnetTransport
import org.connectbot.telnetlib.openTelnetSession
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

/**
 * Connects a child Telnet session using Ktor TCP. The session owns its socket and
 * selector; cancellation during connect also closes both. No Java Socket API is used.
 * @throws IllegalArgumentException for a blank host, invalid port or nonpositive timeout.
 */
suspend fun connectTelnet(
    scope: CoroutineScope,
    host: String,
    port: Int = 23,
    config: TelnetConfig = TelnetConfig(),
    ioContext: CoroutineContext = Dispatchers.IO,
    connectTimeoutMillis: Long = 10_000,
): TelnetOutcome<TelnetSession> {
    require(host.isNotBlank()) { "Host must not be blank" }
    require(port in 1..65535) { "Port must be in 1..65535" }
    require(connectTimeoutMillis > 0) { "Connection timeout must be positive" }
    scope.coroutineContext.ensureActive()
    return coroutineScope {
        var selector: SelectorManager? = null
        var socket: Socket? = null
        var transferred = false

        // Cancel this connect operation promptly if its eventual owner is cancelled.
        @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
        val scopeCancellation =
            scope.coroutineContext[Job]?.invokeOnCompletion(
                onCancelling = true,
                invokeImmediately = true,
            ) { cause ->
                if (cause != null) cancel(CancellationException("Telnet owner cancelled", cause))
            }
        try {
            val manager = SelectorManager(ioContext.minusKey(Job))
            selector = manager
            val connected =
                withTimeoutOrNull(connectTimeoutMillis) {
                    socket = aSocket(manager).tcp().connect(host, port)
                    true
                }
            if (connected == null) {
                return@coroutineScope TelnetOutcome.Failure(
                    TelnetFailure(TelnetFailureCode.CONNECT_TIMEOUT, "Telnet connection timed out"),
                )
            }
            currentCoroutineContext().ensureActive()
            scope.coroutineContext.ensureActive()
            val transport = KtorTransport(socket!!, manager)
            val session = openTelnetSession(scope, transport, config, ioContext)
            transferred = true
            TelnetOutcome.Success(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            scope.coroutineContext.ensureActive()
            TelnetOutcome.Failure(
                TelnetFailure(TelnetFailureCode.TRANSPORT_ERROR, "Could not connect to $host:$port: ${e.message}"),
            )
        } finally {
            scopeCancellation?.dispose()
            if (!transferred) {
                runCatching { socket?.close() }
                runCatching { selector?.close() }
            }
        }
    }
}

private class KtorTransport(
    private val socket: Socket,
    private val selector: SelectorManager,
) : TelnetTransport {
    private val localEndpoint: java.net.InetSocketAddress? =
        (socket.localAddress as? InetSocketAddress)?.let { local ->
            local.resolveAddress()?.let { bytes -> java.net.InetSocketAddress(java.net.InetAddress.getByAddress(bytes), local.port) }
        }

    override fun getLocalSocketAddress(): java.net.InetSocketAddress? = localEndpoint

    private val input = socket.openReadChannel()
    private val output = socket.openWriteChannel(autoFlush = false)
    private val closed = AtomicBoolean()

    override suspend fun read(
        destination: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        validateSlice(destination.size, offset, length)
        if (length == 0) return 0
        return input.readAvailable(destination, offset, length)
    }

    override suspend fun write(
        source: ByteArray,
        offset: Int,
        length: Int,
    ) {
        validateSlice(source.size, offset, length)
        output.writeFully(source, offset, length)
        // Ktor's channel is buffered: make write completion mean the bytes were handed
        // to its backend, including small negotiation replies and keyboard input.
        output.flush()
    }

    override suspend fun flush() {
        output.flush()
    }

    override suspend fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                socket.close()
            } finally {
                try {
                    input.cancel()
                } finally {
                    selector.close()
                }
            }
        }
    }

    private fun validateSlice(
        size: Int,
        offset: Int,
        length: Int,
    ) {
        require(offset >= 0 && length >= 0 && offset <= size && length <= size - offset)
    }
}
