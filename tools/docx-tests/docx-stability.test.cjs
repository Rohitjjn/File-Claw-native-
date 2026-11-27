const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const host = read('app/src/main/java/com/example/ui/preview/docx/DocxWebViewHost.kt');
const screen = read('app/src/main/java/com/example/ui/preview/docx/DocxPreviewScreen.kt');
const viewer = read('app/src/main/assets/docx_viewer/viewer.js');
const html = read('app/src/main/assets/docx_viewer/index.html');
const instrumentation = read('app/src/androidTest/java/com/example/ui/preview/docx/DocxPreviewInstrumentationTest.kt');

test('DOCX uses one WebView and Chromium overview cannot compete with fit layout', () => {
  assert.equal((screen.match(/DocxWebViewHost\(/g) || []).length, 1);
  assert.equal((host.match(/AndroidView\(/g) || []).length, 1);
  assert.match(host, /useWideViewPort\s*=\s*true/);
  assert.match(host, /loadWithOverviewMode\s*=\s*false/);
  assert.match(html, /name="viewport" content="width=device-width, initial-scale=1\.0, minimum-scale=0\.5, maximum-scale=5\.0, user-scalable=yes"/);
  assert.match(html, /#scaled-document \.docx-wrapper \{[^}]*box-sizing: border-box; width: 100%; max-width: 100%/);
  const scaleCallbackStart = host.indexOf('onScaleChanged =');
  const scaleCallbackEnd = host.indexOf('onCreated(this)', scaleCallbackStart);
  assert.notEqual(scaleCallbackStart, -1);
  assert.doesNotMatch(host.slice(scaleCallbackStart, scaleCallbackEnd), /evaluateJavascript|postDelayed/);
  assert.doesNotMatch(viewer, /ResizeObserver|refitAfterViewportResize|addEventListener\(['"]resize/);
  assert.doesNotMatch(viewer, /els\.viewport\.content\s*=/);
  assert.match(viewer, /window\.visualViewport && window\.visualViewport\.width/);
  assert.match(viewer, /Math\.max\(\.\.\.pages\.map\(page => page\.offsetWidth\)\)/);
  assert.match(viewer, /els\.document\.style\.setProperty\('zoom', String\(fit\.fitScale\), 'important'\)/);
  assert.equal((viewer.match(/setProperty\('zoom'/g) || []).length, 1);
  assert.doesNotMatch(viewer, /setInterval|onScaleChanged|syncNativeScaleBounds/);
});

test('DOCX render is once per generation and leaves exactly one visible wrapper', () => {
  assert.match(viewer, /if\s*\(generation\s*===\s*lastLoadGeneration\)\s*return\s*loadPromise/);
  assert.match(viewer, /els\.document\.replaceChildren\(\)[\s\S]{0,300}renderAsync/);
  assert.match(viewer, /querySelectorAll\(['"]\.docx-wrapper['"]\)\.length/);
  assert.match(viewer, /wrappers !== 1/);
  assert.match(instrumentation, /document\.querySelectorAll\(['"]\.docx-wrapper['"]\)\.length/);
  assert.match(instrumentation, /two-second idle observation/);
  assert.match(instrumentation, /native scale and page bounds must remain stable/);
});
