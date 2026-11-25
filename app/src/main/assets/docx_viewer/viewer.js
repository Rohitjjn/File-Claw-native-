(function () {
    'use strict';
    const MAX_BYTES = 25 * 1024 * 1024;
    const MAX_TEXT_CHARS = 2000000;
    const MAX_MATCHES = 500;
    const els = {};
    let renderId = 0;
    let matches = [];
    let currentSearchQuery = '';
    let totalMatches = 0;
    let matchesTruncated = false;
    let matchIndex = -1;
    let searchObserver;
    let readySent = false;
    let activePage = 0;
    let fitScale = 1;
    let fitLayoutViewportWidth = 0;
    let printMode = false;

    function send(method, payload) {
        try {
            const bridge = window.DocxBridge;
            if (bridge && typeof bridge[method] === 'function') {
                bridge[method](JSON.stringify(payload));
            }
        } catch (_) { /* A detached WebView cannot receive callbacks. */ }
    }

    function status(message, error) {
        els.status.hidden = !message;
        els.status.textContent = message || '';
        els.status.classList.toggle('error', !!error);
    }

    function visibleLayoutMetrics() {
        const rect = els.document.getBoundingClientRect();
        const viewport = els.scroller.getBoundingClientRect();
        const pages = Array.from(els.document.querySelectorAll('.docx, section.docx'));
        let visiblePageCount = 0;
        let top = Number.POSITIVE_INFINITY;
        let bottom = 0;
        let right = 0;
        pages.forEach(page => {
            const box = page.getBoundingClientRect();
            top = Math.min(top, box.top);
            bottom = Math.max(bottom, box.bottom);
            right = Math.max(right, box.right);
            if (box.width > 16 && box.height > 16 && box.right > viewport.left && box.left < viewport.right &&
                box.bottom > viewport.top && box.top < viewport.bottom) visiblePageCount++;
        });
        const contentHeight = Math.max(els.document.scrollHeight || 0, rect.height || 0,
            Number.isFinite(top) ? bottom - top : 0);
        const contentWidth = Math.max(els.document.scrollWidth || 0, rect.width || 0, right - rect.left);
        return { pageCount: pages.length, contentHeight, contentWidth, visiblePageCount };
    }

    function visibleContent() {
        const metrics = visibleLayoutMetrics();
        const text = (els.document.innerText || '').trim().length > 0;
        const image = Array.from(els.document.querySelectorAll('img')).some(i => i.complete && i.naturalWidth > 0);
        const table = !!els.document.querySelector('table');
        return window.DocxRenderReadiness.hasVisibleLayout(metrics) && (text || image || table);
    }

    function pageCount() {
        return els.document.querySelectorAll('.docx, section.docx').length;
    }

    function reportPage() {
        const pages = Array.from(els.document.querySelectorAll('.docx, section.docx'));
        if (!pages.length) return;
        const center = els.scroller.getBoundingClientRect().top + Math.min(els.scroller.clientHeight * .45, 240);
        let nearest = 0;
        let distance = Number.POSITIVE_INFINITY;
        pages.forEach((page, index) => {
            const rect = page.getBoundingClientRect();
            const candidate = Math.abs((rect.top + rect.bottom) / 2 - center);
            if (candidate < distance) { distance = candidate; nearest = index; }
        });
        if (nearest !== activePage) {
            activePage = nearest;
            send('onPageChanged', { page: nearest + 1, pages: pages.length });
        }
    }

    function reportReady(id) {
        if (id !== renderId || readySent) return;
        const metrics = visibleLayoutMetrics();
        if (!window.DocxRenderReadiness.hasVisibleLayout(metrics) || !visibleContent()) return;
        readySent = true;
        status('');
        send('onDocumentVisuallyRendered', {
            generation: id,
            pageCount: metrics.pageCount,
            contentHeight: Math.ceil(metrics.contentHeight),
            contentWidth: Math.ceil(metrics.contentWidth),
            visiblePageCount: metrics.visiblePageCount,
            fitScale
        });
        reportPage();
    }

    function clearHighlights() {
        for (const mark of els.document.querySelectorAll('mark.docx-highlight')) {
            const parent = mark.parentNode;
            if (!parent) continue;
            parent.replaceChild(document.createTextNode(mark.textContent || ''), mark);
            parent.normalize();
        }
        matches = [];
        currentSearchQuery = '';
        totalMatches = 0;
        matchesTruncated = false;
        matchIndex = -1;
    }

    function search(query) {
        clearHighlights();
        const needle = String(query || '').trim();
        if (!needle || needle.length > 256) {
            send('onSearchResults', { total: 0, current: 0, truncated: false });
            return;
        }
        currentSearchQuery = needle;
        const walker = document.createTreeWalker(els.document, NodeFilter.SHOW_TEXT, {
            acceptNode(node) {
                if (!node.nodeValue || !node.nodeValue.trim()) return NodeFilter.FILTER_REJECT;
                const parent = node.parentElement;
                if (!parent || /^(SCRIPT|STYLE|MARK)$/i.test(parent.tagName) || parent.closest('svg, math, noscript')) return NodeFilter.FILTER_REJECT;
                return NodeFilter.FILTER_ACCEPT;
            }
        });
        const nodes = [];
        let totalChars = 0;
        while (walker.nextNode() && nodes.length < 50000) {
            const node = walker.currentNode;
            totalChars += node.nodeValue.length;
            if (totalChars > MAX_TEXT_CHARS) break;
            nodes.push(node);
        }
        const found = window.DocxSearch.findMatches(nodes, needle, MAX_MATCHES);
        totalMatches = found.total;
        matchesTruncated = found.truncated;
        for (const entry of found.matches.slice().reverse()) {
            const group = [];
            for (const rangeInfo of entry.ranges.slice().reverse()) {
                const node = rangeInfo.node;
                if (!node.parentNode || rangeInfo.end > node.nodeValue.length) continue;
                const range = document.createRange();
                range.setStart(node, rangeInfo.start);
                range.setEnd(node, rangeInfo.end);
                const mark = document.createElement('mark');
                mark.className = 'docx-highlight';
                try {
                    range.surroundContents(mark);
                    group.unshift(mark);
                } catch (_) { /* Text-only ranges should be surroundable. */ }
            }
            entry.marks = group;
        }
        matches = found.matches.map(entry => entry.marks || []).filter(group => group.length > 0);
        matchIndex = matches.length ? 0 : -1;
        if (matches.length) activateMatch(0, false);
        send('onSearchResults', { total: found.total, current: matches.length ? 1 : 0, truncated: found.truncated });
    }

    function activateMatch(index, scroll) {
        matches.forEach(group => group.forEach(mark => mark.classList.remove('docx-highlight-active')));
        if (!matches.length) return;
        matchIndex = (index + matches.length) % matches.length;
        const group = matches[matchIndex];
        group.forEach(mark => mark.classList.add('docx-highlight-active'));
        const mark = group[0];
        if (scroll && mark) {
            const rect = mark.getBoundingClientRect();
            els.scroller.scrollTo({ top: Math.max(0, els.scroller.scrollTop + rect.top - 80), behavior: 'smooth' });
            if (rect.width * rect.height <= 0) mark.scrollIntoView({ block: 'center' });
        }
        send('onSearchResults', { total: totalMatches, current: matchIndex + 1, truncated: matchesTruncated });
    }

    function rebuildHighlights() {
        if (currentSearchQuery) search(currentSearchQuery);
    }

    function layoutViewportWidth() {
        return Math.max(1, document.documentElement.clientWidth || window.innerWidth || 1);
    }

    function applyViewportScale(targetScale, fit) {
        fitScale = fit.fitScale;
        fitLayoutViewportWidth = layoutViewportWidth();
        els.scroller.style.paddingLeft = `${fit.cssMargin}px`;
        els.scroller.style.paddingRight = `${fit.cssMargin}px`;
        els.viewport.content = `width=device-width, initial-scale=${targetScale}, minimum-scale=${fit.minScale}, maximum-scale=${fit.maxScale}, user-scalable=yes`;
    }

    function applyInitialFit() {
        if (printMode) return 1;
        const page = els.document.querySelector('.docx, section.docx');
        const fit = page && window.DocxViewportFit.calculate(
            layoutViewportWidth(),
            page.offsetWidth,
            12
        );
        if (!fit) throw new Error('FIT_TO_WIDTH_UNAVAILABLE');
        applyViewportScale(fit.fitScale, fit);
        return fit.fitScale;
    }

    function refitAfterViewportResize() {
        if (!readySent || printMode) return;
        const viewportWidth = layoutViewportWidth();
        if (Math.abs(viewportWidth - fitLayoutViewportWidth) < 1) return;
        const page = els.document.querySelector('.docx, section.docx');
        const fit = page && window.DocxViewportFit.calculate(viewportWidth, page.offsetWidth, 12);
        if (!fit) return;
        const currentScale = window.visualViewport ? window.visualViewport.scale : fitScale;
        const relativeZoom = currentScale / fitScale;
        const targetScale = Math.min(fit.maxScale, Math.max(fit.minScale, fit.fitScale * relativeZoom));
        applyViewportScale(targetScale, fit);
    }

    async function readDocumentStream(response, generation) {
        const total = Math.max(0, Number(response.headers.get('content-length') || 0));
        if (total > MAX_BYTES) throw new Error('RESOURCE_LIMIT_EXCEEDED');
        const reader = response.body && response.body.getReader ? response.body.getReader() : null;
        if (!reader) {
            const fallback = await response.arrayBuffer();
            if (fallback.byteLength > MAX_BYTES) throw new Error('RESOURCE_LIMIT_EXCEEDED');
            send('onProgress', { generation, loaded: fallback.byteLength, total: fallback.byteLength });
            return fallback;
        }

        const target = total > 0 ? new Uint8Array(total) : null;
        const chunks = [];
        let loaded = 0;
        while (true) {
            const next = await reader.read();
            if (next.done) break;
            const chunk = next.value;
            loaded += chunk.byteLength;
            if (loaded > MAX_BYTES || (target && loaded > target.byteLength)) {
                await reader.cancel();
                throw new Error(loaded > MAX_BYTES ? 'RESOURCE_LIMIT_EXCEEDED' : 'LOAD_LENGTH_MISMATCH');
            }
            if (target) target.set(chunk, loaded - chunk.byteLength);
            else chunks.push(chunk);
            status(total > 0 ? `Loading document… ${Math.floor(loaded * 100 / total)}%` : 'Loading document…');
            send('onProgress', { generation, loaded, total });
        }
        if (target) return target.slice(0, loaded).buffer;
        const combined = new Uint8Array(loaded);
        let offset = 0;
        chunks.forEach(chunk => { combined.set(chunk, offset); offset += chunk.byteLength; });
        return combined.buffer;
    }

    async function load(fileUrl, generation) {
        renderId = generation;
        readySent = false;
        els.document.replaceChildren();
        clearHighlights();
        status('Loading document…');
        if (!window.JSZip || !window.docx || typeof window.docx.renderAsync !== 'function') throw new Error('RENDERER_UNAVAILABLE');
        const response = await fetch(fileUrl);
        if (!response.ok) throw new Error('LOAD_FAILED_' + response.status);
        const buffer = await readDocumentStream(response, generation);
        if (buffer.byteLength > MAX_BYTES) throw new Error('RESOURCE_LIMIT_EXCEEDED');
        status('Preparing document…');
        try {
            const archive = await JSZip.loadAsync(buffer, { checkCRC32: false });
            const entries = Object.keys(archive.files);
            if (!archive.file('[Content_Types].xml') || !archive.file('word/document.xml')) throw new Error('INVALID_PACKAGE');
            if (entries.length > 20000) throw new Error('RESOURCE_LIMIT_EXCEEDED');
            const core = archive.file('word/document.xml');
            const xml = await core.async('string');
            if (xml.length > MAX_TEXT_CHARS * 8) throw new Error('RESOURCE_LIMIT_EXCEEDED');
        } catch (error) {
            if (/INVALID_PACKAGE|RESOURCE_LIMIT/.test(String(error && error.message))) throw error;
            throw new Error('INVALID_PACKAGE');
        }
        status('Rendering pages…');
        const options = {
            className: 'docx', inWrapper: true, ignoreWidth: false, ignoreHeight: false,
            ignoreFonts: false, breakPages: true, ignoreLastRenderedPageBreak: false,
            experimental: true, trimXmlDeclaration: true, useBase64URL: true,
            renderHeaders: true, renderFooters: true, renderFootnotes: true,
            renderEndnotes: true, renderComments: false, renderChanges: false,
            renderAltChunks: false, debug: false
        };
        try {
            await window.docx.renderAsync(buffer, els.document, undefined, options);
        } catch (_) {
            throw new Error('RENDER_FAILED');
        }
        if (generation !== renderId) return;
        status('Laying out pages…');
        await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
        if (!pageCount()) throw new Error('EMPTY_DOCUMENT_WITHOUT_PAGES');
        const targetScale = applyInitialFit();
        const deadline = Date.now() + 3500;
        let stableFitFrames = 0;
        const verify = () => {
            if (generation !== renderId || readySent) return;
            const currentScale = window.visualViewport ? window.visualViewport.scale : 1;
            if (!printMode && Math.abs(currentScale - targetScale) > 0.03) {
                stableFitFrames = 0;
            } else {
                stableFitFrames++;
            }
            if (stableFitFrames >= 2 && visibleContent()) reportReady(generation);
            else if (Date.now() < deadline) requestAnimationFrame(verify);
            else throwFailure(!printMode && Math.abs(currentScale - targetScale) > 0.03
                ? 'LAYOUT_FAILED_FIT_TO_WIDTH'
                : 'LAYOUT_FAILED_NOT_VISIBLE');
        };
        requestAnimationFrame(verify);
    }

    function throwFailure(code) {
        status('This document could not be displayed. Try opening it again or printing it.', true);
        send('onFailure', { generation: renderId, code, detail: code });
    }

    function runFromUrl() {
        const params = new URLSearchParams(location.search);
        printMode = params.get('print') === '1';
        if (params.get('dark') === '1') document.documentElement.classList.add('dark');
        const path = params.get('file');
        const generation = Number(params.get('generation') || 1);
        if (!path) { throwFailure('LOAD_FAILED_MISSING_FILE'); return; }
        load(path, generation).catch(error => throwFailure(String(error && error.message || error || 'LOAD_FAILED')));
    }

    window.DocxViewer = {
        search(query) { search(query); },
        next() { if (matches.length) activateMatch(matchIndex + 1, true); },
        previous() { if (matches.length) activateMatch(matchIndex - 1, true); },
        goToPage(index) {
            const pages = els.document.querySelectorAll('.docx, section.docx');
            const page = pages[Math.max(0, Math.min(pages.length - 1, Number(index) || 0))];
            if (page) els.scroller.scrollTo({ top: els.scroller.scrollTop + page.getBoundingClientRect().top - els.scroller.getBoundingClientRect().top, behavior: 'smooth' });
        },
        clearSearch() { clearHighlights(); send('onSearchResults', { total: 0, current: 0, truncated: false }); },
        setTheme(dark, paper, pagePaper, ink, edge) {
            const root = document.documentElement;
            root.classList.toggle('dark', !!dark);
            for (const [name, value] of [['--paper', paper], ['--page-paper', pagePaper], ['--ink', ink], ['--page-ink', ink], ['--edge', edge], ['--page-edge', edge]]) {
                if (typeof value === 'string' && /^#[0-9a-f]{6}$/i.test(value)) root.style.setProperty(name, value);
            }
        },
        getState() { return { ready: readySent, pages: pageCount(), zoom: window.visualViewport ? window.visualViewport.scale : 1, fitScale }; }
    };

    document.addEventListener('DOMContentLoaded', () => {
        els.scroller = document.getElementById('scroller');
        els.stage = document.getElementById('stage');
        els.document = document.getElementById('scaled-document');
        els.status = document.getElementById('status');
        els.viewport = document.getElementById('docx-viewport');
        configureScrollReporting();
        window.addEventListener('resize', refitAfterViewportResize);
        const onResize = () => {
            if (pageCount()) {
                if (readySent) rebuildHighlights();
                else reportReady(renderId);
            }
        };
        if (typeof ResizeObserver !== 'undefined') new ResizeObserver(onResize).observe(els.scroller);
        else window.addEventListener('resize', onResize);
        runFromUrl();
    });
})();
