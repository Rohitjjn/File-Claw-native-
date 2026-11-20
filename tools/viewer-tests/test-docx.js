/**
 * Executes the REAL app/src/main/assets/editor/docx_editor.html pipeline in jsdom,
 * mirroring exactly what the Android preview WebView does:
 *
 *   https://appassets.androidplatform.net/assets/editor/docx_editor.html
 *   onPageFinished -> loadDocx(null, night)      (bytes via fetch /docx-src/document.docx)
 *
 * HONESTY NOTE (what this covers / what it cannot): jsdom executes the real scripts
 * (jszip, docx-preview, the page's inline engine) and the real fetch path, so it
 * verifies the full JS pipeline: script wiring, loadDocx state machine, fetch
 * fallback, renderAsync DOM construction, bridge callbacks, zoom/search/dark-mode
 * glue. It has NO real layout engine: getBoundingClientRect/offsetWidth are STUBBED
 * below with plausible values so the painted-content failsafe can be exercised —
 * paint/geometry truth remains the device's (and CI emulator's) domain.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { JSDOM } from "jsdom";

const here = dirname(fileURLToPath(import.meta.url));
const repo = join(here, "..", "..");
const assets = join(repo, "app", "src", "main", "assets");

const html = readFileSync(join(assets, "editor", "docx_editor.html"), "utf8");
const jszip = readFileSync(join(assets, "editor", "jszip.min.js"), "utf8");
const docxPreview = readFileSync(
  join(assets, "editor", "docx-preview.min.js"),
  "utf8"
);
const docxFixture = readFileSync(join(here, "fixtures", "document.docx"));

// The page's inline script is the last <script> block in the file.
const inlineMatch = html.match(/<script>\s*([\s\S]*?)\s*<\/script>\s*<\/body>/);
if (!inlineMatch) {
  console.error("FAIL: could not extract the inline script from docx_editor.html");
  process.exit(1);
}
const inlineScript = inlineMatch[1];

const results = [];
const check = (name, ok, detail = "") => {
  results.push({ name, ok, detail });
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? ` — ${detail}` : ""}`);
};

async function runViewer(url, { printMode = false } = {}) {
  const dom = new JSDOM(html, {
    url,
    runScripts: "outside-only",
    pretendToBeVisual: true, // provides requestAnimationFrame
  });
  const { window } = dom;

  // --- Android bridge stub (records every call) ---
  const bridge = { loaded: [], errors: [], taps: 0, pages: [], zoom: [], searches: [] };
  window.Android = {
    getDocxBase64: () => null, // force the fetch path, like the device
    onDocumentLoaded: (totalPages) => bridge.loaded.push(totalPages),
    onDocumentError: (error) => bridge.errors.push(error),
    onDocumentTap: () => bridge.taps++,
    onDocumentDoubleTap: () => {},
    onZoomChanged: (z) => bridge.zoom.push(z),
    onPageChanged: (current, total) => bridge.pages.push([current, total]),
    onSearchResultCount: (count, index, truncated) =>
      bridge.searches.push([count, index, truncated]),
    onPrintReady: () => bridge.loaded.push("print-ready"),
    exitWithoutSaving: () => {},
  };

  // --- fetch polyfill: mirrors WebViewAssetLoader path handlers (jsdom has no
  //     fetch implementation, so a minimal Response shim is provided) ---
  // Cross-realm note: the page's JS runs inside jsdom's VM, whose ArrayBuffer
  // global differs from Node's — bytes must be copied into a jsdom-realm
  // ArrayBuffer or JSZip's instanceof checks reject them.
  const toRealmArrayBuffer = (nodeBuffer) => {
    const realmBuffer = new window.ArrayBuffer(nodeBuffer.byteLength);
    new window.Uint8Array(realmBuffer).set(nodeBuffer);
    return realmBuffer;
  };
  class FetchResponse {
    constructor(realmBuffer, status = 200) {
      this.status = status;
      this.ok = status >= 200 && status < 300;
      this._buffer = realmBuffer;
    }
    arrayBuffer() {
      return Promise.resolve(this._buffer);
    }
  }
  window.fetch = (input) => {
    const path = typeof input === "string" ? input : (input && input.url) || String(input);
    if (path.endsWith("/docx-src/document.docx")) {
      return Promise.resolve(new FetchResponse(toRealmArrayBuffer(docxFixture)));
    }
    return Promise.resolve(new FetchResponse(new window.ArrayBuffer(0), 404));
  };

  // --- jsdom realm shims (harness gaps, NOT app bugs) ---
  // JSZip's 'immediate' scheduler needs setImmediate, which jsdom lacks; without
  // it every zip-file .async() promise pends forever. Android WebView provides it.
  window.setImmediate = (fn, ...args) => setTimeout(() => fn(...args), 0);
  // jsdom does not implement scrollIntoView (used by the search highlight).
  window.Element.prototype.scrollIntoView = function () {};

  // --- geometry stubs (see HONESTY NOTE) ---
  const proto = window.Element.prototype;
  proto.getBoundingClientRect = function () {
    return { width: 794, height: 1000, top: 0, left: 0, right: 794, bottom: 1000, x: 0, y: 0 };
  };
  Object.defineProperty(proto, "offsetWidth", { get: () => 794, configurable: true });
  Object.defineProperty(proto, "offsetHeight", { get: () => 1000, configurable: true });

  // --- load the page's scripts in DOM order ---
  window.eval(jszip);
  window.eval(docxPreview);
  window.eval(inlineScript);

  // --- the Android-side init: exactly what onPageFinished evaluates ---
  window.eval(`loadDocx(null, ${!printMode});`);

  // wait (max 5s) until the bridge reports a settled outcome
  const deadline = Date.now() + 5000;
  while (
    Date.now() < deadline &&
    bridge.loaded.length === 0 &&
    bridge.errors.length === 0
  ) {
    await new Promise((r) => setTimeout(r, 50));
  }
  return { window, bridge, dom };
}

// ------------------------------------------------------------ test cases ---
let failures = 0;
const finish = () => {
  failures = results.filter((r) => !r.ok).length;
  console.log(failures === 0 ? "\nDOCX VIEWER TESTS: ALL PASS" : `\nDOCX VIEWER TESTS: ${failures} FAILURE(S)`);
  process.exit(failures === 0 ? 0 : 1);
};

// --- 1. Preview mode: the exact device pipeline ---
const preview = await runViewer(
  "https://appassets.androidplatform.net/assets/editor/docx_editor.html"
);
{
  const { window, bridge } = preview;
  check("no load error reported", bridge.errors.length === 0, JSON.stringify(bridge.errors));
  check("onDocumentLoaded fired", bridge.loaded.length === 1, `calls=${JSON.stringify(bridge.loaded)}`);
  const pages = window.document.querySelectorAll("#output .docx");
  check("rendered page sections exist", pages.length >= 1, `pages=${pages.length}`);
  const text = window.document.querySelector("#output").textContent || "";
  check(
    "document text present in DOM",
    text.includes("JHUNJHUNU") && text.includes("Stenography2026"),
    text.slice(0, 80)
  );

  // --- 2. Zoom engine (v1.3.2 transform+sizer): effective zoom = baseScale * currentZoom ---
  const container = window.document.getElementById("output");
  const sizer = window.document.getElementById("zoom-sizer");
  const effAt = (z) => window.eval(`(function(){ currentZoom = ${z}; return getEffectiveZoom(); })()`);
  const initialEff = window.eval("getEffectiveZoom()");
  window.eval("setZoom(2.0);");
  const effAt2 = effAt(2);
  check(
    "setZoom(2) applies scale(baseScale x 2)",
    container.style.transform === `scale(${effAt2})`,
    `transform=${container.style.transform} expected=scale(${effAt2})`
  );
  const sizerW = window.parseFloat(sizer.style.width) || 0;
  check(
    "zoom-sizer sized to the visual width",
    sizerW === Math.ceil(794 * effAt2),
    `width=${sizer.style.width} expected=${Math.ceil(794 * effAt2)}`
  );
  check(
    "onZoomChanged reported",
    bridge.zoom.length >= 1 && bridge.zoom[bridge.zoom.length - 1] === 2,
    JSON.stringify(bridge.zoom)
  );
  window.eval("setZoom(1.0);");
  check(
    "setZoom(1) returns to the fit-width baseline",
    container.style.transform === `scale(${initialEff})`,
    `transform=${container.style.transform} expected=scale(${initialEff})`
  );

  // --- 3. Idempotent load (Kotlin supervisor re-issues the init; must be a no-op) ---
  const before = bridge.loaded.length;
  window.eval("loadDocx(null, false);");
  await new Promise((r) => setTimeout(r, 200));
  check("re-issued loadDocx is a no-op", bridge.loaded.length === before, `calls=${bridge.loaded.length}`);

  // --- 4. In-document search ---
  window.eval(`searchWord("Stenography2026");`);
  await new Promise((r) => setTimeout(r, 100));
  check(
    "searchWord finds the marker and reports the count",
    bridge.searches.length >= 1 && bridge.searches[0][0] >= 1,
    JSON.stringify(bridge.searches)
  );
  const marks = window.document.querySelectorAll("mark.docx-highlight");
  check("highlight marks created", marks.length >= 1, `marks=${marks.length}`);

  // --- 5. Dark mode + line spacing toggles ---
  window.eval("setDarkMode(true);");
  check("dark mode class applied", window.document.body.classList.contains("dark"));
  window.eval("setLineSpacing('relaxed');");
  check("relaxed line-spacing class applied", window.document.body.classList.contains("relaxed"));
}

// --- 6. Print mode: the path that provably works on the device ---
const printRun = await runViewer(
  "https://appassets.androidplatform.net/assets/editor/docx_editor.html?print=1",
  { printMode: true }
);
{
  const { window, bridge } = printRun;
  check("print: no load error", bridge.errors.length === 0, JSON.stringify(bridge.errors));
  check(
    "print: onPrintReady fired (print path parity)",
    bridge.loaded.includes("print-ready"),
    JSON.stringify(bridge.loaded)
  );
  const pages = window.document.querySelectorAll("#output .docx");
  check("print: rendered page sections exist", pages.length >= 1, `pages=${pages.length}`);
}

finish();
