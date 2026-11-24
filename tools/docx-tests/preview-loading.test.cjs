const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const viewModel = fs.readFileSync(path.join(root, 'app/src/main/java/com/example/viewmodel/MainViewModel.kt'), 'utf8');

test('pending preview state carries the selected file into routing and canvas selection', () => {
  assert.match(viewModel, /data class Loading\(val file: RecentFileEntity\? = null\) : FileContentState\(\)/);
  assert.match(viewModel, /is Loading -> file/);
  assert.match(viewModel, /val loading = FileContentState\.Loading\(fileEntity\)[\s\S]*?_currentFileState\.value = loading/);
  assert.match(viewModel, /NavigateToPreview\(loading\)/);
});

test('file navigation is emitted before recent-file IO work starts', () => {
  const openFile = viewModel.slice(viewModel.indexOf('fun openFile('), viewModel.indexOf('private fun buildContentState('));
  const navigation = openFile.indexOf('NavigateToPreview(loading)');
  const ioWork = openFile.indexOf('viewModelScope.launch(Dispatchers.IO)');
  assert.ok(navigation >= 0, 'missing immediate preview navigation event');
  assert.ok(ioWork > navigation, 'recent-file work must not precede preview navigation');
  assert.match(openFile, /repository\.insertRecentFile\(updatedEntity\)/);
});
