const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const surface = read('app/src/main/java/com/example/ui/preview/viewers/PreviewScreenSurface.kt');
const topBar = read('app/src/main/java/com/example/ui/preview/chrome/PreviewTopBar.kt');
const tray = read('app/src/main/java/com/example/ui/preview/chrome/PreviewFloatingTray.kt');
const pill = read('app/src/main/java/com/example/ui/preview/chrome/PreviewFocusPill.kt');

test('focus chrome overlays a stable reader viewport with synchronized easing', () => {
  assert.match(surface, /topBar = \{\}/);
  assert.match(surface, /Keep reader content at a stable size while the chrome enters or exits focus mode/);
  assert.match(surface, /Box\(Modifier\.fillMaxWidth\(\)\.align\(Alignment\.TopCenter\)\)\s*\{\s*previewHeader\(\)/);
  for (const source of [topBar, tray, pill]) {
    assert.match(source, /tween\(280, easing = FastOutSlowInEasing\)/);
    assert.doesNotMatch(source, /slideInVertically\s*\{ it \}|slideOutVertically\s*\{ it \}|fadeIn\(\)|fadeOut\(\)/);
  }
});
