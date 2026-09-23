/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.hardware

import android.os.SystemClock
import android.view.KeyEvent

enum class HardwareModifier {
    Shift, Ctrl, Alt
}

data class ModifierLockSnapshot(
    val shiftPhysical: Boolean = false,
    val ctrlPhysical: Boolean = false,
    val altPhysical: Boolean = false,
    val shiftOneShot: Boolean = false,
    val ctrlOneShot: Boolean = false,
    val altOneShot: Boolean = false,
    val shiftLatched: Boolean = false,
    val ctrlLatched: Boolean = false,
    val altLatched: Boolean = false,
    val shiftInUse: Boolean = false,
    val ctrlInUse: Boolean = false,
    val altInUse: Boolean = false
) {
    val shiftActive get() = shiftPhysical || shiftOneShot || shiftLatched
    val ctrlActive get() = ctrlPhysical || ctrlOneShot || ctrlLatched
    val altActive get() = altPhysical || altOneShot || altLatched

    fun toModifierState() = ModifierState(
        shiftPressed = shiftActive || shiftInUse,
        ctrlPressed = ctrlActive || ctrlInUse,
        altPressed = altActive || altInUse,
        shiftOneShot = shiftOneShot,
        ctrlOneShot = ctrlOneShot,
        altOneShot = altOneShot,
        shiftLatched = shiftLatched,
        ctrlLatched = ctrlLatched,
        altLatched = altLatched
    )
}

data class ModifierRoutingResult(
    val consume: Boolean,
    val event: KeyEvent,
    val syntheticMetaState: Int = 0,
    val snapshot: ModifierLockSnapshot
)

data class ModifierKeyDecision(
    val consume: Boolean,
    val syntheticMetaState: Int,
    val snapshot: ModifierLockSnapshot
)

/**
 * Implements Supra's one-shot and double-tap latch behavior without sending
 * consumed modifier events to Fcitx. Normal keys receive a synthetic meta
 * state while a modifier is latched or being used as a one-shot modifier.
 */
