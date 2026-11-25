(function (root, factory) {
    const api = factory();
    if (typeof module === 'object' && module.exports) module.exports = api;
    if (root) root.DocxViewportFit = api;
})(typeof window !== 'undefined' ? window : globalThis, function () {
    'use strict';

    function calculate(viewportWidth, pageWidth, visualMargin = 12) {
        if (![viewportWidth, pageWidth, visualMargin].every(Number.isFinite) ||
            viewportWidth <= 0 || pageWidth <= 0 || visualMargin < 0 || viewportWidth <= visualMargin * 2) {
            return null;
        }
        const fitScale = Math.min(1, (viewportWidth - visualMargin * 2) / pageWidth);
        if (!Number.isFinite(fitScale) || fitScale <= 0) return null;
        return {
            fitScale,
            minScale: fitScale * 0.5,
            maxScale: fitScale * 5,
            cssMargin: visualMargin / fitScale
        };
    }

    return { calculate };
});
