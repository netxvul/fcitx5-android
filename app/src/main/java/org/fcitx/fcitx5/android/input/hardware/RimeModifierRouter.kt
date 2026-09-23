/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.hardware

import android.view.KeyEvent

/**
 * Resolves the ambiguity between a Rime modifier tap and Supra's double-tap
 * lock. A short tap is held until the double-tap window expires. A second tap
 * enters the normal [ModifierLockController] latch state; a regular key turns
 * the pending modifier into a one-shot or held modifier.
 */
class RimeModifierRouter(
    private val modifierLockController: ModifierLockController,
    private val scheduler: Scheduler,
    private val delayMs: () -> Long,
    private val dispatchRaw: (KeyEvent) -> Unit,
    private val onStateChanged: () -> Unit = {}
) {
    interface Scheduler {
        fun postDelayed(task: Runnable, delayMs: Long)
        fun remove(task: Runnable)
    }

    private data class Pending(
        val modifier: HardwareModifier,
        val down: KeyEvent,
        var up: KeyEvent? = null,
        var forwarded: Boolean = false,
        var suppressTap: Boolean = false,
        var lockActivated: Boolean = false
    )

    private val pending = mutableMapOf<HardwareModifier, Pending>()
    private val tasks = mutableMapOf<HardwareModifier, Runnable>()

    fun handle(event: KeyEvent): ModifierRoutingResult {
        return handle(
            event.keyCode,
            event.action,
            if (event.eventTime > 0) event.eventTime else 0L,
            event.repeatCount,
            event,
            true
        )
    }

    fun handle(
        keyCode: Int,
        action: Int,
        eventTime: Long,
        repeatCount: Int,
        event: KeyEvent,
        buildRoutedEvent: Boolean = true
    ): ModifierRoutingResult {
        val modifier = modifierFor(keyCode)
        return if (modifier != null) {
            handleModifier(modifier, keyCode, action, eventTime, repeatCount, event, buildRoutedEvent)
        } else {
            resolveHeldModifiers()
            modifierLockController.handle(
                keyCode,
                event,
                action,
                eventTime,
                repeatCount,
                enabled = true,
                buildRoutedEvent = buildRoutedEvent
            )
        }.also { onStateChanged() }
    }

    fun reset() {
        tasks.values.forEach(scheduler::remove)
        tasks.clear()
        pending.clear()
        modifierLockController.reset()
        onStateChanged()
    }

    private fun handleModifier(
        modifier: HardwareModifier,
        keyCode: Int,
        action: Int,
        eventTime: Long,
        repeatCount: Int,
        event: KeyEvent,
        buildRoutedEvent: Boolean
    ): ModifierRoutingResult {
        val current = pending[modifier]
        return when (action) {
            KeyEvent.ACTION_DOWN -> {
                if (repeatCount != 0) {
                    resolveHeld(modifier)
                    val result = modifierLockController.handle(
                        keyCode,
                        event,
                        action,
                        eventTime,
                        repeatCount,
                        enabled = true,
                        buildRoutedEvent = buildRoutedEvent
                    )
                    if (pending[modifier]?.forwarded == true) dispatchRaw(event)
                    result
                } else if (current == null) {
                    val wasLatched = modifierLockController.isLatched(modifier)
                    val result = modifierLockController.handle(
                        keyCode,
                        event,
                        action,
                        eventTime,
                        repeatCount,
                        enabled = true,
                        buildRoutedEvent = buildRoutedEvent
                    )
                    pending[modifier] = Pending(
                        modifier = modifier,
                        down = event,
                        suppressTap = wasLatched
                    )
                    result
                } else if (current.up != null && !current.lockActivated) {
                    cancelTask(modifier)
                    val result = modifierLockController.handle(
                        keyCode,
                        event,
                        action,
                        eventTime,
                        repeatCount,
                        enabled = true,
                        buildRoutedEvent = buildRoutedEvent
                    )
                    current.lockActivated = modifierLockController.isLatched(modifier)
                    current.suppressTap = true
                    current.up = null
                    result
                } else {
                    modifierLockController.handle(
                        keyCode,
                        event,
                        action,
                        eventTime,
                        repeatCount,
                        enabled = true,
                        buildRoutedEvent = buildRoutedEvent
                    )
                }
            }

            KeyEvent.ACTION_UP -> {
                val result = modifierLockController.handle(
                    keyCode,
                    event,
                    action,
                    eventTime,
                    repeatCount,
                    enabled = true,
                    buildRoutedEvent = buildRoutedEvent
                )
                val pendingModifier = pending[modifier]
                when {
                    pendingModifier == null -> result
                    pendingModifier.lockActivated || pendingModifier.suppressTap -> {
                        pending.remove(modifier)
                        cancelTask(modifier)
                        result
                    }
                    pendingModifier.forwarded -> {
                        pending.remove(modifier)
                        cancelTask(modifier)
                        dispatchRaw(event)
                        result
                    }
                    else -> {
                        pendingModifier.up = event
                        scheduleTapReplay(pendingModifier)
                        result
                    }
                }
            }

            else -> modifierLockController.handle(
                keyCode,
                event,
                action,
                eventTime,
                repeatCount,
                enabled = true,
                buildRoutedEvent = buildRoutedEvent
            )
        }
    }

    private fun resolveHeldModifiers() {
        pending.values
            .filter { it.up == null && !it.forwarded }
            .toList()
            .forEach { resolveHeld(it.modifier) }
    }

    private fun resolveHeld(modifier: HardwareModifier) {
        val current = pending[modifier] ?: return
        if (current.up != null || current.forwarded) return
        current.forwarded = true
        dispatchRaw(current.down)
    }

    private fun scheduleTapReplay(current: Pending) {
        cancelTask(current.modifier)
        val task = Runnable {
            val active = pending[current.modifier]
            val up = active?.up
            if (active !== current || up == null || current.forwarded || current.lockActivated) return@Runnable
            pending.remove(current.modifier)
            tasks.remove(current.modifier)
            modifierLockController.cancelPendingTap(current.modifier)
            dispatchRaw(current.down)
            dispatchRaw(up)
            onStateChanged()
        }
        tasks[current.modifier] = task
        scheduler.postDelayed(task, delayMs().coerceIn(1L, 5000L))
    }

    private fun cancelTask(modifier: HardwareModifier) {
        tasks.remove(modifier)?.let(scheduler::remove)
    }

    private fun modifierFor(keyCode: Int): HardwareModifier? = when (keyCode) {
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> HardwareModifier.Shift
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> HardwareModifier.Ctrl
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> HardwareModifier.Alt
        else -> null
    }
}
