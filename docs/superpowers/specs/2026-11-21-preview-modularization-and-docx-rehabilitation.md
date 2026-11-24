# Preview Modularization and DOCX Rehabilitation

**Status:** Approved by the user in Arena on 2026-11-21.

## Goal

Reduce `FilePreviewScreen.kt` to a 400–500-line routing/coordinator while preserving preview routes, and harden DOCX open/readiness, zoom/panning, tray/menu appearance, and renderer lifecycle with regression evidence.

## Approved architecture

- `MainViewModel` remains the owner of file/business state. `FilePreviewScreen` coordinates navigation, screen-level ephemeral state, and viewer selection; leaf components receive immutable state and explicit callbacks.
- Move preview-only viewer implementations from `ui/screens/` into `ui/preview/viewers/`, keeping related viewer implementations together and avoiding imports back into the coordinator.
- Put the shared preview top bar/find bar, overflow menu, floating tray, and dialog surfaces in focused `ui/preview/chrome/` components. Keep their interfaces independent of `FilePreviewScreen` and avoid cycles.
- Keep the offline DOCX renderer in `ui/preview/docx/` and `assets/docx_viewer/`. The DOCX screen opens at 100% user zoom (fit-to-width remains a separate layout scale); user zoom remains bounded to 50%–500%.
- Keep DOCX backgrounds white from the pending navigation state through Compose and WebView, regardless of the app's dark theme. Show the minimal white loading overlay with a black circular spinner and exact text `Loading document`. Dismiss it only after the renderer's double-`requestAnimationFrame` readiness gate and WebView visual-state callback; fade it out.
- Ensure oversized page layout scrolls from the leading edge and is reachable at all four edges. The action tray floats transparently over the document, not in an opaque footer. The white, elevated, rounded overflow menu uses black icons and approximately 13sp labels and has no Dark Mode item.
- Preserve the existing early navigation event and IO-dispatched recent/history work; regression coverage must prove that opening does not wait on persistence. Give the loading route enough file identity to select the DOCX white canvas before rendering succeeds.
- Update `docs/PROJECT_STATUS.md` and `docs/ISSUE_REGISTER.md` with code/test evidence, distinguish emulator evidence from physical-device reproduction, and leave owner-device root cause unconfirmed unless directly reproduced.

## Non-goals

- No changes to unrelated format behavior or the DOCX size safety limit.
- No network renderer, new rendering dependency, or document-editing feature.
- No claim of physical-device validation without physical-device evidence.

## Verification and delivery

Run `npm test --prefix tools/docx-tests`, Node syntax checks, `git diff --check`, and available source/line-budget checks locally. Java/JDK is unavailable in this sandbox; rely on `.github/workflows/docx-verify.yml` for JVM tests, lint, Detekt, compilation, API 34 WebView instrumentation, debug APK build, and uploaded APK artifact. Push only to `arena/ea756912-file-claw-native`, monitor the exact-SHA workflow run to completion, and report the run URL and direct APK artifact name/ID/size. Report any CI failure honestly and do not reuse older run evidence as proof for new code.
