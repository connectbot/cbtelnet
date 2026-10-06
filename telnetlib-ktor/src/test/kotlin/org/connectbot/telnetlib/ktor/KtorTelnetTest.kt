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
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.connectbot.telnetlib.TelnetFailureCode
import org.connectbot.telnetlib.TelnetInput
import org.connectbot.telnetlib.TelnetOutcome
import org.connectbot.telnetlib.TelnetStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KtorTelnetTest {
    @Test
    fun `loopback peer handles fragmented commands writes and EOF`() =
        runBlocking {
            withTimeout(10_000) {
                val selector = SelectorManager(Dispatchers.IO)
                val server = aSocket(selector).tcp().bind("127.0.0.1", 0)
                try {
                    val port = (server.localAddress as InetSocketAddress).port
                    val peerAddress = CompletableDeferred<InetSocketAddress>()
                    val peer =
                        async(Dispatchers.IO) {
                            val socket = server.accept()
                            peerAddress.complete(socket.remoteAddress as InetSocketAddress)
                            try {
                                val output = socket.openWriteChannel(autoFlush = true)
                                val input = socket.openReadChannel()
                                for (part in listOf(byteArrayOf(65, -1), byteArrayOf(-5), byteArrayOf(1, 66))) {
                                    output.writeFully(part)
                                    output.flush()
                                    delay(5)
                                }
                                val received = ByteArray(5)
                                input.readFully(received)
                                assertContentEquals(byteArrayOf(-1, -3, 1, 13, 0), received)
                            } finally {
                                socket.close()
                            }
                        }
                    val session =
                        assertIs<TelnetOutcome.Success<org.connectbot.telnetlib.TelnetSession>>(
                            connectTelnet(this, "127.0.0.1", port),
                        ).value
                    val localAddress = assertNotNull(session.getLocalSocketAddress())
                    val acceptedAddress = peerAddress.await()
                    assertEquals(
                        java.net.InetAddress
                            .getByAddress(acceptedAddress.resolveAddress())
                            .hostAddress,
                        localAddress.address.hostAddress,
                    )
                    assertEquals(acceptedAddress.port, localAddress.port)
                    val received = mutableListOf<Byte>()
                    var sawEcho = false
                    while (received.size < 2 || !sawEcho) {
                        when (val input = assertIs<TelnetOutcome.Success<TelnetInput?>>(session.receive()).value) {
                            is TelnetInput.Data -> {
                                received += input.bytes.toList()
                            }

                            is TelnetInput.EchoChanged -> {
                                assertEquals(false, input.localEcho)
                                sawEcho = true
                            }

                            else -> {
                                error("Unexpected peer input: $input")
                            }
                        }
                    }
                    assertContentEquals(byteArrayOf(65, 66), received.toByteArray())
                    assertIs<TelnetOutcome.Success<Unit>>(session.carriageReturn())
                    assertIs<TelnetOutcome.Success<Unit>>(session.flush())
                    peer.await()
                    assertNull(assertIs<TelnetOutcome.Success<TelnetInput?>>(session.receive()).value)
                    assertEquals(TelnetStatus.PEER_EOF, session.state.value.status)
                    assertEquals(localAddress, session.getLocalSocketAddress())
                } finally {
                    server.close()
                    selector.close()
                }
            }
        }

    @Test
    fun `scope cancellation closes a blocked Ktor read`(): Unit =
        runBlocking {
            withTimeout(10_000) {
                val selector = SelectorManager(Dispatchers.IO)
                val server = aSocket(selector).tcp().bind("127.0.0.1", 0)
                val parent = Job()
                try {
                    val scope = CoroutineScope(Dispatchers.IO + parent)
                    val session =
                        assertIs<TelnetOutcome.Success<org.connectbot.telnetlib.TelnetSession>>(
                            connectTelnet(scope, "127.0.0.1", (server.localAddress as InetSocketAddress).port),
                        ).value
                    val peer = server.accept()
                    try {
                        parent.cancelAndJoin()
                        assertEquals(TelnetStatus.CANCELLED, session.state.value.status)
                        assertEquals(-1, peer.openReadChannel().readAvailable(ByteArray(1)))
                        assertIs<TelnetOutcome.Success<Unit>>(session.close())
                    } finally {
                        peer.close()
                    }
                } finally {
                    parent.cancel()
                    server.close()
                    selector.close()
                }
            }
        }

    @Test
    fun `invalid endpoint throws without connecting`(): Unit =
        runBlocking {
            assertFailsWith<IllegalArgumentException> { connectTelnet(this, "", 0) }
        }
}
