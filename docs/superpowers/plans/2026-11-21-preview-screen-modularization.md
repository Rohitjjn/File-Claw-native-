# Preview Screen Modularization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce `FilePreviewScreen.kt` from 2,480 lines to 400–500 lines while keeping all existing file-preview routes and interactions.

**Architecture:** Keep `FilePreviewScreen` as the composition/router owner and `MainViewModel` as the business-state owner. Move preview-only viewers to `ui/preview/viewers/`; extract shared chrome/dialogs to `ui/preview/chrome/` with immutable inputs and callbacks so shared components do not import the coordinator or create dependency cycles.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, existing Node `node:test` contract suite, Android Gradle CI.

**Spec:** `docs/superpowers/specs/2026-11-21-preview-modularization-and-docx-rehabilitation.md`

## Global Constraints

- Preserve every current file-state route and existing callbacks/navigation semantics.
- `FilePreviewScreen.kt` must finish at 400–500 lines and remain the destination imported by `MainActivity`.
- ViewModel owns business state; Compose owns ephemeral visual state; leaves take props/callbacks, not ViewModels or coordinator references.
- Keep DOCX-only visual rules in the DOCX feature and shared chrome contracts free of format-specific ViewModel dependencies.
- Make only evidence-based claims; Java/Android checks run in CI because the sandbox has no JDK.

## Review Focus

- Any file-state branch loses or changes a viewer/action during extraction → cover all `FileContentState` route cases in the coordinator contract test and existing instrumented DOCX route checks.
- Relocated composables retain implicit same-package helpers or introduce a cycle → assert package/import boundaries in the Node structure test and compile in CI.
- Search, editor, and modal callbacks lose their state owner → preserve their input/output contract in the preview contract test and screen wiring checks; add focused state tests only for extracted pure state reducers.
- Tray and top bar become independent visually but lose focus-mode/inset/navigation semantics → exercise callback wiring and inspect API 34 instrumentation screenshots/screen tests.
- A passing test suite still leaves the coordinator over budget → enforce the 500-line maximum as a Node test and re-run after final formatting.

---

### Task 1: Add the preview modularization contract tests

**Files:**
- Create: `tools/docx-tests/preview-modularization.test.cjs`
- Modify: `tools/docx-tests/package.json` only if the standard `node --test` discovery pattern needs an explicit path.

**Interfaces:**
- Produces: Node contracts for the coordinator line budget, viewer package ownership, chrome component existence, and route-dispatch ownership.

- [ ] **Step 1: Write failing tests** named `FilePreviewScreen stays within the 500-line coordinator budget`, `preview viewers live in ui.preview.viewers`, and `shared chrome is composed through explicit preview components`. Assert the exact package/file paths and that coordinator owns the `FileContentState` route dispatch.
- [ ] **Step 2: Verify RED** with `node --test tools/docx-tests/preview-modularization.test.cjs`; expected failures are missing extracted files and the current 2,480-line budget, not test syntax errors.
- [ ] **Step 3: Keep the test runnable in the existing suite** via `npm test --prefix tools/docx-tests`.

### Task 2: Relocate existing viewer implementations without behavior changes

**Files:**
- Move: `ui/screens/ArchiveAndImagePreviews.kt` → `ui/preview/viewers/ArchiveAndImagePreviews.kt`
- Move: `ui/screens/CodeAndCsvPreviews.kt` → `ui/preview/viewers/CodeAndCsvPreviews.kt`
- Move: `ui/screens/MediaAndBinaryPreviews.kt` → `ui/preview/viewers/MediaAndBinaryPreviews.kt`
- Move: `ui/screens/PdfPreview.kt` → `ui/preview/viewers/PdfPreview.kt`
- Move: `ui/screens/TextDocumentPreviews.kt` → `ui/preview/viewers/TextDocumentPreviews.kt`
- Move: `ui/screens/UniversalViewers.kt` → `ui/preview/viewers/UniversalViewers.kt`
- Move: `ui/screens/WebAndPptxPreviews.kt` → `ui/preview/viewers/WebAndPptxPreviews.kt`
- Modify: imports in app Kotlin sources and cross-viewer calls among the moved files.
- Test: `tools/docx-tests/preview-modularization.test.cjs`.

**Interfaces:**
- Consumes: Current public viewer function signatures and `MainViewModel.FileContentState`.
- Produces: Same viewer entry points under `com.example.ui.preview.viewers`; no `ui.preview.viewers` source imports `com.example.ui.screens.FilePreviewScreen`.

- [ ] **Step 1: Extend the Node test** to assert the seven viewer files exist under the new directory, carry package `com.example.ui.preview.viewers`, and no old source copies remain.
- [ ] **Step 2: Verify RED** with `node --test tools/docx-tests/preview-modularization.test.cjs`; expected missing-path/package failures.
- [ ] **Step 3: Move the viewer files and adjust package/import references** without changing function bodies or APIs. Keep `PreviewContextUtils.kt` in `ui/screens` if it remains app-shell-specific; if viewers consume it, move that helper into `ui/preview/viewers` and update its package rather than adding a reverse import.
- [ ] **Step 4: Verify GREEN** with the Node test and `node --check` for the DOCX renderer files. Run `git diff --check`; Kotlin compile is verified by CI.

### Task 3: Extract preview data contracts and route rendering

