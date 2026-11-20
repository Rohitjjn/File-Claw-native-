package com.example.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

/**
 * Motion system (docs/UI_FRONTIER.md §4.4). Motion explains — every animation answers
 * "where did this come from / where does it go". Never blocks input; ≤ 320 ms.
 * Call sites must honor the reduced-motion setting (system animation scale == 0 or
 * Settings toggle) by gating non-essential motion through [rememberMotionScale]-style checks.
 */
object Motion {
    /** fast = selection/focus changes, small reveals */
    const val FAST_MS = 120
    /** base = most state changes (the default) */
    const val BASE_MS = 180
    /** slow = sheet/dialog settle, larger layout transitions */
    const val SLOW_MS = 240
    /** hero = shared-axis screen transitions only — nothing else earns 320 ms */
    const val HERO_MS = 320

    /** emphasized (0.2, 0, 0, 1) — default deceleration for entering content */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** emphasizedDecelerate (0, 0, 0, 1) — full stop into rest */
    val EmphasizedDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** emphasizedAccelerate (0.3, 0, 0.8, 0.15) — leaving content, in exactly one beat */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** standard (0.4, 0, 0.2, 1) — symmetric crossfades */
    val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
}

/**
 * Haptics map (§4.4). Constants name the *meaning*; call sites pick the concrete
 * `HapticFeedbackType`. `TICK` = selection/hover changes, `CONFIRM` = save/print success,
 * `WARN` = delete intent / unsafe action about to happen, `HEAVY` = destructive commit.
 */
object Haptics {
    const val TICK = "tick"
    const val CONFIRM = "confirm"
    const val WARN = "warn"
    const val HEAVY = "heavy"
}
