const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const viewer = read('app/src/main/assets/docx_viewer/viewer.js');
const tray = read('app/src/main/java/com/example/ui/preview/docx/DocxTrayAction.kt');
const nativeZoom = read('tools/docx-tests/docx-native-zoom.test.cjs');

test('DOCX pinch/zoom regressions are guarded by Chromium and Android edge-pan coverage', () => {
  assert.match(nativeZoom, /nativeWebViewZoomSupportsHorizontalPanToBothEdges/);
  assert.match(nativeZoom, /builtInZoomControls/);
  assert.match(viewer, /function applyInitialFit\(\)[\s\S]*?page\.style\.zoom\s*=\s*String\(fit\.fitScale\)/);
  assert.match(viewer, /pages\.forEach\(page => \{ page\.style\.zoom = String\(fit\.fitScale\); \}\)/);
  assert.doesNotMatch(viewer, /touchmove|function setZoom|transform\s*:\s*scale/);
  assert.doesNotMatch(viewer, /onDocumentDoubleTap|onZoomChanged/);
});

test('DOCX tray action model exposes exactly the requested stable sequence', () => {
  const labels = [...tray.matchAll(/^\s+[A-Z]+\("([^"]+)"\),?$/gm)].map(match => match[1]);
  assert.deepEqual(labels, ['Page', 'Rotate', 'Search', 'Print', 'Edit']);
});
