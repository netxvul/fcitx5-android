/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.hardware

import android.view.KeyEvent

data class ModifierState(
    val shiftPressed: Boolean = false,
    val ctrlPressed: Boolean = false,
    val altPressed: Boolean = false,
    val capsLock: Boolean = false,
    val symPressed: Boolean = false,
    val shiftOneShot: Boolean = false,
    val ctrlOneShot: Boolean = false,
    val altOneShot: Boolean = false,
    val shiftLatched: Boolean = false,
    val ctrlLatched: Boolean = false,
    val altLatched: Boolean = false
) {
    val hasIndicator: Boolean
        get() = shiftPressed || ctrlPressed || altPressed || symPressed || capsLock ||
            shiftLatched || ctrlLatched || altLatched
}

class ModifierStateTracker {
    private var state = ModifierState()
    private val pressed = mutableSetOf<Int>()

    fun snapshot() = state

    fun onKeyDown(event: KeyEvent): ModifierState =
        onKeyDown(event.keyCode, event.metaState, event.isCapsLockOn)

    fun onKeyDown(keyCode: Int, metaState: Int = 0, capsLock: Boolean = false): ModifierState {
        if (keyCode in ModifierKeys) pressed.add(keyCode)
        return update(metaState, capsLock)
    }

    fun onKeyUp(event: KeyEvent): ModifierState =
        onKeyUp(event.keyCode, event.isCapsLockOn, event.metaState)

    fun onKeyUp(keyCode: Int, capsLock: Boolean = false, metaState: Int = 0): ModifierState {
        pressed.remove(keyCode)
        return update(metaState, capsLock)
    }

    private fun update(metaState: Int, capsLock: Boolean): ModifierState {
        // Use the latest native meta state instead of latching flags inferred
        // from an earlier letter event indefinitely. Track both modifier sides.
        state = ModifierState(
            shiftPressed = KeyEvent.KEYCODE_SHIFT_LEFT in pressed ||
                KeyEvent.KEYCODE_SHIFT_RIGHT in pressed || metaState and KeyEvent.META_SHIFT_ON != 0,
            ctrlPressed = KeyEvent.KEYCODE_CTRL_LEFT in pressed ||
                KeyEvent.KEYCODE_CTRL_RIGHT in pressed || metaState and KeyEvent.META_CTRL_ON != 0,
            altPressed = KeyEvent.KEYCODE_ALT_LEFT in pressed ||
                KeyEvent.KEYCODE_ALT_RIGHT in pressed || metaState and KeyEvent.META_ALT_ON != 0,
            symPressed = KeyEvent.KEYCODE_SYM in pressed,
            capsLock = capsLock
        )
        return state
    }

    fun reset(): ModifierState {
        pressed.clear()
        state = ModifierState()
        return state
    }

    private companion object {
        val ModifierKeys = setOf(
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT, KeyEvent.KEYCODE_SYM
        )
    }
}
