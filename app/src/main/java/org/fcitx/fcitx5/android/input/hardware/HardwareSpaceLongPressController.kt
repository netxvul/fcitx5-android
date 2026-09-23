/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.hardware

import android.view.KeyEvent

/**
 * Delays an unmodified hardware Space until it is known whether it is a tap
 * or a long press. A tap is replayed as the original down/up pair; a long
 * press consumes the pair and invokes [onLongPress].
 */
class HardwareSpaceLongPressController(
    private val scheduler: Scheduler,
    private val delayMs: () -> Long,
    private val onLongPress: () -> Unit,
    private val dispatchTap: (KeyEvent) -> Unit
) {
    interface Scheduler {
        fun postDelayed(task: Runnable, delayMs: Long)

        fun remove(task: Runnable)
    }

    private var downEvent: KeyEvent? = null
    private var longPressed = false

    private val longPressTask = Runnable {
        if (downEvent != null) {
            longPressed = true
            onLongPress()
        }
    }

    /** Returns true when the event belongs to a pending hardware Space tap. */
    fun handle(event: KeyEvent): Boolean = handle(event.action, event.repeatCount, event)

    /** Pure-event variant for deterministic tests and non-Android event sources. */
    fun handle(action: Int, repeatCount: Int = 0, event: KeyEvent): Boolean {
        when (action) {
            KeyEvent.ACTION_DOWN -> {
                if (repeatCount == 0 && downEvent == null) {
                    downEvent = event
                    longPressed = false
                    scheduler.postDelayed(longPressTask, delayMs().coerceAtLeast(0L))
                }
                return downEvent != null
            }

            KeyEvent.ACTION_UP -> {
                val pendingDown = downEvent ?: return false
                val wasLongPressed = longPressed
                scheduler.remove(longPressTask)
                clearPending()
                if (!wasLongPressed) {
                    dispatchTap(pendingDown)
                    dispatchTap(event)
                }
                return true
            }

            else -> return downEvent != null
        }
    }

    fun reset() {
        if (downEvent == null) return
        scheduler.remove(longPressTask)
        clearPending()
    }

    private fun clearPending() {
        downEvent = null
        longPressed = false
    }
}
