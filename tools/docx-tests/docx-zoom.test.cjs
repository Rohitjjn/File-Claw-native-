const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const viewer = fs.readFileSync(path.join(root, 'app/src/main/assets/docx_viewer/viewer.js'), 'utf8');
const html = fs.readFileSync(path.join(root, 'app/src/main/assets/docx_viewer/index.html'), 'utf8');
const tray = fs.readFileSync(path.join(root, 'app/src/main/java/com/example/ui/preview/docx/DocxTrayAction.kt'), 'utf8');

test('fit width reserves the vertical scrollbar gutter to prevent resize feedback', () => {
  assert.match(html, /#scroller\s*\{[^}]*scrollbar-gutter:\s*stable\s*;/s);
  assert.match(viewer, /const availableWidth = Math\.max\(160, els\.scroller\.clientWidth - 16\)/);
  assert.match(viewer, /let userZoom = 1;/);
  assert.match(viewer, /Math\.max\(\.5, Math\.min\(5,/);
});

test('viewport resize preserves an anchor and does not rewrite an unchanged scale', () => {
  assert.match(viewer, /function captureAnchor\(anchorX, anchorY\)/);
  assert.match(viewer, /const anchor = captureAnchor\(\);\s*updateScale\(anchor\);/);
  assert.match(viewer, /if \(lastReportedZoom !== userZoom \|\| lastReportedFitScale !== fitScale \|\| lastReportedScale !== scale\)/);
  assert.match(viewer, /getState\(\) \{ return \{ ready: readySent, pages: pageCount\(\), zoom: userZoom \}; \}/);
});

test('single taps are deferred and double taps zoom without dispatching single tap', () => {
  assert.match(viewer, /pendingTapTimer = setTimeout\(\(\) => \{[\s\S]*?send\('onDocumentTap', \{\}\);[\s\S]*?\}, 320\);/);
  assert.match(viewer, /clearTimeout\(pendingTapTimer\);[\s\S]*?send\('onDocumentDoubleTap', \{ zoom: userZoom \}\);/);
});

test('DOCX tray action model exposes exactly the requested stable sequence', () => {
  const labels = [...tray.matchAll(/^\s+[A-Z]+\("([^"]+)"\),?$/gm)].map(match => match[1]);
  assert.deepEqual(labels, ['Page', 'Rotate', 'Search', 'Print', 'Edit']);
});
