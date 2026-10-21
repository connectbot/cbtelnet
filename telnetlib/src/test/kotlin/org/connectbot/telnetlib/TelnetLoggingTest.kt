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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.io.IOException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TelnetLoggingTest {
    @Test
    fun `session logs lifecycle without application payloads`() =
        runTest {
            captureLogs { events ->
                val transport = TestTransport()
                val session = openTelnetSession(backgroundScope, transport)
                val incoming = "incoming application payload".toByteArray()
                val outgoing = "outgoing application payload".toByteArray()
                transport.input.send(incoming)
                val received = assertIs<TelnetOutcome.Success<TelnetInput?>>(session.receive()).value
                assertContentEquals(incoming, assertIs<TelnetInput.Data>(received).bytes)
                assertIs<TelnetOutcome.Success<Unit>>(session.write(outgoing))
                assertIs<TelnetOutcome.Success<Unit>>(session.close())

                assertTrue(events.any { it.level == Level.INFO && it.formattedMessage == "Telnet session opened" })
                assertTrue(events.any { it.level == Level.INFO && it.formattedMessage == "Telnet session ended: LOCAL_CLOSED" })
                assertFalse(events.any { it.formattedMessage.contains("application payload") })
            }
        }

    @Test
    fun `transport failure logs its category and cause`() =
        runTest {
            captureLogs { events ->
                val session = openTelnetSession(backgroundScope, TestTransport(IOException("read failed")))
                val result = assertIs<TelnetOutcome.Failure>(session.receive())
                assertEquals(TelnetFailureCode.TRANSPORT_ERROR, result.error.code)
                val event = events.single { it.level == Level.WARN }
                assertTrue(event.formattedMessage.contains("TRANSPORT_ERROR"))
                assertEquals("read failed", event.throwableProxy.cause.message)
                session.close()
            }
        }

    private suspend fun captureLogs(block: suspend (List<ILoggingEvent>) -> Unit) {
        val logger = LoggerFactory.getLogger("org.connectbot.telnetlib.TelnetSession") as Logger
        val previousLevel = logger.level
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.level = Level.DEBUG
        logger.addAppender(appender)
        try {
            block(appender.list)
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
            appender.stop()
        }
    }

    private class TestTransport(
        private val failure: IOException? = null,
    ) : TelnetTransport {
        val input = Channel<ByteArray>(Channel.UNLIMITED)

        override suspend fun read(
            destination: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            failure?.let { throw it }
            val bytes = input.receiveCatching().getOrNull() ?: return -1
            bytes.copyInto(destination, offset)
            return bytes.size
        }

        override suspend fun write(
            source: ByteArray,
            offset: Int,
            length: Int,
        ) = Unit

        override suspend fun flush() = Unit

        override suspend fun close() {
            input.close()
        }
    }
}
