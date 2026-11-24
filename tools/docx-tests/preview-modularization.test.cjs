const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const java = path.join(root, 'app/src/main/java/com/example/ui');
const coordinator = path.join(java, 'screens/FilePreviewScreen.kt');
const source = () => fs.readFileSync(coordinator, 'utf8');
const surface = () => fs.readFileSync(path.join(java, 'preview/viewers/PreviewScreenSurface.kt'), 'utf8');

const viewerFiles = [
  'ArchiveAndImagePreviews.kt',
  'CodeAndCsvPreviews.kt',
  'MediaAndBinaryPreviews.kt',
  'PdfPreview.kt',
  'TextDocumentPreviews.kt',
  'UniversalViewers.kt',
  'WebAndPptxPreviews.kt'
];

const chromeFiles = [
  'PreviewTopBar.kt',
  'PreviewFindBar.kt',
  'PreviewOverflowMenu.kt',
  'PreviewFloatingTray.kt',
  'PreviewDialogs.kt',
  'PreviewWindowEffects.kt',
  'PreviewFocusPill.kt',
  'PreviewScreenState.kt'
];

test('FilePreviewScreen stays within the 500-line coordinator budget', () => {
  const lineCount = source().split(/\r?\n/).length;
  assert.ok(lineCount <= 500, `FilePreviewScreen.kt is ${lineCount} lines; maximum is 500`);
});

test('preview viewer implementations live in ui.preview.viewers', () => {
  for (const name of viewerFiles) {
    const moved = path.join(java, 'preview/viewers', name);
    const old = path.join(java, 'screens', name);
    assert.ok(fs.existsSync(moved), `Missing extracted viewer: ${path.relative(root, moved)}`);
    assert.equal(fs.existsSync(old), false, `Viewer still lives in screens: ${path.relative(root, old)}`);
    assert.match(fs.readFileSync(moved, 'utf8'), /package com\.example\.ui\.preview\.viewers/);
  }
  const viewerSources = fs.readdirSync(path.join(java, 'preview/viewers')).filter(name => name.endsWith('.kt'));
  for (const name of viewerSources) {
    assert.doesNotMatch(fs.readFileSync(path.join(java, 'preview/viewers', name), 'utf8'), /import com\.example\.ui\.screens\.FilePreviewScreen/);
  }
});

test('shared preview chrome is extracted and composed through explicit components', () => {
  for (const name of chromeFiles) {
    const file = path.join(java, 'preview/chrome', name);
    assert.ok(fs.existsSync(file), `Missing preview chrome: ${path.relative(root, file)}`);
    assert.match(fs.readFileSync(file, 'utf8'), /package com\.example\.ui\.preview\.chrome/);
    assert.doesNotMatch(fs.readFileSync(file, 'utf8'), /MainViewModel/);
  }
  assert.match(surface(), /PreviewHeader\(/);
  assert.match(surface(), /PreviewFloatingTray\(/);
  assert.match(surface(), /PreviewContent\(/);
  assert.match(surface(), /PreviewDialogHost\(/);
  assert.match(surface(), /PreviewFocusPill\(/);
  assert.match(source(), /rememberPreviewScreenState\(/);
  assert.match(source(), /PreviewLargeTextSearchEffects\(/);
  assert.ok(fs.existsSync(path.join(java, 'preview/operations/PreviewTextOperations.kt')));
  assert.ok(fs.existsSync(path.join(java, 'preview/viewers/PreviewLargeTextSearchEffects.kt')));
  assert.doesNotMatch(surface(), /DropdownMenu\s*\(/);
});

test('screen coordinator dispatches every file state through PreviewContent', () => {
  assert.match(surface(), /PreviewContent\(/);
  assert.match(surface(), /PreviewContent\(/);
  assert.doesNotMatch(surface(), /when\s*\(val state = fileState\)/);
});

test('all file-state routes are implemented in the extracted PreviewContent component', () => {
  const contentPath = path.join(java, 'preview/viewers/PreviewContent.kt');
  assert.ok(fs.existsSync(contentPath), 'Missing route renderer PreviewContent.kt');
  const content = fs.readFileSync(contentPath, 'utf8');
  const routes = [
    'Loading', 'DocxPreviewLimitExceeded', 'Error', 'ArchivePasswordRequired', 'TextSuccess',
    'CsvSuccess', 'ZipSuccess', 'ImageSuccess', 'PdfSuccess', 'DocxSuccess', 'PptxSuccess',
    'MediaSuccess', 'BinarySuccess', 'SvgSuccess', 'SqliteSuccess', 'FontSuccess', 'Model3dSuccess', 'HtmlSuccess'
  ];
  for (const route of routes) assert.match(content, new RegExp(`FileContentState\\.${route}\\b`), `Missing ${route} route`);
  assert.doesNotMatch(content, /import com\\.example\\.ui\\.screens\\.FilePreviewScreen/);
  assert.doesNotMatch(content, /viewModel\\s*:/);
  const csv = fs.readFileSync(path.join(java, 'preview/viewers/CodeAndCsvPreviews.kt'), 'utf8');
  const pptx = fs.readFileSync(path.join(java, 'preview/viewers/WebAndPptxPreviews.kt'), 'utf8');
  assert.doesNotMatch(csv.slice(csv.indexOf('fun CsvPreview('), csv.indexOf('fun CsvPreview(') + 1000), /viewModel\\s*:/);
  assert.doesNotMatch(pptx.slice(pptx.indexOf('fun PptxPreviewWebView('), pptx.indexOf('fun PptxPreviewWebView(') + 700), /viewModel\\s*:/);
});
