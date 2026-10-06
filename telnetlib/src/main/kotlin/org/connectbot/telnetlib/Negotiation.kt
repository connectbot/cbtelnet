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

/** RFC 1143 section 7, mirrored for each option direction. */
internal enum class QState { NO, YES, WANTNO, WANTNO_OPPOSITE, WANTYES, WANTYES_OPPOSITE }

internal data class QTransition(
    val state: QState,
    val send: Boolean? = null,
)

internal object QMethod {
    fun receive(
        state: QState,
        positive: Boolean,
        supported: Boolean,
    ): QTransition =
        if (positive) {
            when (state) {
                QState.NO -> if (supported) QTransition(QState.YES, true) else QTransition(QState.NO, false)
                QState.YES -> QTransition(state)
                QState.WANTNO -> QTransition(QState.NO)
                QState.WANTNO_OPPOSITE -> QTransition(QState.YES)
                QState.WANTYES -> QTransition(QState.YES)
                QState.WANTYES_OPPOSITE -> QTransition(QState.WANTNO, false)
            }
        } else {
            when (state) {
                QState.NO -> QTransition(state)
                QState.YES -> QTransition(QState.NO, false)
                QState.WANTNO -> QTransition(QState.NO)
                QState.WANTNO_OPPOSITE -> QTransition(QState.WANTYES, true)
                QState.WANTYES, QState.WANTYES_OPPOSITE -> QTransition(QState.NO)
            }
        }

    fun request(
        state: QState,
        enable: Boolean,
    ): QTransition =
        if (enable) {
            when (state) {
                QState.NO -> QTransition(QState.WANTYES, true)
                QState.YES -> QTransition(state)
                QState.WANTNO -> QTransition(QState.WANTNO_OPPOSITE)
                QState.WANTNO_OPPOSITE, QState.WANTYES -> QTransition(state)
                QState.WANTYES_OPPOSITE -> QTransition(QState.WANTYES)
            }
        } else {
            when (state) {
                QState.NO -> QTransition(state)
                QState.YES -> QTransition(QState.WANTNO, false)
                QState.WANTNO, QState.WANTYES_OPPOSITE -> QTransition(state)
                QState.WANTNO_OPPOSITE -> QTransition(QState.WANTNO)
                QState.WANTYES -> QTransition(QState.WANTYES_OPPOSITE)
            }
        }
}
