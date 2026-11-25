const test = require('node:test');
const assert = require('node:assert/strict');
const fit = require('../../app/src/main/assets/docx_viewer/viewport-fit.js');

test('computes native fit scale and CSS gutter to preserve 12px visual margins', () => {
  const result = fit.calculate(360, 793.73, 12);
  assert.ok(Math.abs(result.fitScale - (360 - 24) / 793.73) < 0.0001);
  assert.ok(Math.abs(result.cssMargin * result.fitScale - 12) < 0.001);
  assert.ok(Math.abs(result.minScale - result.fitScale * 0.5) < 0.0001);
  assert.ok(Math.abs(result.maxScale - result.fitScale * 5) < 0.0001);
});

test('does not zoom documents that already fit the available width', () => {
  const result = fit.calculate(412, 300, 12);
  assert.equal(result.fitScale, 1);
  assert.equal(result.cssMargin, 12);
  assert.equal(result.minScale, 0.5);
  assert.equal(result.maxScale, 5);
});

test('rejects invalid viewport or page dimensions', () => {
  assert.equal(fit.calculate(0, 794), null);
  assert.equal(fit.calculate(360, 0), null);
  assert.equal(fit.calculate(Number.NaN, 794), null);
});
