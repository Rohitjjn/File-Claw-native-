# Third-party notices

## Bundled in `app/src/main/assets/`
| Library | Version | License | Verified from |
|---|---|---|---|
| JSZip | 3.10.1 | MIT **or** GPLv3 (dual) | header of `docx/jszip.min.js` |
| docx-preview | not stated in file | upstream: Apache-2.0 (**verify**) | no license text shipped in the bundle |
| pptx-preview | not stated in file | upstream: MIT (**verify**) | no license text shipped in the bundle |
| x-spreadsheet | not stated in file | upstream: MIT (**verify**) | no license text shipped in the bundle |

## Gradle dependencies (see `gradle/libs.versions.toml`)
AndroidX / Jetpack Compose / Room / WorkManager / Navigation (Apache-2.0) · Coil (Apache-2.0) · telephoto (Apache-2.0) ·
Apache Commons Compress (Apache-2.0) · XZ for Java (public domain / 0BSD) · zip4j (Apache-2.0) · fastexcel (Apache-2.0) ·
JetBrains markdown (Apache-2.0) · AndroidPdfViewer / pdfium-android (Apache-2.0).
Verify each license against the resolved versions before a store release.

Removed in 1.1.0: pdfbox-android, kotlin-csv, fastexcel (writer), kotlinx-serialization.
