/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.hardware

class SystemStatusIconController(
    private val showIcon: (Int) -> Unit,
    private val hideIcon: () -> Unit
) {
    private var baseIcon: Int? = null
    private var modifierIcon: Int? = null
    private var isPhysicalKeyboard = false
    private var modifierIndicatorsEnabled = false
    private var renderedIcon: Int? = null

    fun setPhysicalKeyboard(value: Boolean) {
        isPhysicalKeyboard = value
        render()
    }

    fun setModifierIndicatorsEnabled(value: Boolean) {
        modifierIndicatorsEnabled = value
        render()
    }

    fun setModifierState(state: ModifierState) {
        modifierIcon = ModifierIconResolver.resolve(state)
        render()
    }

    fun setBaseIcon(@androidx.annotation.DrawableRes icon: Int?) {
        baseIcon = icon
        render()
    }

    fun clear() {
        baseIcon = null
        modifierIcon = null
        renderedIcon = null
        hideIcon()
    }

    private fun render() {
        // Hardware modifiers remain meaningful while a virtual picker is visible.
        val target = when {
            modifierIndicatorsEnabled && modifierIcon != null -> modifierIcon
            isPhysicalKeyboard -> baseIcon
            else -> null
        }
        if (target == renderedIcon) return
        if (target == null) hideIcon() else showIcon(target)
        renderedIcon = target
    }
}
