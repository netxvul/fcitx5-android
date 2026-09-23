/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.hardware

import androidx.annotation.DrawableRes
import org.fcitx.fcitx5.android.R

object ModifierIconResolver {
    @DrawableRes
    fun resolve(state: ModifierState): Int? {
        if (!state.hasIndicator) return null
        // The active symbol layer takes priority in Android's single IME icon slot.
        if (state.symPressed) return R.drawable.ic_status_modifier_sym
        // A single Android IME status slot is available. Combination resources retain
        // the same three-state representation used by Pastiera Supra.
        val shift = if (state.capsLock || state.shiftLatched) 2 else if (state.shiftPressed) 1 else 0
        val ctrl = if (state.ctrlLatched) 2 else if (state.ctrlPressed) 1 else 0
        val alt = if (state.altLatched) 2 else if (state.altPressed) 1 else 0
        val key = "$shift$ctrl$alt"
        val icons = mapOf(
            "001" to R.drawable.ic_status_modifiers_s0_c0_a1,
            "002" to R.drawable.ic_status_modifiers_s0_c0_a2,
            "010" to R.drawable.ic_status_modifiers_s0_c1_a0,
            "011" to R.drawable.ic_status_modifiers_s0_c1_a1,
            "012" to R.drawable.ic_status_modifiers_s0_c1_a2,
            "020" to R.drawable.ic_status_modifiers_s0_c2_a0,
            "021" to R.drawable.ic_status_modifiers_s0_c2_a1,
            "022" to R.drawable.ic_status_modifiers_s0_c2_a2,
            "100" to R.drawable.ic_status_modifiers_s1_c0_a0,
            "101" to R.drawable.ic_status_modifiers_s1_c0_a1,
            "102" to R.drawable.ic_status_modifiers_s1_c0_a2,
            "110" to R.drawable.ic_status_modifiers_s1_c1_a0,
            "111" to R.drawable.ic_status_modifiers_s1_c1_a1,
            "112" to R.drawable.ic_status_modifiers_s1_c1_a2,
            "120" to R.drawable.ic_status_modifiers_s1_c2_a0,
            "121" to R.drawable.ic_status_modifiers_s1_c2_a1,
            "122" to R.drawable.ic_status_modifiers_s1_c2_a2,
            "200" to R.drawable.ic_status_modifiers_s2_c0_a0,
            "201" to R.drawable.ic_status_modifiers_s2_c0_a1,
            "202" to R.drawable.ic_status_modifiers_s2_c0_a2,
            "210" to R.drawable.ic_status_modifiers_s2_c1_a0,
            "211" to R.drawable.ic_status_modifiers_s2_c1_a1,
            "212" to R.drawable.ic_status_modifiers_s2_c1_a2,
            "220" to R.drawable.ic_status_modifiers_s2_c2_a0,
            "221" to R.drawable.ic_status_modifiers_s2_c2_a1,
            "222" to R.drawable.ic_status_modifiers_s2_c2_a2
        )
        return icons[key] ?: R.drawable.ic_status_modifier_generic
    }
}
