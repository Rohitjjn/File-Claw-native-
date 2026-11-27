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

test('fills the available width for documents narrower than the viewport', () => {
  const result = fit.calculate(412, 300, 12);
  assert.ok(Math.abs(result.fitScale * 300 + 24 - 412) < 0.01);
  assert.equal(result.cssMargin, 12 / result.fitScale);
  assert.equal(result.minScale, result.fitScale * 0.5);
  assert.equal(result.maxScale, result.fitScale * 5);
});

test('fits the measured document width at compact, phone, and landscape viewport sizes', () => {
  const naturalPageWidth = 793.73;
  for (const viewportWidth of [320, 360, 412, 480, 800, 915]) {
    const result = fit.calculate(viewportWidth, naturalPageWidth, 12);
    assert.ok(result, `fit should be available at ${viewportWidth}px`);
    assert.ok(Math.abs(result.fitScale * naturalPageWidth + 24 - viewportWidth) < 0.01,
      `${viewportWidth}px should fit the natural page with 12px gutters: ${JSON.stringify(result)}`);
    assert.ok(result.fitScale > 0 && result.fitScale <= 5);
  }
});

test('rejects invalid viewport or page dimensions', () => {
  assert.equal(fit.calculate(0, 794), null);
  assert.equal(fit.calculate(360, 0), null);
  assert.equal(fit.calculate(Number.NaN, 794), null);
});
