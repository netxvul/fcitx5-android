/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.hardware

import android.view.KeyEvent
import android.view.inputmethod.InputConnection

data class SymRoutingResult(
    val consume: Boolean,
    val event: KeyEvent,
    val pageOpen: Boolean
)

data class SymKeyDecision(
    val consume: Boolean,
    val committedText: String? = null,
    val pageOpen: Boolean
)

/** Handles SYM tap-to-open and SYM+key direct symbol input for hardware keyboards. */
class SymModeController(
    private val toggleSymbolPage: (Boolean) -> Unit,
    private val commitText: (String, InputConnection?) -> Unit
) {
    private var pending = false
    private var chordKeyCode: Int? = null
    private var pageOpen = false
    /** Symbols in the currently visible picker page, in keyboard order. */
    private var pageSymbols: List<String>? = null

    fun handle(
        event: KeyEvent,
        inputConnection: InputConnection?,
        enabled: Boolean = true
    ): SymRoutingResult {
        if (!enabled) return SymRoutingResult(false, event, pageOpen)
        val decision = handleKey(
            event.keyCode,
            event.action,
            event.repeatCount,
            event.isShiftPressed
        )
        if (decision.committedText != null && inputConnection == null) {
            // Do not swallow a physical key if the editor temporarily has no
            // InputConnection (for example during an editor transition).
            chordKeyCode = null
            return SymRoutingResult(false, event, decision.pageOpen)
        }
        decision.committedText?.let { commitText(it, inputConnection) }
        return SymRoutingResult(decision.consume, event, decision.pageOpen)
    }

    /** Pure-key variant for deterministic tests and non-Android event sources. */
    fun handleKey(
        keyCode: Int,
        action: Int,
        repeatCount: Int = 0,
        shifted: Boolean = false,
        enabled: Boolean = true
    ): SymKeyDecision {
        if (!enabled) return SymKeyDecision(false, pageOpen = pageOpen)
        if (keyCode == KeyEvent.KEYCODE_SYM) {
            if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
                pending = true
            } else if (action == KeyEvent.ACTION_UP && pending) {
                pending = false
                pageOpen = !pageOpen
                toggleSymbolPage(pageOpen)
            }
            return SymKeyDecision(true, pageOpen = pageOpen)
        }
        if (pending && action == KeyEvent.ACTION_DOWN && repeatCount == 0 && !isModifier(keyCode)) {
            val symbol = symbolFor(keyCode, shifted)
            pending = false
            if (symbol != null) {
                chordKeyCode = keyCode
                return SymKeyDecision(true, symbol, pageOpen)
            }
        }
        if (pageOpen && action == KeyEvent.ACTION_DOWN && repeatCount == 0 && !isModifier(keyCode)) {
            val symbol = symbolFor(keyCode, shifted)
            if (symbol != null) {
                chordKeyCode = keyCode
                return SymKeyDecision(true, symbol, pageOpen)
            }
        }
        if (chordKeyCode == keyCode) {
            if (action == KeyEvent.ACTION_UP) chordKeyCode = null
            return SymKeyDecision(true, pageOpen = pageOpen)
        }
        return SymKeyDecision(false, pageOpen = pageOpen)
    }

    fun close() {
        pending = false
        chordKeyCode = null
        pageOpen = false
        pageSymbols = null
    }

    fun isPageOpen() = pageOpen

    /** Leaves SYM mode before another hardware modifier takes ownership. */
    fun switchToModifierMode() {
        if (!pageOpen && !pending) return
        pending = false
        chordKeyCode = null
        if (pageOpen) {
            pageOpen = false
            toggleSymbolPage(false)
        }
    }

    /**
     * Updates the physical-key mapping when the picker is paged or its category
     * changes. The first 26 cells correspond to Q..P, A..L and Z..M.
     */
    fun updatePageSymbols(symbols: List<String>?) {
        pageSymbols = symbols?.take(26)
    }

    private fun isModifier(keyCode: Int) = keyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
        keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT || keyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
        keyCode == KeyEvent.KEYCODE_CTRL_RIGHT || keyCode == KeyEvent.KEYCODE_ALT_LEFT ||
        keyCode == KeyEvent.KEYCODE_ALT_RIGHT || keyCode == KeyEvent.KEYCODE_SYM

    private fun symbolFor(keyCode: Int, shifted: Boolean): String? {
        physicalKeyIndex(keyCode)?.let { index ->
            pageSymbols?.let { return it.getOrNull(index) }
        }
        return when (keyCode) {
        KeyEvent.KEYCODE_Q -> "~"
        KeyEvent.KEYCODE_W -> "`"
        KeyEvent.KEYCODE_E -> if (shifted) "{" else "["
        KeyEvent.KEYCODE_R -> if (shifted) "}" else "]"
        KeyEvent.KEYCODE_T -> "^"
        KeyEvent.KEYCODE_Y -> "%"
        KeyEvent.KEYCODE_U -> "<"
        KeyEvent.KEYCODE_I -> ">"
        KeyEvent.KEYCODE_O -> "="
        KeyEvent.KEYCODE_P -> "|"
        KeyEvent.KEYCODE_A -> "&"
        KeyEvent.KEYCODE_S -> "÷"
        KeyEvent.KEYCODE_D -> "±"
        KeyEvent.KEYCODE_F -> "•"
        KeyEvent.KEYCODE_G -> "\\"
        KeyEvent.KEYCODE_H -> "\""
        KeyEvent.KEYCODE_J -> "'"
        KeyEvent.KEYCODE_K -> "«"
        KeyEvent.KEYCODE_L -> "»"
        KeyEvent.KEYCODE_Z -> "¿"
        KeyEvent.KEYCODE_X -> "¡"
        KeyEvent.KEYCODE_C -> "©"
        KeyEvent.KEYCODE_V -> "™"
        KeyEvent.KEYCODE_B -> "§"
        KeyEvent.KEYCODE_N -> "€"
        KeyEvent.KEYCODE_M -> "$"
        else -> null
        }
    }

    private fun physicalKeyIndex(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_Q -> 0
        KeyEvent.KEYCODE_W -> 1
        KeyEvent.KEYCODE_E -> 2
        KeyEvent.KEYCODE_R -> 3
        KeyEvent.KEYCODE_T -> 4
        KeyEvent.KEYCODE_Y -> 5
        KeyEvent.KEYCODE_U -> 6
        KeyEvent.KEYCODE_I -> 7
        KeyEvent.KEYCODE_O -> 8
        KeyEvent.KEYCODE_P -> 9
        KeyEvent.KEYCODE_A -> 10
        KeyEvent.KEYCODE_S -> 11
        KeyEvent.KEYCODE_D -> 12
        KeyEvent.KEYCODE_F -> 13
        KeyEvent.KEYCODE_G -> 14
        KeyEvent.KEYCODE_H -> 15
        KeyEvent.KEYCODE_J -> 16
        KeyEvent.KEYCODE_K -> 17
        KeyEvent.KEYCODE_L -> 18
        KeyEvent.KEYCODE_Z -> 19
        KeyEvent.KEYCODE_X -> 20
        KeyEvent.KEYCODE_C -> 21
        KeyEvent.KEYCODE_V -> 22
        KeyEvent.KEYCODE_B -> 23
        KeyEvent.KEYCODE_N -> 24
        KeyEvent.KEYCODE_M -> 25
        else -> null
    }
}
