(function (root, factory) {
    const api = factory();
    if (typeof module === 'object' && module.exports) module.exports = api;
    root.DocxRenderReadiness = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
    'use strict';

    function hasVisibleLayout(metrics) {
        const pageCount = Number(metrics && metrics.pageCount);
        const contentHeight = Number(metrics && metrics.contentHeight);
        const contentWidth = Number(metrics && metrics.contentWidth);
        const visiblePageCount = Number(metrics && metrics.visiblePageCount);
        return Number.isFinite(pageCount) && pageCount > 0 &&
            Number.isFinite(contentHeight) && contentHeight > 0 &&
            Number.isFinite(contentWidth) && contentWidth > 0 &&
            Number.isFinite(visiblePageCount) && visiblePageCount > 0;
    }

    return { hasVisibleLayout };
});
