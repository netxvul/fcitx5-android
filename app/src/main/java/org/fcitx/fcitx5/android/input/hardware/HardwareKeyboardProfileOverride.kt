/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.hardware

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum

enum class HardwareKeyboardProfileOverride(override val stringRes: Int) : ManagedPreferenceEnum {
    Auto(R.string.hardware_keyboard_profile_auto),
    Q25(R.string.hardware_keyboard_profile_q25),
    Key2(R.string.hardware_keyboard_profile_key2),
    Titan2(R.string.hardware_keyboard_profile_titan2),
    Titan2Elite(R.string.hardware_keyboard_profile_titan2_elite)
}
