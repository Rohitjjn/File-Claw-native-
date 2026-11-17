package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Light Palette (Claude Classic Warm Off-White & Terracotta)
val ClaudeTerracotta = Color(0xFFCC785C) // Signature Antigravity Coral/Terracotta
val ClaudeSoftTomato = Color(0xFFA9583E) // Coral Active
val ClaudeAlabaster = Color(0xFFFAF9F5) // Warm cream canvas
val ClaudeCharcoal = Color(0xFF141413) // Earthy Ink text
val ClaudeMutedText = Color(0xFF6C6A64) // Muted editorial text
val ClaudeSandBorder = Color(0xFFE6DFD8) // Elegant hairline border
val ClaudeSelectedBg = Color(0xFFEFE9DE) // Light cream card surface
val ClaudeWhite = Color(0xFFFAF9F5) // Warm cream layout background

// Dark Palette
val ClaudeOnyx = Color(0xFF181715) // Surface Dark
val ClaudeDarkSurface = Color(0xFF252320) // Surface Dark Elevated
val ClaudeDarkBorder = Color(0xFF2D2C2A) // Dark hairline border
val ClaudeDarkText = Color(0xFFFAF9F5) // On Dark text
val ClaudeDarkMutedText = Color(0xFFA09D96) // On Dark soft text
val ClaudeDarkSelectedBg = Color(0xFF1F1E1B) // Surface Dark Soft
val ClaudeOrangeAccent = Color(0xFFE8A55A) // Accent Amber


// ─────────────────────────────────────────────────────────────────────────────
// Phase 3a semantic tier (docs/UI_FRONTIER.md §4.1). Light + dark pairs live side by side;
// the active side is chosen in Theme.kt. Nothing outside this file should invent hex values.
// ─────────────────────────────────────────────────────────────────────────────

// Light semantic tokens
val ClaudeSurfaceContainerLight = Color(0xFFF4F1EA)      // insets, wells, code blocks
val ClaudeSurfaceContainerHighLight = Color(0xFFECE8DE)   // pressed cards
val ClaudeOnSurfaceVariantLight = Color(0xFF57544E)      // secondary "muted" text
val ClaudeSuccessLight = Color(0xFF5B7B4E)               // warm sage
val ClaudeWarningLight = Color(0xFF8F5B1C)               // warm ochre (contrast-tuned 2026-11-17: 5.42:1 on paper; spec table updated)
val ClaudeDangerLight = Color(0xFFA6492F)                // terracotta-deep
val ClaudeSelectionFillLight = Color(0xFFEFE9DE)         // selected rows/cards
val ClaudeLinkLight = ClaudeTerracotta                   // never blue

// Dark semantic tokens
val ClaudeSurfaceContainerDark = Color(0xFF2E2C28)        // insets, wells, code blocks
val ClaudeSurfaceContainerHighDark = Color(0xFF353230)    // pressed cards
val ClaudeOnSurfaceVariantDark = Color(0xFFB9B6AE)        // secondary "muted" text
val ClaudeSuccessDark = Color(0xFFA9C79A)                 // warm sage
val ClaudeWarningDark = Color(0xFFE8A55A)                 // warm amber
val ClaudeDangerDark = Color(0xFFE08B6D)                 // warm terracotta
val ClaudeSelectionFillDark = Color(0xFF242220)           // selected rows/cards
val ClaudeLinkDark = ClaudeOrangeAccent                   // never blue

// Format-family tint wells — ICONOGRAPHY ONLY (file-family glyph backgrounds), never large surfaces.
// Light hexes are the §4.1 spec; dark hexes are the same hues at 16 % over ClaudeOnyx (computed, not invented).
val ClaudeWellDocsLight = Color(0xFFEBD9CF)
val ClaudeWellDataLight = Color(0xFFE4E8DC)
val ClaudeWellMediaLight = Color(0xFFE8DFF0)
val ClaudeWellArchivesLight = Color(0xFFE8E3CE)
val ClaudeWellCodeLight = Color(0xFFE0E6EA)
val ClaudeWellDocsDark = Color(0xFF3A3633)
val ClaudeWellDataDark = Color(0xFF393835)
val ClaudeWellMediaDark = Color(0xFF393738)
val ClaudeWellArchivesDark = Color(0xFF393833)
val ClaudeWellCodeDark = Color(0xFF383837)
