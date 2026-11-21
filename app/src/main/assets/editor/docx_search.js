(function (root, factory) {
    const api = factory();
    if (typeof module === 'object' && module.exports) module.exports = api;
    root.DocxSearch = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
    'use strict';

    function findMatches(textNodes, rawQuery, maxMatches) {
        const query = String(rawQuery || '').trim();
        const limit = Math.max(0, Number(maxMatches) || 0);
        if (!query) return { total: 0, truncated: false, matches: [] };

        const spans = [];
        let text = '';
        for (const node of textNodes) {
            const value = typeof node === 'string' ? node : String(node.nodeValue || '');
            const start = text.length;
            text += value;
            spans.push({ node, start, end: text.length });
        }

        const escaped = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        const matcher = new RegExp(escaped, 'giu');
        const matches = [];
        let total = 0;
        let spanIndex = 0;

        for (const found of text.matchAll(matcher)) {
            total++;
            if (matches.length >= limit) continue;
            const start = found.index;
            const end = start + found[0].length;
            while (spanIndex < spans.length && spans[spanIndex].end <= start) spanIndex++;

            const ranges = [];
            for (let i = spanIndex; i < spans.length && spans[i].start < end; i++) {
                const span = spans[i];
                const from = Math.max(start, span.start);
                const to = Math.min(end, span.end);
                if (to > from && typeof span.node !== 'string') {
                    ranges.push({ node: span.node, start: from - span.start, end: to - span.start });
                }
            }
            matches.push({ ranges });
        }

        return { total, truncated: total > matches.length, matches };
    }

    return { findMatches };
});
