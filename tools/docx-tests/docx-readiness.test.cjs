const test = require('node:test');
const assert = require('node:assert/strict');
const { hasVisibleLayout } = require('../../app/src/main/assets/docx_viewer/render-readiness.js');

test('visible render requires pages, positive measured content height and a visible page', () => {
  assert.equal(hasVisibleLayout({ pageCount: 2, contentHeight: 1800, contentWidth: 680, visiblePageCount: 1 }), true);
});

test('zero-height or offscreen documents cannot be reported visually rendered', () => {
  assert.equal(hasVisibleLayout({ pageCount: 2, contentHeight: 0, contentWidth: 680, visiblePageCount: 1 }), false);
  assert.equal(hasVisibleLayout({ pageCount: 2, contentHeight: 1800, contentWidth: 680, visiblePageCount: 0 }), false);
});

test('invalid dimensions and empty documents cannot pass readiness', () => {
  assert.equal(hasVisibleLayout({ pageCount: 0, contentHeight: 1800, contentWidth: 680, visiblePageCount: 1 }), false);
  assert.equal(hasVisibleLayout({ pageCount: 1, contentHeight: Number.NaN, contentWidth: 680, visiblePageCount: 1 }), false);
  assert.equal(hasVisibleLayout({ pageCount: 1, contentHeight: 1800, contentWidth: Infinity, visiblePageCount: 1 }), false);
});