class ModifierLockController(
    doubleTapThresholdMs: Long = 500L,
    private val now: () -> Long = { SystemClock.uptimeMillis() }
) {
    private var doubleTapThresholdMs = doubleTapThresholdMs.coerceAtLeast(1L)
    private class State {
        val pressedKeys = mutableSetOf<Int>()
        val physical get() = pressedKeys.isNotEmpty()
        var oneShot = false
        var latched = false
        var downAt = 0L
        var lastReleaseAt = Long.MIN_VALUE
        var lastTapWasQuick = false
        var usedWhileHeld = false
        var toggledLock = false
    }

    private val states = HardwareModifier.entries.associateWith { State() }
    // Each key retains its modifiers through repeats and its matching release.
    // A global mask loses state when two letter keys overlap.
    private val inFlight = mutableMapOf<Int, Int>()

    fun handle(event: KeyEvent, enabled: Boolean = true): ModifierRoutingResult {
        return handle(
            event.keyCode,
            event,
            event.action,
            if (event.eventTime > 0) event.eventTime else now(),
            event.repeatCount,
            enabled
        )
    }

    fun handle(
        keyCode: Int,
        event: KeyEvent,
        action: Int,
        eventTime: Long,
        repeatCount: Int = 0,
        enabled: Boolean = true,
        buildRoutedEvent: Boolean = true
    ): ModifierRoutingResult {
        val decision = handleKey(
            keyCode, action, if (eventTime > 0) eventTime else now(), repeatCount, enabled
        )
        val routed = if (decision.consume || !enabled || !buildRoutedEvent) event
            else withMetaState(event, decision.syntheticMetaState)
        return ModifierRoutingResult(
            decision.consume, routed, decision.syntheticMetaState, decision.snapshot
        )
    }

    /** The production event path and tests share the same state transitions. */
    fun handleKey(
        keyCode: Int,
        action: Int,
        eventTime: Long,
        repeatCount: Int = 0,
        enabled: Boolean = true
    ): ModifierKeyDecision {
        if (!enabled) {
            reset()
            return ModifierKeyDecision(false, 0, snapshot())
        }
        val modifier = modifierFor(keyCode)
        if (modifier != null) {
            updateModifier(modifier, keyCode, action, eventTime, repeatCount)
            return ModifierKeyDecision(true, 0, snapshot())
        }
        val appliedMeta = when (action) {
            KeyEvent.ACTION_DOWN -> inFlight.getOrPut(keyCode) {
                activeMetaState().also { consumePendingModifiers() }
            }
            KeyEvent.ACTION_UP -> inFlight.remove(keyCode) ?: 0
            else -> 0
        }
        return ModifierKeyDecision(false, appliedMeta, snapshot())
    }

    private fun consumePendingModifiers() {
        states.values.forEach {
            it.oneShot = false
            it.lastTapWasQuick = false
            if (it.physical) it.usedWhileHeld = true
        }
    }

    /** Screen-key taps have no hardware key-up; consume their one-shot immediately. */
    fun consumeShiftFromScreen() {
        states.getValue(HardwareModifier.Shift).apply {
            oneShot = false
            lastTapWasQuick = false
            if (physical) usedWhileHeld = true
        }
    }

    fun toggleShiftFromScreen(lock: Boolean) {
        states.getValue(HardwareModifier.Shift).apply {
            if (lock) {
                latched = !latched
                oneShot = false
            } else if (latched || oneShot) {
                latched = false
                oneShot = false
            } else {
                oneShot = true
            }
            lastTapWasQuick = false
        }
    }

    fun reset() {
        states.values.forEach {
            it.pressedKeys.clear()
            it.oneShot = false
            it.latched = false
            it.downAt = 0L
            it.lastReleaseAt = Long.MIN_VALUE
            it.lastTapWasQuick = false
            it.usedWhileHeld = false
            it.toggledLock = false
        }
        inFlight.clear()
    }

    fun snapshot(): ModifierLockSnapshot {
        val inUse = inFlight.values.fold(0) { acc, meta -> acc or meta }
        return ModifierLockSnapshot(
            shiftPhysical = states.getValue(HardwareModifier.Shift).physical,
            ctrlPhysical = states.getValue(HardwareModifier.Ctrl).physical,
            altPhysical = states.getValue(HardwareModifier.Alt).physical,
            shiftOneShot = states.getValue(HardwareModifier.Shift).oneShot,
            ctrlOneShot = states.getValue(HardwareModifier.Ctrl).oneShot,
            altOneShot = states.getValue(HardwareModifier.Alt).oneShot,
            shiftLatched = states.getValue(HardwareModifier.Shift).latched,
            ctrlLatched = states.getValue(HardwareModifier.Ctrl).latched,
            altLatched = states.getValue(HardwareModifier.Alt).latched,
            shiftInUse = inUse and KeyEvent.META_SHIFT_ON != 0,
            ctrlInUse = inUse and KeyEvent.META_CTRL_ON != 0,
            altInUse = inUse and KeyEvent.META_ALT_ON != 0
        )
    }

    fun hasActiveModifier(): Boolean = snapshot().toModifierState().hasIndicator

    fun setDoubleTapThresholdMs(value: Long) {
        doubleTapThresholdMs = value.coerceAtLeast(1L)
    }

    fun isLatched(modifier: HardwareModifier): Boolean =
        states.getValue(modifier).latched

    /** Clears a quick tap after it has been replayed as a raw Rime event. */
    fun cancelPendingTap(modifier: HardwareModifier) {
        states.getValue(modifier).apply {
            if (!physical && !latched) {
                oneShot = false
                lastReleaseAt = Long.MIN_VALUE
                lastTapWasQuick = false
                toggledLock = false
            }
        }
    }

    private fun updateModifier(
        modifier: HardwareModifier,
        keyCode: Int,
        action: Int,
        currentTime: Long,
        repeatCount: Int
    ) {
        val state = states.getValue(modifier)
        if (action == KeyEvent.ACTION_DOWN) {
            if (repeatCount != 0 || keyCode in state.pressedKeys) return
            if (!state.physical) {
                val isDoubleTap = state.oneShot && state.lastTapWasQuick &&
                    currentTime - state.lastReleaseAt in 0..doubleTapThresholdMs
                state.toggledLock = state.latched || isDoubleTap
                when {
                    state.latched -> state.latched = false // single tap unlocks at any time
                    isDoubleTap -> state.latched = true
                }
                state.oneShot = false
                state.usedWhileHeld = false
                state.downAt = currentTime
            }
            state.pressedKeys.add(keyCode)
        } else if (action == KeyEvent.ACTION_UP) {
            if (!state.pressedKeys.remove(keyCode) || state.physical) return
            val duration = (currentTime - state.downAt).coerceAtLeast(0L)
            val tap = !state.usedWhileHeld && !state.toggledLock &&
                duration <= doubleTapThresholdMs
            state.oneShot = tap
            state.lastReleaseAt = currentTime
            state.lastTapWasQuick = tap
        }
    }

    private fun activeMetaState(): Int {
        val state = snapshot()
        var result = 0
        if (state.shiftActive) result = result or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        if (state.ctrlActive) result = result or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (state.altActive) result = result or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        return result
    }

    private fun withMetaState(event: KeyEvent, metaState: Int): KeyEvent {
        if (metaState == event.metaState) return event
        return KeyEvent(
            event.downTime,
            event.eventTime,
            event.action,
            event.keyCode,
            event.repeatCount,
            event.metaState or metaState,
            event.deviceId,
            event.scanCode,
            event.flags,
            event.source
        )
    }

    private fun modifierFor(keyCode: Int): HardwareModifier? = when (keyCode) {
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> HardwareModifier.Shift
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> HardwareModifier.Ctrl
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> HardwareModifier.Alt
        else -> null
    }
}
