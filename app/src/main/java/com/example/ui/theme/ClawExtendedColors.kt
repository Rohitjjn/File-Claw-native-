package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Extended semantic colors that Material3 has no slot for (docs/UI_FRONTIER.md §4.1):
 * save/confirm (success), truncated/read-only (warning), destructive (danger), selected rows
 * (selectionFill), links (link), and the format-family tint wells (iconography only).
 * The ACTIVE side is provided by FilesClawTheme; call sites read via LocalClawColors.current.
 */
@Immutable
data class ClawExtendedColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val selectionFill: Color,
    val link: Color,
    val wellDocs: Color,
    val wellData: Color,
    val wellMedia: Color,
    val wellArchives: Color,
    val wellCode: Color,
)

val LightClawColors = ClawExtendedColors(
    success = ClaudeSuccessLight,
    warning = ClaudeWarningLight,
    danger = ClaudeDangerLight,
    selectionFill = ClaudeSelectionFillLight,
    link = ClaudeLinkLight,
    wellDocs = ClaudeWellDocsLight,
    wellData = ClaudeWellDataLight,
    wellMedia = ClaudeWellMediaLight,
    wellArchives = ClaudeWellArchivesLight,
    wellCode = ClaudeWellCodeLight,
)

val DarkClawColors = ClawExtendedColors(
    success = ClaudeSuccessDark,
    warning = ClaudeWarningDark,
    danger = ClaudeDangerDark,
    selectionFill = ClaudeSelectionFillDark,
    link = ClaudeLinkDark,
    wellDocs = ClaudeWellDocsDark,
    wellData = ClaudeWellDataDark,
    wellMedia = ClaudeWellMediaDark,
    wellArchives = ClaudeWellArchivesDark,
    wellCode = ClaudeWellCodeDark,
)

val LocalClawColors = staticCompositionLocalOf { LightClawColors }
