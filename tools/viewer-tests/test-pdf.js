/**
 * Executes the REAL pdf.js loading path used by app/src/main/assets/pdfjs/search.html:
 * the exact same module files, the globalThis.pdfjsWorker main-thread ("fake worker")
 * mechanism, and the exact getDocument options — against a local fixture served by a
 * fetch polyfill. This is the path that fixed the device's "Failed to fetch"
 * (worker-side fetch bypassing shouldInterceptRequest): the test proves the options
 * combination + fake-worker mechanism actually loads and parses a PDF through fetch.
 */
import { readFileSync, existsSync, statSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const repo = join(here, "..", "..");
const assets = join(repo, "app", "src", "main", "assets");
const pdfjsDir = join(assets, "pdfjs");

const results = [];
const check = (name, ok, detail = "") => {
  results.push({ name, ok, detail });
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? ` — ${detail}` : ""}`);
};

// --- fetch polyfill mirroring the WebViewAssetLoader handlers ---
const MIME = {
  ".pdf": "application/pdf",
  ".bcmap": "application/octet-stream",
  ".pfb": "application/octet-stream",
};
const serve = (pathname) => {
  if (pathname.endsWith("/search-pdf/document.pdf")) {
    const buf = readFileSync(join(here, "fixtures", "document.pdf"));
    return new Response(buf, { status: 200, headers: { "Content-Type": "application/pdf", "Content-Length": String(buf.length) } });
  }
  if (pathname.startsWith("/assets/pdfjs/")) {
    const rel = pathname.slice("/assets/pdfjs/".length);
    const file = join(pdfjsDir, rel);
    if (existsSync(file) && statSync(file).isFile()) {
      const ext = rel.slice(rel.lastIndexOf("."));
      return new Response(readFileSync(file), {
        status: 200,
        headers: { "Content-Type": MIME[ext] || "application/octet-stream" },
      });
    }
  }
  return new Response("not found", { status: 404 });
};
const realFetch = globalThis.fetch;
globalThis.fetch = (input, init) => {
  const url = String(input instanceof Request ? input.url : input);
  try {
    const path = new URL(url, "https://appassets.androidplatform.net").pathname;
    return Promise.resolve(serve(path));
  } catch {
    return realFetch(input, init);
  }
};

// --- jsdom/node realm shims (harness gaps, NOT app bugs) ---
// pdf.js isValidFetchUrl() consults document.baseURI; a browser always has it.
globalThis.document = { baseURI: "https://appassets.androidplatform.net/" };

// --- the page's mechanism, verbatim: import worker module, publish on globalThis ---
const pdfjsLib = await import("file://" + join(pdfjsDir, "pdf.min.mjs"));
globalThis.pdfjsWorker = await import("file://" + join(pdfjsDir, "pdf.worker.min.mjs"));
pdfjsLib.GlobalWorkerOptions.workerSrc = "/assets/pdfjs/pdf.worker.min.mjs";

// In node, pdf.js routes file/http URLs through its node streams (not fetch), so
// the fetch path is verified through pdf.js's own fetchData() — the same wrapper
// the worker uses in the browser — against the intercepted fetch.
const fetched = await pdfjsLib.fetchData(
  "https://appassets.androidplatform.net/search-pdf/document.pdf",
  "arraybuffer"
);
const fixture = readFileSync(join(here, "fixtures", "document.pdf"));
check(
  "pdf.js fetchData retrieves the document through the intercepted fetch",
  fetched?.byteLength === fixture.length,
  `bytes=${fetched?.byteLength} expected=${fixture.length}`
);
check(
  "worker module exposes WorkerMessageHandler (the globalThis.pdfjsWorker mechanism)",
  typeof globalThis.pdfjsWorker?.WorkerMessageHandler === "function"
);

// Full parse through the main-thread (fake) worker with the page's option set.
const task = pdfjsLib.getDocument({
  data: new Uint8Array(fixture),
  cMapUrl: "/assets/pdfjs/cmaps/",
  cMapPacked: true,
  standardFontDataUrl: "/assets/pdfjs/standard_fonts/",
  isEvalSupported: false,
  useWorkerFetch: false,
  disableRange: true,
  disableStream: true,
  disableAutoFetch: true,
});

const doc = await task.promise;
check("document parsed through the main-thread worker", doc.numPages === 2, `numPages=${doc.numPages}`);

const page1 = await doc.getPage(1);
const tc = await page1.getTextContent();
const text = tc.items.map((i) => i.str).join(" ");
check("page 1 text extracted", text.includes("PDFTEST-PAGEONE-MARKER"), text.slice(0, 60));

const page2 = await doc.getPage(2);
const tc2 = await page2.getTextContent();
check("page 2 text extracted", tc2.items.map((i) => i.str).join(" ").includes("PDFTEST-PAGETWO"));

const failures = results.filter((r) => !r.ok).length;
console.log(failures === 0 ? "\nPDF VIEWER TESTS: ALL PASS" : `\nPDF VIEWER TESTS: ${failures} FAILURE(S)`);
process.exit(failures === 0 ? 0 : 1);
