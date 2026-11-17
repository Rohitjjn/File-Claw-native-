# Files Claw — UI Frontier Plan (Phase 0 design audit, 2026-11-17)

**Status:** design document only — **no UI code may change until Phase 3.** Phase 1 = build-green +
broken-format repairs; Phase 2 = architecture splits. This file is the spec Phase 3 executes against.
Written as a frontier-design review in the Claude tradition: warm, editorial, quiet, exact.

Grounded in what exists today: `ui/theme/{Color,Type,Glass,Theme}.kt`, `ui/component/ClaudeComponents.kt`,
`ui/screens/*.kt` (FilePreviewScreen ≈ 5,390 lines), `docs/DESIGN.md`.

---

## 1. The verdict up front

The app already *speaks* Claude — warm paper `#FAF9F5`, charcoal `#141413`, terracotta `#CC785C`,
serif display type, hairline strokes, restrained glass. What it is not yet is *Claude-finished*:
the system is a coat of paint, not a behaviour. Scaffolds paint opaque backgrounds (glass can't glow
through), every screen re-implements its own chrome by hand, the reading surfaces don't use the
editorial type system, and there is no spatial or motion grammar tying it together.

**North star: "The Quiet Desk."** Files Claw is a calm, well-lit study desk, not a dashboard.
One accent, generous margins, hairlines instead of shadows, type that breathes, motion that explains.
Premium = *restraint executed perfectly*, not more surface.

---

## 2. Where the UI stands today (honest inventory)

**Working / genuinely good**
- Design tokens exist (`Color.kt`, glass fills + hairline gradient + sheen in `Glass.kt`); DESIGN.md is real.
- `claude_app_bar` is in use; `ClaudeCard`, `ClaudeButton`, `FileExtensionBadge`, `StaggeredAnimatedItem`,
  `premiumLoadingPulse`, grid/list toggle, favorites tabs, compact pill search (Loop 4), settings
  redesign (Loop 15), skeleton loading for PDF/DOCX (Loop 14), floating reader chrome with auto-fade.
- Serif display typography tokens exist in `Type.kt` (Copernicus/Tiempos-like) — but **the app uses
  `FontFamily.Serif` generic, never a bundled face**, so the signature voice is whatever the OEM ships.

**Not working yet (the frontier gaps)**
1. **Glass is invisible** — opaque `Scaffold` backgrounds; `GlassBackdrop` isn't mounted at root;
   sheets/dialogs/snackbars aren't glass.
2. **No bundled typefaces** — Serif/Sans are platform generics; Claude's voice can't be controlled.
3. **Chrome re-implemented per screen** — three hand-rolled compact search bars (Home/AllFiles/Search),
   inconsistent top bars, mixed icon styles (deprecated `Icons.Default.*`).
4. **Reading surfaces aren't editorial** — PDF/TXT/MD readers use default WebView/Text views;
   `Type.kt` serif tokens go unused where they'd matter most (the document itself).
5. **No motion system** — stagger fade exists; no shared-axis screen transitions, no predictive-back
   morphing, no unified durations/easings; animations are one-off `tween` calls.
6. **Monolith preview screen** — 5,390 lines of mixed chrome + viewer logic; UI bleeds between pages.
7. **No tablet/adaptive mode** — single-pane stretch on 7″+ screens; no nav rail; no two-pane list-detail.
8. **No keyboard/focus model** — unusable with hardware keyboards/ChromeOS; no command palette,
   no shortcuts, Tab order accidental.
9. **Accessibility unaudited** — icon buttons without labels, contrast of "muted" `#A09D96` on glass
   unmeasured, font-scale overflow untested, tables/grids not screen-reader-friendly.
10. **Empty states are functional, not graceful**; error states are inconsistent (spinner-forever bugs
    — see `docs/FILE_SUPPORT_AUDIT.md` AUD-005/006/008).
11. **English hard-coded everywhere** — no RTL mirroring validation (`supportsRtl=true` but untested).
12. **Iconography is Material-default** — no Files Claw glyph system (file-family sigils) of its own.

---

## 3. Design principles (the law for Phase 3)

