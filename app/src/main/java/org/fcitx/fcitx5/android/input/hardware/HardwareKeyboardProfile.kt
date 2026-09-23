/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.hardware

import android.os.Build

enum class HardwareKeyboardModel {
    Auto,
    Q25,
    Key2,
    Titan2,
    Titan2Elite,
    Unknown
}

data class HardwareBuildFingerprint(
    val brand: String,
    val manufacturer: String,
    val model: String,
    val device: String,
    val product: String,
    val board: String,
    val display: String
) {
    fun containsAny(vararg tokens: String): Boolean = tokens.any { token ->
        listOf(brand, manufacturer, model, device, product, board, display)
            .any { value -> value.contains(token, ignoreCase = true) }
    }
}

data class HardwareKeyboardProfile(
    val model: HardwareKeyboardModel,
    val layoutName: String
)

object HardwareKeyboardProfiles {
    fun current(): HardwareBuildFingerprint = HardwareBuildFingerprint(
        brand = Build.BRAND.orEmpty(),
        manufacturer = Build.MANUFACTURER.orEmpty(),
        model = Build.MODEL.orEmpty(),
        device = Build.DEVICE.orEmpty(),
        product = Build.PRODUCT.orEmpty(),
        board = Build.BOARD.orEmpty(),
        display = Build.DISPLAY.orEmpty()
    )

    fun resolve(
        fingerprint: HardwareBuildFingerprint,
        override: HardwareKeyboardModel = HardwareKeyboardModel.Auto
    ): HardwareKeyboardProfile {
        if (override != HardwareKeyboardModel.Auto) {
            return profileFor(override)
        }

        val values = listOf(
            fingerprint.brand,
            fingerprint.manufacturer,
            fingerprint.model,
            fingerprint.device,
            fingerprint.product,
            fingerprint.board,
            fingerprint.display
        ).joinToString(" ").lowercase()

        return when {
            values.contains("titan2elite_qwerty") ||
                values.contains("titan2elite-qwerty") ||
                values.contains("titan2eliteqwerty") ||
                (values.contains("unihertz") &&
                    (fingerprint.display.contains("elite", ignoreCase = true) ||
                        fingerprint.board.contains("g72", ignoreCase = true))) ->
                profileFor(HardwareKeyboardModel.Titan2Elite)
            values.contains("titan 2") || values.contains("titan2") ->
                profileFor(HardwareKeyboardModel.Titan2)
            values.contains("q25") &&
                (values.contains("zinwa") || values.contains("blackberry") ||
                    fingerprint.device.equals("q25", ignoreCase = true)) ->
                profileFor(HardwareKeyboardModel.Q25)
            fingerprint.device.equals("athena", ignoreCase = true) ||
                fingerprint.device.equals("luna", ignoreCase = true) ||
                values.contains("blackberry key2") || values.contains("bbf100") ->
                profileFor(HardwareKeyboardModel.Key2)
            else -> profileFor(HardwareKeyboardModel.Unknown)
        }
    }

    fun profileFor(model: HardwareKeyboardModel): HardwareKeyboardProfile = when (model) {
        HardwareKeyboardModel.Q25 -> HardwareKeyboardProfile(model, "q25")
        HardwareKeyboardModel.Key2 -> HardwareKeyboardProfile(model, "key2")
        HardwareKeyboardModel.Titan2 -> HardwareKeyboardProfile(model, "titan2")
        HardwareKeyboardModel.Titan2Elite -> HardwareKeyboardProfile(model, "titan2elite_qwerty")
        HardwareKeyboardModel.Auto, HardwareKeyboardModel.Unknown ->
            HardwareKeyboardProfile(model, "unknown")
    }
}
