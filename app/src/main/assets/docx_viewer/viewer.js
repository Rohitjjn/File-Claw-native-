(function () {
    'use strict';
    const MAX_BYTES = 64 * 1024 * 1024;
    const MAX_TEXT_CHARS = 2000000;
    const MAX_MATCHES = 500;
    const els = {};
    let renderId = 0;
    let userZoom = 1;
    let fitScale = 1;
    let matches = [];
    let totalMatches = 0;
    let matchesTruncated = false;
    let matchIndex = -1;
    let searchObserver;
    let firstTouch = null;
    let lastTap = 0;
    let readySent = false;
    let activePage = 0;

    function send(method, payload) {
        try {
            const bridge = window.DocxBridge;
            if (bridge && typeof bridge[method] === 'function') bridge[method](JSON.stringify(payload));
        } catch (_) { /* A detached WebView cannot receive callbacks. */ }
    }

    function status(message, error) {
        els.status.hidden = !message;
        els.status.textContent = message || '';
        els.status.classList.toggle('error', !!error);
    }

    function visibleContent() {
        const rect = els.document.getBoundingClientRect();
        if (!els.document.querySelector('.docx, section.docx')) return false;
        const viewport = els.scroller.getBoundingClientRect();
        const visible = Array.from(els.document.querySelectorAll('.docx, section.docx')).some(page => {
            const box = page.getBoundingClientRect();
            return box.width > 16 && box.height > 16 && box.right > viewport.left && box.left < viewport.right &&
                box.bottom > viewport.top && box.top < viewport.bottom;
        });
        const text = (els.document.innerText || '').trim().length > 0;
        const image = Array.from(els.document.querySelectorAll('img')).some(i => i.complete && i.naturalWidth > 0);
        const table = !!els.document.querySelector('table');
        return rect.width > 16 && rect.height > 16 && visible && (text || image || table);
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
        const count = pageCount();
        if (!count || !visibleContent()) return;
        readySent = true;
        status('');
        send('onReady', { generation: id, pages: count, title: document.title });
        reportPage();
    }

    function updateScale() {
        const page = els.document.querySelector('.docx, section.docx');
        if (!page || !page.offsetWidth) return;
        const naturalWidth = page.offsetWidth;
        const availableWidth = Math.max(160, els.scroller.clientWidth - 16);
        fitScale = Math.min(1, Math.max(.05, availableWidth / naturalWidth));
        const scale = fitScale * userZoom;
        // CSS zoom participates in normal layout and scroll sizing. This avoids the
        // transform + synthetic spacer workaround that desynchronized touch/scroll.
        els.document.style.width = naturalWidth + 'px';
        els.document.style.zoom = String(scale);
        send('onZoomChanged', { zoom: userZoom, fitScale, effectiveScale: scale });
    }

    function setZoom(next, anchorX, anchorY) {
        const previous = fitScale * userZoom;
        userZoom = Math.max(.5, Math.min(5, Number(next) || 1));
        const scrollerRect = els.scroller.getBoundingClientRect();
        const x = Number.isFinite(anchorX) ? anchorX - scrollerRect.left : els.scroller.clientWidth / 2;
        const y = Number.isFinite(anchorY) ? anchorY - scrollerRect.top : els.scroller.clientHeight / 2;
        const before = els.document.getBoundingClientRect();
        const contentX = (x - (before.left - scrollerRect.left)) / Math.max(.01, previous);
        const contentY = (y - (before.top - scrollerRect.top)) / Math.max(.01, previous);
        updateScale();
        const current = fitScale * userZoom;
        const after = els.document.getBoundingClientRect();
        const documentLeft = after.left - scrollerRect.left + els.scroller.scrollLeft;
        const documentTop = after.top - scrollerRect.top + els.scroller.scrollTop;
        els.scroller.scrollLeft = Math.max(0, documentLeft + contentX * current - x);
        els.scroller.scrollTop = Math.max(0, documentTop + contentY * current - y);
        send('onZoomChanged', { zoom: userZoom, fitScale, effectiveScale: current });
    }

    function clearHighlights() {
        for (const mark of els.document.querySelectorAll('mark.docx-highlight')) {
            const parent = mark.parentNode;
            if (!parent) continue;
            parent.replaceChild(document.createTextNode(mark.textContent || ''), mark);
            parent.normalize();
        }
        matches = [];
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
        matches = found.matches;
        for (const entry of matches.slice().reverse()) {
            for (const rangeInfo of entry.ranges.slice().reverse()) {
                const node = rangeInfo.node;
                if (!node.parentNode || rangeInfo.end > node.nodeValue.length) continue;
                const range = document.createRange();
                range.setStart(node, rangeInfo.start);
                range.setEnd(node, rangeInfo.end);
                const mark = document.createElement('mark');
                mark.className = 'docx-highlight';
                try { range.surroundContents(mark); } catch (_) { /* Text-only ranges should be surroundable. */ }
            }
        }
        matches = Array.from(els.document.querySelectorAll('mark.docx-highlight'));
        matchIndex = matches.length ? 0 : -1;
        if (matches.length) activateMatch(0, false);
        send('onSearchResults', { total: found.total, current: matches.length ? 1 : 0, truncated: found.truncated });
    }

    function activateMatch(index, scroll) {
        matches.forEach(mark => mark.classList.remove('docx-highlight-active'));
        if (!matches.length) return;
        matchIndex = (index + matches.length) % matches.length;
        const mark = matches[matchIndex];
        mark.classList.add('docx-highlight-active');
        if (scroll) {
            const scale = fitScale * userZoom;
            const rect = mark.getBoundingClientRect();
            els.scroller.scrollTo({ top: Math.max(0, els.scroller.scrollTop + rect.top - 80), behavior: 'smooth' });
            if (rect.width * rect.height <= 0 || !scale) mark.scrollIntoView({ block: 'center' });
        }
        send('onSearchResults', { total: totalMatches, current: matchIndex + 1, truncated: matchesTruncated });
    }

    function rebuildHighlights() {
        const active = els.document.querySelector('mark.docx-highlight-active');
        const query = active ? active.textContent : '';
        if (query) search(query);
    }

    function configureGestures() {
        els.scroller.addEventListener('touchstart', event => {
            if (event.touches.length >= 2) {
                firstTouch = { distance: Math.hypot(event.touches[0].clientX - event.touches[1].clientX, event.touches[0].clientY - event.touches[1].clientY), zoom: userZoom, x: (event.touches[0].clientX + event.touches[1].clientX) / 2, y: (event.touches[0].clientY + event.touches[1].clientY) / 2 };
            } else if (event.touches.length === 1) firstTouch = { x: event.touches[0].clientX, y: event.touches[0].clientY };
        }, { passive: true });
        els.scroller.addEventListener('touchmove', event => {
            if (event.touches.length === 2 && firstTouch && 'distance' in firstTouch) {
                const distance = Math.hypot(event.touches[0].clientX - event.touches[1].clientX, event.touches[0].clientY - event.touches[1].clientY);
                setZoom(firstTouch.zoom * distance / Math.max(1, firstTouch.distance), firstTouch.x, firstTouch.y);
            }
        }, { passive: true });
        els.scroller.addEventListener('touchend', event => {
            if (event.changedTouches.length === 1 && firstTouch && !('distance' in firstTouch)) {
                const t = event.changedTouches[0];
                if (Math.hypot(t.clientX - firstTouch.x, t.clientY - firstTouch.y) < 18) {
                    const now = Date.now();
                    if (now - lastTap < 320) {
                        setZoom(userZoom > 1.4 ? 1 : 2, t.clientX, t.clientY);
                        send('onDocumentDoubleTap', {});
                    }
                    lastTap = now;
                }
            }
            firstTouch = null;
        }, { passive: true });
        els.scroller.addEventListener('scroll', () => requestAnimationFrame(reportPage), { passive: true });
        els.scroller.addEventListener('click', () => send('onDocumentTap', {}), { passive: true });
        els.scroller.addEventListener('wheel', event => {
            if (event.ctrlKey || event.metaKey) {
                event.preventDefault();
                setZoom(userZoom * (event.deltaY < 0 ? 1.1 : 1 / 1.1), event.clientX, event.clientY);
            }
        }, { passive: false });
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
        const length = Number(response.headers.get('content-length') || 0);
        if (length > MAX_BYTES) throw new Error('RESOURCE_LIMIT_EXCEEDED');
        const buffer = await response.arrayBuffer();
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
        updateScale();
        if (!pageCount()) throw new Error('EMPTY_DOCUMENT_WITHOUT_PAGES');
        const deadline = Date.now() + 3500;
        const verify = () => {
            if (generation !== renderId || readySent) return;
            updateScale();
            if (visibleContent()) reportReady(generation);
            else if (Date.now() < deadline) requestAnimationFrame(verify);
            else throwFailure('LAYOUT_FAILED_NOT_VISIBLE');
        };
        requestAnimationFrame(verify);
    }

    function throwFailure(code) {
        status('This document could not be displayed. Try opening it again or printing it.', true);
        send('onFailure', { generation: renderId, code, detail: code });
    }

    function runFromUrl() {
        const params = new URLSearchParams(location.search);
        if (params.get('dark') === '1') document.documentElement.classList.add('dark');
        const path = params.get('file');
        const generation = Number(params.get('generation') || 1);
        if (!path) { throwFailure('LOAD_FAILED_MISSING_FILE'); return; }
        load(path, generation).catch(error => throwFailure(String(error && error.message || error || 'LOAD_FAILED')));
    }

    window.DocxViewer = {
        setZoom(value) { setZoom(value); },
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
        getState() { return { ready: readySent, pages: pageCount(), zoom: userZoom }; }
    };

    document.addEventListener('DOMContentLoaded', () => {
        els.scroller = document.getElementById('scroller');
        els.stage = document.getElementById('stage');
        els.document = document.getElementById('scaled-document');
        els.status = document.getElementById('status');
        configureGestures();
        const onResize = () => {
            if (pageCount()) {
                updateScale();
                if (readySent) rebuildHighlights();
                else reportReady(renderId);
            }
        };
        if (typeof ResizeObserver !== 'undefined') new ResizeObserver(onResize).observe(els.scroller);
        else window.addEventListener('resize', onResize);
        runFromUrl();
    });
})();
