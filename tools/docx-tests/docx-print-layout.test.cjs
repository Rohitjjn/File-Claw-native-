const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const controller = read('app/src/main/java/com/example/ui/preview/docx/DocxPrintController.kt');
const policy = read('app/src/main/java/com/example/core/format/DocxPrintLayoutPolicy.kt');
const helper = read('app/src/main/java/com/example/utils/FilePrintHelper.kt');
const html = read('app/src/main/assets/docx_viewer/index.html');
const viewer = read('app/src/main/assets/docx_viewer/viewer.js');
const instrumentation = read('app/src/androidTest/java/com/example/ui/preview/docx/DocxPreviewInstrumentationTest.kt');

test('DOCX print uses an isolated unscaled renderer instead of the fitted preview WebView', () => {
  assert.match(controller, /FilePrintHelper\.printDocx\(context, file, title\)/);
  assert.doesNotMatch(controller, /FilePrintHelper\.printWebView/);
  assert.match(helper, /DocxViewer\.prepareForPrint\(\)/);
  assert.match(helper, /DocxPrintLayoutPolicy\.isReadyForPrint/);
  assert.match(policy, /abs\(scale - 1\.0\) > UNIT_SCALE_TOLERANCE/);
  assert.match(policy, /rootWidth <= 0\.0 \|\| pageWidth <= 0\.0/);
  assert.match(policy, /Equality with the screen viewport is not a print invariant/);
  assert.doesNotMatch(policy, /rootWidth < viewportWidth|pageWidth < viewportWidth/);
  assert.match(helper, /createPrintDocumentAdapter\(title\)/);
  assert.match(viewer, /prepareForPrint\(\)/);
  assert.match(instrumentation, /printPreparationResetsPreviewFitAndUsesFullPageWidth/);
  assert.match(instrumentation, /printPreparedWidth/);
  assert.match(instrumentation, /printScale/);
});

test('DOCX print CSS declares A4 geometry and removes mobile fit/card styling', () => {
  assert.match(html, /@page\s*\{\s*size:\s*A4;\s*margin:\s*12mm;\s*\}/);
  assert.match(html, /@media print[\s\S]*?#scaled-document\s*\{[^}]*width:\s*100%\s*!important;[^}]*max-width:\s*100%\s*!important;[^}]*transform:\s*none\s*!important/s);
  assert.match(html, /@media print[\s\S]*?#scaled-document \.docx, #scaled-document section\.docx\s*\{[^}]*width:\s*100%\s*!important;[^}]*max-width:\s*100%\s*!important;[^}]*box-shadow:\s*none\s*!important/s);
  assert.match(html, /\.print-prepared #scaled-document\s*\{[^}]*transform:\s*none\s*!important/s);
  assert.match(html, /\.print-prepared #scaled-document\s*\{[^}]*width:\s*100%\s*!important;[^}]*max-width:\s*100%\s*!important/s);
});
