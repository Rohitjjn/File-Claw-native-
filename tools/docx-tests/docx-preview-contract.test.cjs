const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const screen = fs.readFileSync(path.join(root, 'app/src/main/java/com/example/ui/preview/viewers/PreviewScreenSurface.kt'), 'utf8');
const docxScreen = fs.readFileSync(path.join(root, 'app/src/main/java/com/example/ui/preview/docx/DocxPreviewScreen.kt'), 'utf8');
const menu = fs.readFileSync(path.join(root, 'app/src/main/java/com/example/ui/preview/chrome/PreviewTopBar.kt'), 'utf8');

test('DOCX action tray is a sibling floating overlay, not a Scaffold footer', () => {
  assert.doesNotMatch(screen, /bottomBar\s*=\s*\{/);
  assert.match(screen, /PreviewFloatingTray\(/);
  assert.match(screen, /Alignment\.BottomCenter/);
  assert.match(screen, /Color\.Transparent/);
});

test('DOCX has a white elevated overflow menu without a dark mode action', () => {
  assert.match(menu, /PreviewOverflowMenu\(/);
  assert.doesNotMatch(menu, /Dark Mode|Light Mode|docxNightModeOverride/);
  assert.match(docxScreen, /dark\s*=\s*isNightMode/);
});
