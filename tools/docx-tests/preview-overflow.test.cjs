const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const previewRoot = path.join(root, 'app/src/main/java/com/example/ui/preview');
const menuPath = path.join(previewRoot, 'chrome/PreviewOverflowMenu.kt');
const menu = fs.readFileSync(menuPath, 'utf8');
const instrumentation = fs.readFileSync(path.join(root, 'app/src/androidTest/java/com/example/ui/preview/chrome/PreviewOverflowMenuInstrumentationTest.kt'), 'utf8');
const kotlinFiles = [];
function collect(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) collect(file);
    else if (entry.name.endsWith('.kt')) kotlinFiles.push(fs.readFileSync(file, 'utf8'));
  }
}
collect(previewRoot);
const popupImplementations = kotlinFiles.join('\n').match(/\bPopup\s*\(/g) || [];

test('preview has one rounded white overflow popup with soft shadow and no theme toggle', () => {
  assert.equal(popupImplementations.length, 1, 'Keep exactly one shared preview Popup implementation');
  assert.match(menu, /RoundedCornerShape\(16\.dp\)/);
  assert.match(menu, /shape = menuShape/);
  assert.match(menu, /color\s*=\s*Color\.White/);
  assert.match(menu, /shadowElevation\s*=\s*8\.dp/);
  assert.match(menu, /tint\s*=\s*Color\.Black/);
  assert.match(menu, /widthIn\(min = 180\.dp, max = 215\.dp\)/);
  assert.match(menu, /height\(42\.dp\)/);
  assert.match(menu, /size\(18\.dp\)/);
  assert.match(menu, /Spacer\(Modifier\.width\(12\.dp\)\)/);
  assert.match(menu, /fontSize\s*=\s*13\.5\.sp/);
  for (const label of ["Share file", "Go to Page", "Print / Export as PDF", "Properties", "Remove from history"]) {
    assert.ok(menu.includes(`MenuItemSpec("${label}"`), `DOCX menu action missing: ${label}`);
  }
  assert.match(menu, /PREVIEW_OVERFLOW_MENU_TEST_TAG/);
  assert.match(instrumentation, /docxOverflowMenuShowsFiveCompactActions/);
  assert.match(instrumentation, /assertEquals\(5, labeledActions\)/);
  assert.match(instrumentation, /assertHasClickAction\(\)/);
  assert.match(instrumentation, /widthDp in 180f\.\.215f/);
  assert.match(instrumentation, /heightDp in 40f\.\.44f/);
  assert.doesNotMatch(menu, /Dark Mode|Light Mode|docxNightModeOverride/);
});
