/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import android.view.KeyEvent
import org.fcitx.fcitx5.android.input.InputDeviceManager
import org.fcitx.fcitx5.android.input.hardware.ModifierIconResolver
import org.fcitx.fcitx5.android.input.hardware.HardwareKeyboardModel
import org.fcitx.fcitx5.android.input.hardware.HardwareKeyboardProfiles
import org.fcitx.fcitx5.android.input.hardware.HardwareKeyNormalizer
import org.fcitx.fcitx5.android.input.hardware.HardwareSpaceLongPressController
import org.fcitx.fcitx5.android.input.hardware.ModifierState
import org.fcitx.fcitx5.android.input.hardware.ModifierStateTracker
import org.fcitx.fcitx5.android.input.hardware.ModifierLockController
import org.fcitx.fcitx5.android.input.hardware.SymModeController
import org.fcitx.fcitx5.android.input.hardware.SystemStatusIconController
import org.fcitx.fcitx5.android.input.hardware.RimeModifierRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareKeyboardMigrationTest {
    private class TestScheduler : HardwareSpaceLongPressController.Scheduler {
        private var task: Runnable? = null

        override fun postDelayed(task: Runnable, delayMs: Long) {
            this.task = task
        }

        override fun remove(task: Runnable) {
            if (this.task === task) this.task = null
        }

        fun fire() {
            val current = task
            task = null
            current?.run()
        }
    }

    private class ModifierScheduler : RimeModifierRouter.Scheduler {
        private val tasks = linkedSetOf<Runnable>()

        override fun postDelayed(task: Runnable, delayMs: Long) {
            tasks += task
        }

        override fun remove(task: Runnable) {
            tasks -= task
        }

        fun fireAll() {
            val current = tasks.toList()
            tasks.clear()
            current.forEach(Runnable::run)
        }
    }

    private fun keyEvent(action: Int, keyCode: Int, time: Long) = KeyEvent(
        time, time, action, keyCode, 0, 0, 1, 0, 0, 0
    )

    @Test
    fun hardwareSpaceTapIsReplayedAsDownAndUp() {
        val scheduler = TestScheduler()
        var forwarded = 0
        var longPresses = 0
        val controller = HardwareSpaceLongPressController(
            scheduler,
            delayMs = { 300L },
            onLongPress = { longPresses++ },
            dispatchTap = { forwarded++ }
        )

        assertTrue(controller.handle(KeyEvent.ACTION_DOWN, event = keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE, 100)))
        assertTrue(controller.handle(KeyEvent.ACTION_UP, event = keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE, 150)))
        assertEquals(2, forwarded)
        assertEquals(0, longPresses)
    }

    @Test
    fun hardwareSpaceLongPressSuppressesTapAndRunsAction() {
        val scheduler = TestScheduler()
        var forwarded = 0
        var longPresses = 0
        val controller = HardwareSpaceLongPressController(
            scheduler,
            delayMs = { 300L },
            onLongPress = { longPresses++ },
            dispatchTap = { forwarded++ }
        )

        assertTrue(controller.handle(KeyEvent.ACTION_DOWN, event = keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE, 100)))
        scheduler.fire()
        assertEquals(1, longPresses)
        assertTrue(controller.handle(KeyEvent.ACTION_UP, event = keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE, 500)))
        assertEquals(0, forwarded)
    }

    @Test
    fun rimeSingleModifierTapIsReplayedAfterDoubleTapWindow() {
        val scheduler = ModifierScheduler()
        var raw = 0
        val router = RimeModifierRouter(
            ModifierLockController(doubleTapThresholdMs = 300),
            scheduler,
            delayMs = { 300L },
            dispatchRaw = { raw++ }
        )

        router.handle(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_DOWN, 100, 0,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 100), false)
        router.handle(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_UP, 150, 0,
            keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 150), false)
        assertEquals(0, raw)
        scheduler.fireAll()
        assertEquals(2, raw)
    }

    @Test
    fun rimeDoubleModifierTapEntersSupraLockWithoutRawTap() {
        val scheduler = ModifierScheduler()
        var raw = 0
        val controller = ModifierLockController(doubleTapThresholdMs = 300)
        val router = RimeModifierRouter(controller, scheduler, { 300L }, { raw++ })

        router.handle(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.ACTION_DOWN, 100, 0,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 100), false)
        router.handle(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.ACTION_UP, 150, 0,
            keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 150), false)
        router.handle(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.ACTION_DOWN, 250, 0,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 250), false)
        router.handle(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.ACTION_UP, 300, 0,
            keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 300), false)
        assertEquals(0, raw)
        assertTrue(controller.snapshot().ctrlLatched)
        val result = router.handle(
            KeyEvent.KEYCODE_A, KeyEvent.ACTION_DOWN, 400, 0,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 400), false
        )
        assertTrue(result.syntheticMetaState and KeyEvent.META_CTRL_ON != 0)
    }

    @Test
    fun rimeHeldModifierIsSentBeforeTheFirstCharacter() {
        val scheduler = ModifierScheduler()
        var raw = 0
        val router = RimeModifierRouter(
            ModifierLockController(doubleTapThresholdMs = 300),
            scheduler,
            delayMs = { 300L },
            dispatchRaw = { raw++ }
        )

        router.handle(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_DOWN, 100, 0,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 100), false)
        val letter = router.handle(
            KeyEvent.KEYCODE_A, KeyEvent.ACTION_DOWN, 180, 0,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 180), false
        )
        assertEquals(1, raw)
        assertTrue(letter.syntheticMetaState and KeyEvent.META_SHIFT_ON != 0)
        router.handle(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_UP, 220, 0,
            keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 220), false)
        assertEquals(2, raw)
    }

    @Test
    fun resolvesTitan2EliteFromGenericTitanBuild() {
        val profile = HardwareKeyboardProfiles.resolve(
            HardwareKeyboardProfiles.current().copy(
                brand = "Unihertz",
                manufacturer = "Unihertz",
                model = "Titan 2",
                device = "qwerty",
                product = "titan2",
                board = "G72",
                display = "Titan2 Elite release"
            )
        )
        assertEquals(HardwareKeyboardModel.Titan2Elite, profile.model)
        assertEquals("titan2elite_qwerty", profile.layoutName)
    }

    @Test
    fun q25ControlAndSymKeysAreNormalizedBeforeFcitx() {
        val normalizer = HardwareKeyNormalizer()
        val profile = HardwareKeyboardProfiles.profileFor(HardwareKeyboardModel.Q25)
        assertEquals(
            KeyEvent.KEYCODE_CTRL_LEFT,
            normalizer.normalizeKeyCode(KeyEvent.KEYCODE_SHIFT_RIGHT, 0, profile)
        )
    }

    @Test
    fun titan2EliteScanCode253IsNormalizedToSym() {
        val normalizer = HardwareKeyNormalizer()
        val profile = HardwareKeyboardProfiles.profileFor(HardwareKeyboardModel.Titan2Elite)
        assertEquals(
            KeyEvent.KEYCODE_SYM,
            normalizer.normalizeKeyCode(KeyEvent.KEYCODE_ALT_RIGHT, 253, profile)
        )
    }

    @Test
    fun titan2EliteScanCode251RemainsFunctionKey() {
        val normalizer = HardwareKeyNormalizer()
        val profile = HardwareKeyboardProfiles.profileFor(HardwareKeyboardModel.Titan2Elite)
        assertEquals(
            KeyEvent.KEYCODE_FUNCTION,
            normalizer.normalizeKeyCode(KeyEvent.KEYCODE_UNKNOWN, 251, profile)
        )
    }

    @Test
    fun modifierTrackerKeepsPhysicalStateUntilRelease() {
        val tracker = ModifierStateTracker()
        assertTrue(tracker.onKeyDown(KeyEvent.KEYCODE_SHIFT_LEFT).shiftPressed)
        assertTrue(tracker.onKeyDown(KeyEvent.KEYCODE_A, KeyEvent.META_SHIFT_ON).shiftPressed)
        assertFalse(tracker.onKeyUp(KeyEvent.KEYCODE_SHIFT_LEFT).shiftPressed)
    }

    @Test
    fun statusIconControllerRestoresBaseIconAfterModifierRelease() {
        val rendered = mutableListOf<Int?>()
        val controller = SystemStatusIconController(
            showIcon = { rendered += it },
            hideIcon = { rendered += null }
        )
        controller.setPhysicalKeyboard(true)
        controller.setBaseIcon(10)
        controller.setModifierIndicatorsEnabled(true)
        controller.setModifierState(ModifierState(shiftPressed = true))
        controller.setModifierState(ModifierState())
        assertEquals(10, rendered.last())
    }

    @Test
    fun symIconRemainsVisibleWhileVirtualPickerIsShown() {
        val rendered = mutableListOf<Int?>()
        val controller = SystemStatusIconController(
            showIcon = { rendered += it },
            hideIcon = { rendered += null }
        )
        controller.setModifierIndicatorsEnabled(true)
        controller.setPhysicalKeyboard(true)
        controller.setModifierState(ModifierState(symPressed = true))
        controller.setPhysicalKeyboard(false)
        assertEquals(R.drawable.ic_status_modifier_sym, rendered.last())
        controller.setModifierIndicatorsEnabled(false)
        assertEquals(null, rendered.last())
        controller.setModifierIndicatorsEnabled(true)
        assertEquals(R.drawable.ic_status_modifier_sym, rendered.last())
        controller.setPhysicalKeyboard(true)
        controller.setBaseIcon(10)
        controller.setModifierState(ModifierState())
        assertEquals(10, rendered.last())
        controller.clear()
        assertEquals(null, rendered.last())
    }

    @Test
    fun symIconTakesPriorityOverOtherModifiers() {
        assertEquals(
            R.drawable.ic_status_modifier_sym,
            ModifierIconResolver.resolve(ModifierState(symPressed = true, altPressed = true, shiftLatched = true))
        )
    }

    @Test
    fun hardwareKeyboardFocusDoesNotReuseVisibleSymSurface() {
        fun visible(hardware: Boolean, sym: Boolean, previous: Boolean) =
            InputDeviceManager.resolveVirtualKeyboardVisibility(hardware, sym, previous)
        // The default and editor-touch paths must both suppress the normal keyboard.
        assertFalse(visible(true, false, false))
        assertFalse(visible(true, false, true))
        // An explicit SYM request survives subsequent visibility evaluations.
        assertTrue(visible(true, true, false))
        assertTrue(visible(true, true, true))
        // Phones without physical keyboards retain their original visibility policy.
        assertTrue(visible(false, false, true))
        assertFalse(visible(false, false, false))
    }

    @Test
    fun doubleTapLocksAndThirdTapUnlocksCtrl() {
        val controller = ModifierLockController(doubleTapThresholdMs = 500)
        fun event(action: Int, keyCode: Int, time: Long) = KeyEvent(
            time, time, action, keyCode, 0, 0, 1, 0, 0, 0
        )

        controller.handleKey(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.ACTION_DOWN, 100)
        controller.handleKey(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.ACTION_UP, 150)
        controller.handleKey(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.ACTION_DOWN, 300)
        assertTrue(controller.snapshot().ctrlLatched)
        controller.handleKey(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.ACTION_UP, 350)
        controller.handleKey(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.ACTION_DOWN, 450)
        assertFalse(controller.snapshot().ctrlLatched)
    }

    @Test
    fun oneShotModifierIsAppliedToNextKeyOnly() {
        val controller = ModifierLockController(doubleTapThresholdMs = 500)
        fun event(action: Int, keyCode: Int, time: Long) = KeyEvent(
            time, time, action, keyCode, 0, 0, 1, 0, 0, 0
        )

        controller.handleKey(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.ACTION_DOWN, 100)
        controller.handleKey(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.ACTION_UP, 150)
        val down = controller.handleKey(KeyEvent.KEYCODE_A, KeyEvent.ACTION_DOWN, 200)
        assertTrue(down.syntheticMetaState and KeyEvent.META_ALT_ON != 0)
        assertFalse(controller.snapshot().altOneShot)
        val up = controller.handleKey(KeyEvent.KEYCODE_A, KeyEvent.ACTION_UP, 220)
        assertTrue(up.syntheticMetaState and KeyEvent.META_ALT_ON != 0)
    }

    @Test
    fun shiftIndicatorStaysActiveForOneShotLetterUntilRelease() {
        val controller = ModifierLockController(doubleTapThresholdMs = 500)
        controller.handleKey(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_DOWN, 100)
        controller.handleKey(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_UP, 150)
        assertTrue(controller.snapshot().shiftOneShot)
        val down = controller.handleKey(KeyEvent.KEYCODE_A, KeyEvent.ACTION_DOWN, 200)
        assertTrue(down.syntheticMetaState and KeyEvent.META_SHIFT_ON != 0)
        assertTrue(controller.snapshot().shiftInUse)
        controller.handleKey(KeyEvent.KEYCODE_A, KeyEvent.ACTION_UP, 220)
        assertFalse(controller.snapshot().shiftInUse)
        assertFalse(controller.snapshot().shiftOneShot)
    }

    @Test
    fun latchedShiftUnlocksWithSingleTapAfterAWait() {
        val controller = ModifierLockController(doubleTapThresholdMs = 500)
        controller.handleKey(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_DOWN, 100)
        controller.handleKey(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_UP, 150)
        controller.handleKey(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_DOWN, 300)
        controller.handleKey(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_UP, 350)
        assertTrue(controller.snapshot().shiftLatched)
        controller.handleKey(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_DOWN, 2_000)
        assertFalse(controller.snapshot().shiftLatched)
    }

    @Test
    fun latchedAltUnlocksAfterDelayWithoutLeavingOneShotOrIcon() {
        val controller = ModifierLockController()
        controller.tap(KeyEvent.KEYCODE_ALT_LEFT, 100)
        controller.tap(KeyEvent.KEYCODE_ALT_LEFT, 300)
        assertTrue(controller.snapshot().altLatched)
        assertEquals(
            R.drawable.ic_status_modifiers_s0_c0_a2,
            ModifierIconResolver.resolve(controller.snapshot().toModifierState())
        )

        controller.tap(KeyEvent.KEYCODE_ALT_LEFT, 5_000)
        assertFalse(controller.snapshot().altActive)
        assertFalse(controller.snapshot().altOneShot)
        assertFalse(controller.snapshot().altLatched)
        assertEquals(null, ModifierIconResolver.resolve(controller.snapshot().toModifierState()))
    }

    @Test
    fun firstLetterAfterUnlockHasNoModifierOnDownRepeatOrUp() {
        val keys = listOf(
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT
        )
        keys.forEach { key ->
            val controller = ModifierLockController()
            controller.tap(key, 100)
            controller.tap(key, 300)
            assertTrue("key=$key must lock before unlocking", controller.snapshot().toModifierState().hasIndicator)
            controller.tap(key, 5_000)

            val down = controller.handleKey(KeyEvent.KEYCODE_A, KeyEvent.ACTION_DOWN, 5_100)
            val repeat = controller.handleKey(KeyEvent.KEYCODE_A, KeyEvent.ACTION_DOWN, 5_150, repeatCount = 1)
            val up = controller.handleKey(KeyEvent.KEYCODE_A, KeyEvent.ACTION_UP, 5_200)
            listOf(down, repeat, up).forEach { result ->
                assertFalse("key=$key must not swallow the letter", result.consume)
                assertEquals("key=$key left a modifier on the letter", 0, result.syntheticMetaState)
                assertFalse("key=$key left a stale indicator", result.snapshot.toModifierState().hasIndicator)
            }
        }
    }

    @Test
    fun combinedLocksAreReleasedIndependently() {
        val controller = ModifierLockController()
        controller.tap(KeyEvent.KEYCODE_CTRL_LEFT, 100)
        controller.tap(KeyEvent.KEYCODE_CTRL_LEFT, 300)
        controller.tap(KeyEvent.KEYCODE_SHIFT_LEFT, 500)
        controller.tap(KeyEvent.KEYCODE_SHIFT_LEFT, 700)
        controller.tap(KeyEvent.KEYCODE_ALT_LEFT, 900)
        controller.tap(KeyEvent.KEYCODE_ALT_LEFT, 1_100)

        fun letter(time: Long, expectedMeta: Int, expectedIcon: Int?) {
            val down = controller.handleKey(KeyEvent.KEYCODE_A, KeyEvent.ACTION_DOWN, time)
            val up = controller.handleKey(KeyEvent.KEYCODE_A, KeyEvent.ACTION_UP, time + 50)
            assertEquals(expectedMeta, down.syntheticMetaState)
            assertEquals(expectedMeta, up.syntheticMetaState)
            assertEquals(expectedIcon, ModifierIconResolver.resolve(controller.snapshot().toModifierState()))
        }
        val ctrl = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        val shift = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        val alt = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        letter(1_300, ctrl or shift or alt, R.drawable.ic_status_modifiers_s2_c2_a2)

        controller.tap(KeyEvent.KEYCODE_CTRL_LEFT, 5_000)
        assertFalse(controller.snapshot().ctrlLatched)
        assertTrue(controller.snapshot().shiftLatched)
        assertTrue(controller.snapshot().altLatched)
        letter(5_100, shift or alt, R.drawable.ic_status_modifiers_s2_c0_a2)

        controller.tap(KeyEvent.KEYCODE_ALT_LEFT, 6_000)
        assertFalse(controller.snapshot().altLatched)
        assertTrue(controller.snapshot().shiftLatched)
        letter(6_100, shift, R.drawable.ic_status_modifiers_s2_c0_a0)

        controller.tap(KeyEvent.KEYCODE_SHIFT_LEFT, 7_000)
        letter(7_100, 0, null)
    }

    @Test
    fun unlockingDoesNotChangeReleaseOfAnAlreadyPressedLetter() {
        val controller = ModifierLockController()
        controller.tap(KeyEvent.KEYCODE_SHIFT_LEFT, 100)
        controller.tap(KeyEvent.KEYCODE_SHIFT_LEFT, 300)
        val down = controller.handleKey(KeyEvent.KEYCODE_A, KeyEvent.ACTION_DOWN, 400)

        controller.tap(KeyEvent.KEYCODE_SHIFT_LEFT, 5_000)
        assertFalse(controller.snapshot().shiftLatched)
        assertTrue(controller.snapshot().shiftInUse)
        val nextDown = controller.handleKey(KeyEvent.KEYCODE_B, KeyEvent.ACTION_DOWN, 5_100)
        assertEquals(0, nextDown.syntheticMetaState)
        val up = controller.handleKey(KeyEvent.KEYCODE_A, KeyEvent.ACTION_UP, 5_200)
        assertEquals(down.syntheticMetaState, up.syntheticMetaState)
        assertFalse(controller.snapshot().toModifierState().hasIndicator)
        assertEquals(0, controller.handleKey(KeyEvent.KEYCODE_B, KeyEvent.ACTION_UP, 5_300).syntheticMetaState)
    }

    @Test
    fun symModeSwitchesToModifierModeImmediately() {
        val pageStates = mutableListOf<Boolean>()
        val controller = SymModeController({ pageStates += it }, { _, _ -> })
        controller.handleKey(KeyEvent.KEYCODE_SYM, KeyEvent.ACTION_DOWN)
        controller.handleKey(KeyEvent.KEYCODE_SYM, KeyEvent.ACTION_UP)
        assertTrue(controller.isPageOpen())

        controller.switchToModifierMode()
        assertFalse(controller.isPageOpen())
        assertEquals(listOf(true, false), pageStates)
    }

    @Test
    fun startingSymClearsLatchedModifierMode() {
        val controller = ModifierLockController()
        controller.tap(KeyEvent.KEYCODE_CTRL_LEFT, 100)
        controller.tap(KeyEvent.KEYCODE_CTRL_LEFT, 300)
        assertTrue(controller.hasActiveModifier())
        controller.reset()
        assertFalse(controller.hasActiveModifier())
    }

    private fun ModifierLockController.tap(keyCode: Int, time: Long) {
        assertTrue(handleKey(keyCode, KeyEvent.ACTION_DOWN, time).consume)
        assertTrue(handleKey(keyCode, KeyEvent.ACTION_UP, time + 50).consume)
    }

    @Test
    fun symChordCommitsAndConsumesBothEvents() {
        val committed = mutableListOf<String>()
        val controller = SymModeController({ _ -> }, { text, _ -> committed += text })
        fun event(action: Int, keyCode: Int, time: Long) = KeyEvent(
            time, time, action, keyCode, 0, 0, 1, 0, 0, 0
        )

        assertTrue(controller.handleKey(KeyEvent.KEYCODE_SYM, KeyEvent.ACTION_DOWN).consume)
        val commit = controller.handleKey(KeyEvent.KEYCODE_M, KeyEvent.ACTION_DOWN)
        assertTrue(commit.consume)
        commit.committedText?.let { committed += it }
        assertTrue(controller.handleKey(KeyEvent.KEYCODE_M, KeyEvent.ACTION_UP).consume)
        assertEquals("$", committed.single())
    }

    @Test
    fun symTapTogglesPageState() {
        val pageStates = mutableListOf<Boolean>()
        val controller = SymModeController({ pageStates += it }, { _, _ -> })
        controller.handleKey(KeyEvent.KEYCODE_SYM, KeyEvent.ACTION_DOWN)
        controller.handleKey(KeyEvent.KEYCODE_SYM, KeyEvent.ACTION_UP)
        controller.handleKey(KeyEvent.KEYCODE_SYM, KeyEvent.ACTION_DOWN)
        controller.handleKey(KeyEvent.KEYCODE_SYM, KeyEvent.ACTION_UP)
        assertEquals(listOf(true, false), pageStates)
    }

    @Test
    fun keyOnOpenSymPageCommitsMappedSymbol() {
        val committed = mutableListOf<String>()
        val controller = SymModeController({ _ -> }, { text, _ -> committed += text })
        controller.handleKey(KeyEvent.KEYCODE_SYM, KeyEvent.ACTION_DOWN)
        controller.handleKey(KeyEvent.KEYCODE_SYM, KeyEvent.ACTION_UP)
        val result = controller.handleKey(KeyEvent.KEYCODE_M, KeyEvent.ACTION_DOWN)
        result.committedText?.let { committed += it }
        controller.handleKey(KeyEvent.KEYCODE_M, KeyEvent.ACTION_UP)
        assertEquals(listOf("$"), committed)
    }

    @Test
    fun symMappingFollowsVisiblePickerPage() {
        val controller = SymModeController({ _ -> }, { _, _ -> })
        controller.updatePageSymbols(('a'..'z').map(Char::toString))
        controller.handleKey(KeyEvent.KEYCODE_SYM, KeyEvent.ACTION_DOWN)
        controller.handleKey(KeyEvent.KEYCODE_SYM, KeyEvent.ACTION_UP)

        val result = controller.handleKey(KeyEvent.KEYCODE_Q, KeyEvent.ACTION_DOWN)
        assertEquals("a", result.committedText)
        controller.handleKey(KeyEvent.KEYCODE_Q, KeyEvent.ACTION_UP)
    }
}
