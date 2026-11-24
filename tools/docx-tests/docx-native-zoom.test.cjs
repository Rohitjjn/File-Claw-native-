const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const host = read('app/src/main/java/com/example/ui/preview/docx/DocxWebViewHost.kt');
const client = read('app/src/main/java/com/example/ui/preview/docx/DocxWebViewClient.kt');
const screen = read('app/src/main/java/com/example/ui/preview/docx/DocxPreviewScreen.kt');
const html = read('app/src/main/assets/docx_viewer/index.html');
const viewer = read('app/src/main/assets/docx_viewer/viewer.js');
const instrumentation = read('app/src/androidTest/java/com/example/ui/preview/docx/DocxPreviewInstrumentationTest.kt');
const gestures = read('app/src/main/java/com/example/ui/preview/docx/DocxWebViewGestures.kt');
const surface = read('app/src/main/java/com/example/ui/preview/viewers/PreviewScreenSurface.kt');

test('DOCX pinch and pan are delegated to native Chromium WebView zoom', () => {
  assert.match(host, /setSupportZoom\(true\)/);
  assert.match(host, /builtInZoomControls\s*=\s*true/);
  assert.match(host, /displayZoomControls\s*=\s*false/);
  assert.match(host, /useWideViewPort\s*=\s*true/);
  assert.match(host, /loadWithOverviewMode\s*=\s*false/);
  assert.match(host, /modifier = modifier\.fillMaxSize\(\)\.clipToBounds\(\)/);
  assert.match(client, /override fun onScaleChanged\(/);
  assert.match(host, /fromWebViewScale\(page\.scale, page\.resources\.displayMetrics\.density\)/);
  assert.match(host, /fromWebViewScale\(scale, web\.resources\.displayMetrics\.density\)/);
  assert.match(host, /installConfirmedSingleTapListener\(onTap\)/);
  assert.match(gestures, /onSingleTapConfirmed/);
  assert.match(gestures, /false \/\/ Preserve pinch, double-tap zoom, and one-finger pan in Chromium/);
  assert.doesNotMatch(gestures, /onDoubleTap|detectTransformGestures/);
  assert.match(html, /name="viewport" content="width=device-width, initial-scale=1\.0, minimum-scale=0\.5, maximum-scale=5\.0, user-scalable=yes"/);
  assert.doesNotMatch(viewer, /touchmove|style\.zoom|transform\s*:\s*scale|function setZoom|function updateScale|ctrlKey/);
  assert.doesNotMatch(html, /\bzoom\s*:/);
  assert.doesNotMatch(screen, /detectTransformGestures|pointerInput/);
  assert.match(surface, /usesReaderGestures = .*docx/);
  assert.match(surface, /if \(!usesReaderGestures\)/);
});

test('Android regression fixture checks native zoom defaults, bounds and both horizontal edges', () => {
  assert.match(instrumentation, /nativeWebViewZoomSupportsHorizontalPanToBothEdges/);
  assert.match(instrumentation, /builtInZoomControls/);
  assert.match(instrumentation, /scrollLeft/);
  assert.match(instrumentation, /far-left/);
  assert.match(instrumentation, /far-right/);
  assert.match(instrumentation, /dispatchPinch\(web, SystemClock\.uptimeMillis\(\)/);
  assert.match(instrumentation, /zoom >= \.49f && zoom <= \.52f/);
  assert.match(instrumentation, /zoom <= 5\.02f && zoom >= 4\.9f/);
  assert.match(instrumentation, /5\.0/);
});
