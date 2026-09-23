/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.hardware

import android.view.KeyEvent

data class NormalizedHardwareKeyEvent(
    val keyCode: Int,
    val event: KeyEvent
)

class HardwareKeyNormalizer {
    private var lastQ25MetaState = 0

    private companion object {
        // TitanKey.kl declares scan 251 as FUNC3 (the physical FN key), not CTRL.
        const val TITAN2_FUNCTION_SCAN_CODE = 251
        const val TITAN2_SYM_SCAN_CODE = 253
    }

    fun normalizeKeyCode(
        keyCode: Int,
        scanCode: Int,
        profile: HardwareKeyboardProfile
    ): Int = when (profile.model) {
        HardwareKeyboardModel.Q25 -> when (keyCode) {
            KeyEvent.KEYCODE_SHIFT_RIGHT -> KeyEvent.KEYCODE_CTRL_LEFT
            KeyEvent.KEYCODE_ALT_RIGHT -> KeyEvent.KEYCODE_SYM
            else -> keyCode
        }
        HardwareKeyboardModel.Key2 -> when (scanCode) {
            17 -> KeyEvent.KEYCODE_W
            44 -> KeyEvent.KEYCODE_Z
            50 -> KeyEvent.KEYCODE_M
            else -> keyCode
        }
        HardwareKeyboardModel.Titan2,
        HardwareKeyboardModel.Titan2Elite -> when (scanCode) {
            TITAN2_FUNCTION_SCAN_CODE -> KeyEvent.KEYCODE_FUNCTION
            TITAN2_SYM_SCAN_CODE -> KeyEvent.KEYCODE_SYM
            else -> if (keyCode == KeyEvent.KEYCODE_ALT_RIGHT) KeyEvent.KEYCODE_SYM else keyCode
        }
        else -> keyCode
    }

    fun normalize(
        keyCode: Int,
        event: KeyEvent,
        profile: HardwareKeyboardProfile
    ): NormalizedHardwareKeyEvent {
        return when (profile.model) {
            HardwareKeyboardModel.Q25 -> normalizeQ25(keyCode, event)
            HardwareKeyboardModel.Key2 -> normalizeKey2(keyCode, event)
            HardwareKeyboardModel.Titan2,
            HardwareKeyboardModel.Titan2Elite -> normalizeTitan2(keyCode, event, profile.model)
            else -> NormalizedHardwareKeyEvent(keyCode, event)
        }
    }

    fun reset() {
        lastQ25MetaState = 0
    }

    private fun normalizeQ25(keyCode: Int, event: KeyEvent): NormalizedHardwareKeyEvent {
        val q25Ctrl = KeyEvent.KEYCODE_SHIFT_RIGHT
        val q25Sym = KeyEvent.KEYCODE_ALT_RIGHT
        val q25CtrlMeta = KeyEvent.META_SHIFT_RIGHT_ON
        val q25SymMeta = KeyEvent.META_ALT_RIGHT_ON
        val shouldNormalize = keyCode == q25Ctrl || keyCode == q25Sym ||
            ((event.metaState or lastQ25MetaState) and (q25CtrlMeta or q25SymMeta)) != 0

        if (!shouldNormalize) {
            lastQ25MetaState = event.metaState
            return NormalizedHardwareKeyEvent(keyCode, event)
        }

        val normalizedKeyCode = normalizeKeyCode(keyCode, event.scanCode, HardwareKeyboardProfiles.profileFor(HardwareKeyboardModel.Q25))
        val normalizedMetaState = normalizeQ25MetaState(event.metaState)
        val normalizedScanCode = when (keyCode) {
            q25Ctrl -> 251
            q25Sym -> 253
            else -> event.scanCode
        }
        lastQ25MetaState = event.metaState

        val normalizedEvent = if (
            normalizedKeyCode == event.keyCode &&
                normalizedMetaState == event.metaState &&
                normalizedScanCode == event.scanCode
        ) {
            event
        } else {
            KeyEvent(
                event.downTime,
                event.eventTime,
                event.action,
                normalizedKeyCode,
                event.repeatCount,
                normalizedMetaState,
                event.deviceId,
                normalizedScanCode,
                event.flags,
                event.source
            )
        }
        return NormalizedHardwareKeyEvent(normalizedKeyCode, normalizedEvent)
    }

    private fun normalizeQ25MetaState(metaState: Int): Int {
        val reloadable = KeyEvent.META_SHIFT_MASK or
            KeyEvent.META_ALT_MASK or
            KeyEvent.META_CTRL_MASK or
            KeyEvent.META_SYM_ON
        val shift = if ((metaState and KeyEvent.META_SHIFT_LEFT_ON) != 0) {
            KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        } else 0
        val ctrl = if ((metaState and KeyEvent.META_SHIFT_RIGHT_ON) != 0) {
            KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        } else 0
        val alt = if ((metaState and KeyEvent.META_ALT_LEFT_ON) != 0) {
            KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        } else 0
        val sym = if ((metaState and KeyEvent.META_ALT_RIGHT_ON) != 0) {
            KeyEvent.META_SYM_ON
        } else 0
        return (metaState and reloadable.inv()) or shift or ctrl or alt or sym
    }

    private fun normalizeKey2(keyCode: Int, event: KeyEvent): NormalizedHardwareKeyEvent {
        val normalized = normalizeKeyCode(keyCode, event.scanCode, HardwareKeyboardProfiles.profileFor(HardwareKeyboardModel.Key2))
        if (normalized == keyCode) return NormalizedHardwareKeyEvent(keyCode, event)
        val normalizedEvent = KeyEvent(
            event.downTime,
            event.eventTime,
            event.action,
            normalized,
            event.repeatCount,
            event.metaState,
            event.deviceId,
            event.scanCode,
            event.flags,
            event.source
        )
        return NormalizedHardwareKeyEvent(normalized, normalizedEvent)
    }

    private fun normalizeTitan2(
        keyCode: Int,
        event: KeyEvent,
        model: HardwareKeyboardModel
    ): NormalizedHardwareKeyEvent {
        val normalized = normalizeKeyCode(keyCode, event.scanCode, profileFor(model))
        if (normalized == keyCode) return NormalizedHardwareKeyEvent(keyCode, event)
        val normalizedScanCode = when (event.scanCode) {
            TITAN2_FUNCTION_SCAN_CODE -> TITAN2_FUNCTION_SCAN_CODE
            TITAN2_SYM_SCAN_CODE -> TITAN2_SYM_SCAN_CODE
            else -> event.scanCode
        }
        val normalizedEvent = KeyEvent(
            event.downTime,
            event.eventTime,
            event.action,
            normalized,
            event.repeatCount,
            event.metaState,
            event.deviceId,
            normalizedScanCode,
            event.flags,
            event.source
        )
        return NormalizedHardwareKeyEvent(normalized, normalizedEvent)
    }

    private fun profileFor(model: HardwareKeyboardModel) =
        HardwareKeyboardProfiles.profileFor(model)
}
