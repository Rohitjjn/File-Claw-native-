const test = require('node:test');
const assert = require('node:assert/strict');
const { findMatches } = require('../../app/src/main/assets/docx_viewer/search.js');

function textNode(value) {
  return { nodeValue: value };
}

test('matches a query spanning adjacent styled text nodes', () => {
  const result = findMatches([textNode('The File '), textNode('Claw reader')], 'file claw', 5000);
  assert.equal(result.total, 1);
  assert.equal(result.truncated, false);
  assert.deepEqual(result.matches[0].ranges.map(({ start, end }) => [start, end]), [[4, 9], [0, 4]]);
});

test('keeps whitespace and Unicode text offsets across runs', () => {
  const nodes = [textNode('नमस्ते '), textNode('जयपुर\nराजस्थान')];
  const hindi = findMatches(nodes, 'जयपुर\nराजस्थान', 5000);
  assert.equal(hindi.total, 1);
  assert.deepEqual(hindi.matches[0].ranges.map(({ start, end }) => [start, end]), [[0, nodes[1].nodeValue.length]]);
  assert.equal(findMatches([textNode('left '), textNode('right')], 'left right', 10).total, 1);
});

test('escapes regular-expression syntax and matches case-insensitively', () => {
  const result = findMatches([textNode('A [DOCX] + b.docx')], '[docx]', 10);
  assert.equal(result.total, 1);
  assert.equal(result.matches[0].ranges[0].start, 2);
});

test('counts every occurrence but retains a bounded, navigable first 500', () => {
  const result = findMatches([textNode('x '.repeat(501))], 'x', 500);
  assert.equal(result.total, 501);
  assert.equal(result.matches.length, 500);
  assert.equal(result.truncated, true);
});

test('empty queries and zero-cap requests do not create result ranges', () => {
  assert.deepEqual(findMatches([textNode('hello')], '  ', 5000), {
    total: 0,
    truncated: false,
    matches: []
  });
  const capped = findMatches([textNode('a a')], 'a', 0);
  assert.equal(capped.total, 2);
  assert.equal(capped.matches.length, 0);
  assert.equal(capped.truncated, true);
});
