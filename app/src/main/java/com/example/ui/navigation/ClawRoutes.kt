package com.example.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations for Files Claw.
 *
 * Replaces the former string routes ("splash", "home", …). Every destination is
 * parameter-free by design: the currently-open file lives in [MainViewModel]
 * (see docs/TODO.md KNOWN ISSUES for why that singleton is still intentional —
 * moving file state into route arguments is a Phase 3+ redesign, not Phase 2).
 */
sealed interface ClawDestination {
    @Serializable
    data object Splash : ClawDestination

    @Serializable
    data object Home : ClawDestination

    @Serializable
    data object AllFiles : ClawDestination

    @Serializable
    data object Search : ClawDestination

    @Serializable
    data object Preview : ClawDestination

    @Serializable
    data object Editor : ClawDestination

    @Serializable
    data object Settings : ClawDestination
}