1. **Content is the interface.** Chrome is temporary, quiet, and gets out of the way (auto-hide, focus mode).
2. **Warm, never cold.** Neutrals drift warm (paper/ink), never clinical greys/blues. Error is terracotta-deep, not red.
3. **One accent.** Terracotta `#CC785C` for selection/action only. Amber `#E8A55A` in dark mode only. Nothing else competes.
4. **Hairlines, not shadows.** 1 dp strokes; depth via tonal steps (`background → surface → fill → fillStrong`).
5. **Serif for thought, sans for interface.** Documents and editorial titles = serif; UI labels/metadata = sans.
6. **Motion explains.** Every animation answers "where did this come from / where does it go" — shared-axis, fade-through, morph. ≤ 250 ms, ease-out; never blocks input.
7. **Honest state.** Every screen has designed empty/loading/error/truncated states; nothing is a blank canvas or an eternal spinner.
8. **Offline cathedral.** 100 % local, calm, and confident — the UI should *feel* like it respects your files.

---

## 4. Token frontier (Phase 3a)

### 4.1 Colour — add semantic tier, keep the classics
Keep `background/surface/primary/onBackground/outline` (see DESIGN.md). Add:

| Token | Light | Dark | Use |
|---|---|---|---|
| `surfaceContainer` | `#F4F1EA` | `#2E2C28` | insets, wells, code blocks |
| `surfaceContainerHigh` | `#ECE8DE` | `#353230` | pressed cards |
| `onSurfaceVariant` | `#57544E` | `#B9B6AE` | secondary text (replaces raw alpha hacks) |
| `success` | `#5B7B4E` (warm sage) | `#A9C79A` | save/backup confirmations |
| `warning` | `#B07A2A` (warm ochre) | `#E8A55A` | truncated/read-only banners |
| `danger` | `#A6492F` (terracotta-deep) | `#E08B6D` | delete/slip/unsafe |
| `selectionFill` | `#EFE9DE` | `#242220` | selected rows/cards |
| `link` | `#CC785C` | `#E8A55A` | links (never blue) |

**Format-family tint wells** (file icon backgrounds, *iconography only*, never large surfaces):
docs `#EBD9CF` · data `#E4E8DC` · media `#E8DFF0` · archives `#E8E3CE` · code `#E0E6EA` (light) — dark variants at 16 % alpha over onSurface. Three uses max per screen.

**Banned:** pure black `#000`, pure white `#FFF`, saturated blue/green/red accents, per-format rainbow badges.

### 4.2 Typography — bundle the actual voices (Sil OFL, shipped in APK)
- **Serif (reading & editorial):** *Source Serif 4* (16–72 sp; optical sizes; true italics) — replaces `FontFamily.Serif` everywhere, incl. viewer content backgrounds/headers where we control layout.
- **Sans (UI):** *Inter* — variable font; replaces `FontFamily.Default`. `titleLarge`→ 20/26 w600; metadata w500; tabular numerals for sizes/page counts (`fontFeatureSettings "tnum"`).
- **Mono (code/hex/path):** *JetBrains Mono* or *IBM Plex Mono* — ligatures off by default in viewer, on in editor setting.
- Keep the existing scale; add: `displayLarge` 54/60 serif (Home hero), `labelSmall` 11/14 sans w600 tracked +0.8 (badges/eyebrows), `readingBody` 17/28 serif (TXT/MD/RTF content — **the document finally gets the editorial face**).
- Line-length cap for all reading surfaces: 68ch; measure-driven margins (`Modifier.widthIn(max = 680.dp)`).

### 4.3 Shape, spacing, stroke
- Radius: 8 (controls) · 12 (cards/inputs) · 16 (sheets) · 24 (hero/dialog) · full (pills). Drop the stray 4 dp.
- Spacing grid 4 dp: 4/8/12/16/24/32/48/64. Content horizontal padding: 16 (phone) / 24 (large) / 32 (tablet).
- Hairlines: 1 dp; focused/`outlineStrong` 1.5 dp terracotta for inputs (replaces elevation).

