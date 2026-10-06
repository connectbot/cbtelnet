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

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NegotiationTest {
    @Test
    fun `all RFC 1143 transitions match independent tables`() {
        // Rows: NO, YES, WANTNO empty/opposite, WANTYES empty/opposite.
        // Columns: receive positive, receive negative, request enable, request disable.
        val enabledPolicy =
            listOf(
                listOf(QTransition(QState.YES, true), QTransition(QState.NO), QTransition(QState.WANTYES, true), QTransition(QState.NO)),
                listOf(QTransition(QState.YES), QTransition(QState.NO, false), QTransition(QState.YES), QTransition(QState.WANTNO, false)),
                listOf(QTransition(QState.NO), QTransition(QState.NO), QTransition(QState.WANTNO_OPPOSITE), QTransition(QState.WANTNO)),
                listOf(
                    QTransition(QState.YES),
                    QTransition(QState.WANTYES, true),
                    QTransition(QState.WANTNO_OPPOSITE),
                    QTransition(QState.WANTNO),
                ),
                listOf(QTransition(QState.YES), QTransition(QState.NO), QTransition(QState.WANTYES), QTransition(QState.WANTYES_OPPOSITE)),
                listOf(
                    QTransition(QState.WANTNO, false),
                    QTransition(QState.NO),
                    QTransition(QState.WANTYES),
                    QTransition(QState.WANTYES_OPPOSITE),
                ),
            )
        QState.entries.forEachIndexed { index, state ->
            for (supported in listOf(false, true)) {
                val expectedPositive = if (!supported && state == QState.NO) QTransition(QState.NO, false) else enabledPolicy[index][0]
                assertEquals(expectedPositive, QMethod.receive(state, true, supported))
                assertEquals(enabledPolicy[index][1], QMethod.receive(state, false, supported))
            }
            assertEquals(enabledPolicy[index][2], QMethod.request(state, true))
            assertEquals(enabledPolicy[index][3], QMethod.request(state, false))
        }
    }

    @Test
    fun `crossed reversed and refused requests work in both directions`() {
        for (direction in TelnetDirection.entries) {
            val engine = createTelnetEngine().also { it.start() }
            val positive = if (direction == TelnetDirection.LOCAL) 253 else 251
            val negative = if (direction == TelnetDirection.LOCAL) 254 else 252
            val outgoingPositive = if (direction == TelnetDirection.LOCAL) 251 else 253
            val outgoingNegative = if (direction == TelnetDirection.LOCAL) 252 else 254
            assertEquals(listOf(255, outgoingPositive, 0), engine.requestOption(TelnetOption.BINARY, direction, true).wire())
            assertEquals(emptyList(), engine.requestOption(TelnetOption.BINARY, direction, false).wire())
            assertEquals(listOf(255, outgoingNegative, 0), engine.feed(bytes(255, positive, 0)).wire())
            assertEquals(false, engine.isEnabled(TelnetOption.BINARY, direction))
            assertEquals(emptyList(), engine.feed(bytes(255, negative, 0)).wire())
            engine.requestOption(TelnetOption.BINARY, direction, true)
            assertEquals(emptyList(), engine.feed(bytes(255, negative, 0)).wire())
            assertEquals(false, engine.isEnabled(TelnetOption.BINARY, direction))
            assertEquals(listOf(255, outgoingPositive, 0), engine.feed(bytes(255, positive, 0)).wire())
            assertEquals(emptyList(), engine.feed(bytes(255, positive, 0)).wire())
            assertEquals(listOf(255, outgoingNegative, 0), engine.feed(bytes(255, negative, 0)).wire())
            assertEquals(emptyList(), engine.feed(bytes(255, negative, 0)).wire())
        }
    }

    @Test
    fun `all unsigned option numbers are handled in both directions`() {
        for (option in 0..255) {
            for (direction in TelnetDirection.entries) {
                val engine = createTelnetEngine().also { it.start() }
                val accepted = if (direction == TelnetDirection.LOCAL) option in listOf(0, 3, 24, 31) else option in listOf(0, 1, 3)
                val verb = if (direction == TelnetDirection.LOCAL) 253 else 251
                val response =
                    if (direction == TelnetDirection.LOCAL) {
                        if (accepted) 251 else 252
                    } else {
                        if (accepted) 253 else 254
                    }
                assertEquals(listOf(255, response, option), engine.feed(bytes(255, verb, option)).wire().take(3))
            }
        }
    }
}

internal fun bytes(vararg values: Int): ByteArray = values.map(Int::toByte).toByteArray()

internal fun TelnetResult.wire(): List<Int> =
    effects.filterIsInstance<TelnetEffect.Outbound>().flatMap {
        it.bytes.map { b ->
            b.toInt() and
                255
        }
    }

internal fun TelnetResult.inputs(): List<TelnetInput> = effects.filterIsInstance<TelnetEffect.Input>().map { it.value }