**Files:**
- Create: `ui/preview/viewers/ViewerScrollRequest.kt`
- Create: `ui/preview/viewers/PreviewContent.kt` (orchestrates one file-state branch and receives explicit callbacks/immutable render inputs)
- Modify: `ui/screens/FilePreviewScreen.kt`
- Modify: `ui/preview/viewers/TextDocumentPreviews.kt`
- Test: `tools/docx-tests/preview-modularization.test.cjs`.

**Interfaces:**
- Produces: `ViewerScrollRequest(lineNumber: Int, headingIndex: Int? = null)` in `com.example.ui.preview.viewers`; a `PreviewContent` composable that takes the current `MainViewModel.FileContentState` plus the existing viewer callbacks/state slices and has no dependency on `FilePreviewScreen`.

- [ ] **Step 1: Add route coverage assertions** for `Loading`, `DocxPreviewLimitExceeded`, `Error`, `ArchivePasswordRequired`, `TextSuccess`, `CsvSuccess`, `ZipSuccess`, `ImageSuccess`, `PdfSuccess`, `DocxSuccess`, `PptxSuccess`, `MediaSuccess`, `BinarySuccess`, `SvgSuccess`, `SqliteSuccess`, `FontSuccess`, `Model3dSuccess`, and `HtmlSuccess`.
- [ ] **Step 2: Verify RED** with the Node test; expected missing `PreviewContent` and route cases outside a renderer component.
- [ ] **Step 3: Extract `ViewerScrollRequest` and the `when(fileState)` renderer** into `PreviewContent`; pass state/callbacks explicitly and leave navigation, effects, and screen-level state creation in `FilePreviewScreen`.
- [ ] **Step 4: Verify GREEN** with the Node suite and `git diff --check`; confirm existing viewer signatures remain unchanged.

### Task 4: Extract the top bar, find bar, and overflow menu

**Files:**
- Create: `ui/preview/chrome/PreviewTopBar.kt`
- Create: `ui/preview/chrome/PreviewFindBar.kt`
- Create: `ui/preview/chrome/PreviewOverflowMenu.kt`
- Modify: `ui/screens/FilePreviewScreen.kt`
- Test: `tools/docx-tests/preview-modularization.test.cjs`.

**Interfaces:**
- Produces: stateless composables that receive title, edit/search state, per-item visibility, theme-independent menu styling, and explicit actions. No component may accept `MainViewModel` or access coordinator locals.

- [ ] **Step 1: Add contract assertions** that these components exist in the chrome package and that the coordinator calls them with callbacks rather than containing the menu item tree.
- [ ] **Step 2: Verify RED** with the Node test; expected missing chrome file/component assertions.
- [ ] **Step 3: Extract top-bar/find-bar/menu UI** preserving share, edit actions, page jump, document outline, appearance, print/export, properties, and remove-history behavior. Preserve existing non-DOCX behavior; the DOCX-only menu requirement is covered in the DOCX plan.
- [ ] **Step 4: Verify GREEN** with the Node suite and diff checks; check all click callbacks still map to the existing screen/ViewModel action.

### Task 5: Extract the floating tray and dialog surfaces

**Files:**
- Create: `ui/preview/chrome/PreviewFloatingTray.kt`
- Create: `ui/preview/chrome/PreviewDialogs.kt`
- Create: `ui/preview/chrome/PreviewDialogState.kt` only if a typed snapshot is needed to avoid excessive positional parameters.
- Modify: `ui/screens/FilePreviewScreen.kt`
- Test: `tools/docx-tests/preview-modularization.test.cjs`.

**Interfaces:**
- Produces: a tray composable driven by format flags and actions, plus dialogs driven by explicit values/callbacks. Editor draft/undo stacks remain owned by the editor screen scope.

- [ ] **Step 1: Add contract assertions** that tray/dialog UI is outside the coordinator and has no `ViewModel` parameter.
- [ ] **Step 2: Verify RED** with the Node test; expected missing components.
- [ ] **Step 3: Extract tray and dialogs** including focus/inset handling and callbacks without changing behavior. The DOCX-specific transparent sibling placement/menu styling is implemented in the DOCX plan after this extraction.
- [ ] **Step 4: Verify GREEN** with Node tests, `git diff --check`, and a complete manual branch comparison of all dialog/toolbar/tray actions.

### Task 6: Consolidate coordinator-owned state and enforce the line budget

**Files:**
- Create: `ui/preview/chrome/PreviewScreenState.kt` for ephemeral state shared by multiple extracted siblings, only where the lowest common owner is the screen.
- Modify: `ui/screens/FilePreviewScreen.kt`
- Test: `tools/docx-tests/preview-modularization.test.cjs`.

**Interfaces:**
- Produces: public `FilePreviewScreen(viewModel, onBackClick, onNavigateToEditor, modifier)` at the existing import path; implementation is 400–500 lines and owns state/effects/navigation only.

- [ ] **Step 1: Make the Node budget assertion exact**: fail above 500 lines and report the observed count.
- [ ] **Step 2: Verify RED** on the current coordinator (expected failure at 2,480 lines; already known from the baseline).
- [ ] **Step 3: Remove only duplicate/local wiring made redundant by the extracted components**; keep the coordinator at 400–500 lines and avoid moving business rules out of `MainViewModel`.
- [ ] **Step 4: Run `npm test --prefix tools/docx-tests`, `node --check` for all handwritten DOCX JS, and `git diff --check`; verify every file-state branch is still represented.** Kotlin compile, unit, lint, Detekt, and Android instrumentation run in the workflow.
- [ ] **Step 5: Commit** as `refactor: modularize file preview screen` with only preview-modularization files/tests.