### 4.4 Motion tokens (new `ui/theme/Motion.kt`)
- Durations: `fast` 120 ms · `base` 180 ms · `slow` 240 ms · `hero` 320 ms (only for shared-axis).
- Easings: `emphasized` (0.2,0,0,1) · `emphasizedDecelerate` (0,0,0,1) · `emphasizedAccelerate` · `standard` (0.2,0,0,1? use 0.4,0,0.2,1).
- Patterns: `fade-through` (list→detail), `shared-axis-x` (wizard/editor), `morph` (FAB→search via `SharedTransitionLayout`), predictive-back follows gesture (AndroidX predictive back APIs).
- Haptics map: `tick` (selection) · `confirm` (save/print) · `warn` (delete intent) · `heavy` (destructive commit).
- `prefers-reduced-motion` + system animation scale honoured (gate all non-essential motion).

### 4.5 Glass v2
Mount `GlassBackdrop` at the NavHost root (inside a transparent Scaffold policy) so chrome floats over ambient glow: three radial gradients — terracotta/amber/lavender — at ≤ 28 % alpha (dark) / ≤ 22 % (light). Scaffolds become transparent; overlapping-crossfade overdraw solved via NavHost transition policy (fade-through, 240 ms).
Apply glass to: top app bars, bottom command pills, sheets, dialogs, snackbars, search field.
Keep opaque: reading surfaces, hex, code, text editor, CSV grid cells (DESIGN rule #1 stays law).

---

## 5. Navigation & layout system (Phase 3b)

### 5.1 Phone architecture (unchanged in spirit, perfected in craft)
- Root: bottom-less, **slim top chrome** — wordmark "Files Claw" (serif, 18 sp) left; search + settings glyph right.
- **Quick Open / Command Palette** (`Ctrl/Cmd + K`, hardware mic key, or long-press Home):
  fuzzy finder over *all files + actions* ("save", "print", "toggle theme", "open recents") —
  the single most Claude-like interaction; reuses `FileSearchRanker`.
- Predictive back; system bars transparent with correct light/dark icon contrast per screen
  (per-reader theme already exists for readers; extend app-wide via `enableEdgeToEdge` + `WindowInsets`).
- Gesture-nav friendly: bottom command pill clears gesture inset by ≥ 16 dp.

### 5.2 Adaptive (new — phones rotated, 7″/foldables/tablets)
- `WindowSizeClass` (Compose Material3 adaptive):
  - Compact: current single-pane + top chrome.
  - Medium: **list-detail two-pane** — left: Files/Recents list (360 dp, rail optional); right: reader. Top chrome collapses into each pane.
  - Expanded: nav rail (72 dp, icons+labels) + list pane (400 dp) + detail; persistent outline panel for DOCX/PDF/MD (document TOC already exists — promote to docked column).
- Foldables: posture-aware (table-top: controls split around hinge; book: list|reader split at hinge).
- `supportsRtl` actually verified: logical start/end everywhere; icon mirroring rules for back/forward.

### 5.3 Keyboard & focus model
- Full shortcut map (documented in Settings → About → Shortcuts): `Ctrl/Cmd+P` Quick Open · `Ctrl+F` in-viewer find · `Ctrl+S` save · `Ctrl+Z/Y` undo/redo · `Ctrl+A` select-all-text · `Ctrl+,` settings · `Alt+Left/Right` back/forward · `1-9` switch recents · `Ctrl+Shift+C` copy path · `Ctrl+Shift+D` properties.
- Tab/Shift-Tab cycles focus meaningfully in: file list (typeahead by first letter), dialogs, find bar, editor, properties sheet.
- Focus ring: 2 dp terracotta, offset 2 dp, visible only for keyboard focus (not after touch).

---

## 6. Component frontier (Phase 3c — one system, zero one-offs)

| Component | Spec |
|---|---|
| **ClaudeTopBar** | 56/64 dp; collapsing large editorial title (28 sp serif) on Home/Search/Settings; hairline divider on scroll ≥ 8 dp; nav action left (28 dp glyph), actions right; status-bar aware |
| **FileCard v2** | 12 dp radius; glyph well 44 dp (format tint + mono extension badge); filename sans 15/20 w500 maxLines 1; meta line 12/16 `onSurfaceVariant` (size · modified · location); star reveals on hover/touch-hold; `selectionFill` in batch mode; long-press drag-reorder not needed (recents are recency-sorted) |
| **ReaderCommandBar v2** | Single floating pill (48 dp): leading page/line/format context chip, then 3–5 contextual actions, overflow `⋯`; auto-hide on scroll (120 dp hide velocity), recenters on tap; scrim-free; shadow-free (hairline + glass) |
| **NoticeBanner v2** | Inline, 4 dp left accent bar, icon + 13 sp message + single action; variants: truncated (ochre), read-only (ochre), unsafe/slip (danger), recovered-text DOCX (terracotta) |
| **EmptyState** | Generative warm-paper illustration (spot: desk lamp / open folder / paper plane); serif 20 sp headline ("Nothing here yet"), sans 14 sp guidance, one primary CTA |
| **EditorToolbar (edit mode)** | Sticky above IME (safeDrawing ∪ nav union, existing fix); undo/redo, find, save; save button morphs terracotta→sage on successful atomic write ("Saved" pill, 1.6 s) |
| **PropertiesSheet** | Glass sheet; two-column meta grid; checksum chips (MD5/SHA-256) with tap-to-copy + monospace; path with copy; "Open with" intent chip |
| **OutlineSheet** | DOCX/PDF/MD TOC; active-section tracking; serif section labels; docked variant on expanded width |
| **Dialogs** | 24 dp radius glass; title serif 20 sp; ≤ 3 actions; destructive = danger-filled left action; password fields: show/hide toggle + imePadding (fixes BUG-005) |
| **Snackbar / UndoBar** | Glass, 1 action, 4 s (destructive 8 s), bottom-center above gesture inset; "Deleted 3 files — Undo" |
| **BottomSheet shell** | Unified grabber (32×4 dp), 16 dp top radius (sheet), glass fillStrong, scrim ≤ 40 % warm-black |
| **Loading** | Skeleton pages (PDF/DOCX/XLSX/MD — extend beyond Loop 14), `premiumLoadingPulse` for buttons; no spinners in content areas |

---

## 7. Viewer-specific direction (Phase 3d)

- **PDF reader:** toolbar slims to page chip + rotate/search/print/share/overflow (current). Add: reading-progress hairline at very top (1.5 dp, terracotta), per-page reset zoom double-tap, "Report this page renders wrong" overflow item (feeds support channel), print preview polish.
- **DOCX:** honest recovered-text banner is already good — echo that honesty pattern in PPTX (new). Fit-width baseline + pinch stays; add percentage chip tap-to-exact (already), line spacing toggle (1/1.15/1.5).
- **TXT / Markdown / RTF:** `readingBody` serif; measure 68ch; side "gutter whisper" for line numbers (editor only); fenced code blocks on `surfaceContainer` with mono 13 sp.
- **Code viewer:** Claude syntax theme — paper-tinted ink on `#F7F5EF`, strings in `#7A5A3A` terracotta-brown, comments warm grey italic; toggle Claude/Nord/Parchment in Appearance (3-wave plan already promised).
- **CSV/XLSX:** tabular numerals; row-header column sticky; zebra `selectionFill` at 40 % on hover/selection; frozen first column toggle; sheet tabs for XLSX (when multi-sheet lands, Tier 2).
- **Archives:** tree rows with type glyphs + size + compressed ratio; slip-flagged entries get danger tint + "can't extract" tooltip; breadcrumb path bar (glass).
- **Hex viewer:** offset gutter (mono, `onSurfaceVariant`), 16 bytes/row, ASCII column; **paged 64 KB windows** (AUD-003) with offset input + jump; kapton-amber on selection.
- **Media:** immersive theater; permanent caption (name · codec · duration) on first paint; controls fade; audio gets waveform-ish progress (Canvas-drawn bar, cheap) + album-art well (adaptive icon palette).
- **SQLite:** table picker as chips row; columns sticky; NULL in italic muted; blobs summarized chips.
- **Settings:** keep Loop 15's grouped-card structure; refine with new tokens, serif section eyebrows, live contrast warning chip, theme preview tile (three live mini-previews).
- **Splash:** paper background fades into Home; claw-mark sigil draws itself in 600 ms (vector `Path` animation, once), honors reduced-motion.
- **Launcher icon:** terracotta paper plane / claw sigil on warm paper; monochrome themed-icon support (adaptive + themed) for Android 13+.

---

## 8. Empty / loading / error / offline — the honesty matrix

| Surface | Empty | Loading | Error | Offline |
|---|---|---|---|---|
| Home | "Your desk is clear" + sample files CTA + illustration | skeleton recents grid | honest retry | impossible (no network) — show "All files live on this device" footnote |
| Search | "Say what you seek" hint chips (recents · types) | shimmer rows | "Index hiccup — retry" | n/a |
| Viewer | n/a | page/line skeleton | **specific** reason + Try again (never eternal spinner) — AUD-005/006/008 | n/a |
| Save flow | n/a | atomic progress pill | "Couldn't save — original untouched" + path copy | n/a |

---

## 9. Accessibility & inclusion (gate before Phase 3 sign-off)

- Touch targets ≥ 48 dp everywhere (action rows, chips, icon buttons).
- Contrast measured on the *actual composited glass-over-glow* backdrop (target ≥ 4.5:1 body / 3:1 large); `#A09D96` muted on dark gets bumped to `#B9B6AE`.
- Font scale 1.0→2.0 pass: no clipped labels; cards reflow (metadata wraps under filename).
- TalkBack: every icon labeled; reader pages announced ("Page 3 of 12"); tables announce header + cell; custom rotors ("Headings" in documents).
- Reduced motion honored; haptics optional (Settings toggle).
- Colorblind-safe glyph wells (icon + label, never color-only).
- RTL sweep on Home/AllFiles/Search/Preview.

---

## 10. Anti-patterns (do NOT ship)

- Blurred reading content · pure #000 backgrounds · gradients on documents · shadowy material cards · rainbow file-type badges · blue links · motion > 250 ms that blocks input · spinners in content surfaces · placeholder "coming soon" buttons · icon soup in the top bar (max 3 actions + overflow) · dark pure theme · unlabelled icon buttons.

---

## 11. Implementation order & gates

**Phase 3a — Foundations:** tokens (§4), glass-at-root NavHost policy, `Motion.kt`, bundled fonts (OFL: Source Serif 4 / Inter / JetBrains Mono), component v2 shells, deprecation sweep (`Icons.Default`→AutoMirrored).
**Gate:** app compiles; light/dark screenshot matrix; contrast audit pass.

**Phase 3b — Home, AllFiles, Search, navigation:** adaptive two-pane, Quick Open palette, shortcuts, predictive back, empty-state illustrations.
**Gate:** Macrobenchmark scroll ≥ 55 fps mid-tier; TalkBack sweep; font-scale 2.0 pass.

**Phase 3c — Reader chrome:** ReaderCommandBar v2, progress hairline, per-viewer spec (§7), details sheets unified.
**Gate:** Loop-14 behaviors intact (find bar, scrubber, rotation); focus-mode regression sweep on device.

**Phase 3d — Settings/sheets/dialogs/splash/icon:** tokens applied; live previews; splash animation; adaptive + themed icons.
**Gate:** release-candidate APK; SANITY_CHECKLIST green.

**Phase 3e — Hardening:** a11y, RTL, reduced-motion, tablet postures, keyboard map doc, FINAL contrast audit.
**Gate:** Play-store internal track; crash-free ≥ 99.9 % over 200 sessions.

---

## 12. Measurement plan

- Screenshot matrix (light/dark × 1.0/1.3/2.0 font × compact/medium/expanded) committed to `docs/ui-snapshots/` (CI artifact).
- Macrobenchmark: cold start, recents scroll, PDF open on 500-page doc, list→detail transition.
- Contrast: script samples token pairs over composited backdrops; fails build < 4.5 (body).
- TalkBack: scripted pass (Home → open → find → edit → save → back) recorded.
- Device lab: 1 low-RAM (3 GB), 1 mid, 1 tablet; `dumpsys meminfo` around 200-page PDF session.

## 13. Open decisions (owner: user)

1. **Bundled serif** — Source Serif 4 (OFL) proposed; alternative: keep system serif (cheaper, weaker voice).
2. **Application ID** — keep `com.aistudio.filesclaw.apxqzy` vs move to `app.filesclaw.files` — decide before any Play upload (Phase 5).
3. **Themed icon** — ship monochrome themed icon from day one?
4. **Quick Open scope** — files only vs files+actions (recommended: files+actions).
5. **Tablet default posture** — auto two-pane always vs opt-in in Settings (recommended: auto).
