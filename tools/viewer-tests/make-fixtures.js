/**
 * Generates deterministic test fixtures for the viewer JS tests (no network, no deps
 * beyond fflate for the DOCX zip container).
 *
 *  fixtures/document.docx — minimal but valid OOXML Word file with known marker text.
 *  fixtures/document.pdf  — minimal but valid 2-page PDF with distinct page text.
 *
 * The DOCX deliberately contains no images/styles beyond defaults: the point is to
 * exercise the docx-preview pipeline + our page glue, not Word feature coverage.
 */
import { zipSync, strToU8 } from "fflate";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const outDir = join(here, "fixtures");
mkdirSync(outDir, { recursive: true });

// ---------------------------------------------------------------- DOCX -----
const contentTypes = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>`;

const rels = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>`;

const para = (text) =>
  `<w:p><w:pPr><w:spacing w:after="200"/></w:pPr><w:r><w:t xml:space="preserve">${text}</w:t></w:r></w:p>`;

const documentXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:body>
${para("JHUNJHUNU STENO AND TYPING CLASSES EXAM REVIEW")}
${para("First marker paragraph: the quick brown fox jumps over the lazy dog.")}
${para("Second paragraph with searchable token Stenography2026 for the in-document search test.")}
${para("Third paragraph to give the page some body length for layout checks.")}
<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>
</w:body>
</w:document>`;

const docxBuffer = zipSync(
  {
    "[Content_Types].xml": strToU8(contentTypes),
    "_rels/.rels": strToU8(rels),
    "word/document.xml": strToU8(documentXml),
  },
  { level: 0 }
);
writeFileSync(join(outDir, "document.docx"), docxBuffer);

// ----------------------------------------------------------------- PDF -----
// Minimal, spec-valid 2-page PDF built with explicit byte offsets.
const objects = [];
const addObj = (body) => {
  objects.push(body);
  return objects.length; // 1-based object number
};

const catalogNum = addObj("PLACEHOLDER_CATALOG"); // 1
const pagesNum = addObj("PLACEHOLDER_PAGES"); // 2
const page1Num = addObj("PLACEHOLDER_PAGE1"); // 3
const page2Num = addObj("PLACEHOLDER_PAGE2"); // 4
const fontNum = addObj(
  "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
); // 5

const content1 = "BT /F1 24 Tf 72 700 Td (PDFTEST-PAGEONE-MARKER) Tj ET";
const content2 = "BT /F1 24 Tf 72 700 Td (PDFTEST-PAGETWO) Tj ET";
const content1Num = addObj(
  `<< /Length ${content1.length} >>\nstream\n${content1}\nendstream`
); // 6
const content2Num = addObj(
  `<< /Length ${content2.length} >>\nstream\n${content2}\nendstream`
); // 7

objects[catalogNum - 1] = `<< /Type /Catalog /Pages ${pagesNum} 0 R >>`;
objects[pagesNum - 1] =
  `<< /Type /Pages /Kids [${page1Num} 0 R ${page2Num} 0 R] /Count 2 >>`;
const pageBody = (contentNum) =>
  `<< /Type /Page /Parent ${pagesNum} 0 R /MediaBox [0 0 612 792] ` +
  `/Contents ${contentNum} 0 R /Resources << /Font << /F1 ${fontNum} 0 R >> >> >>`;
objects[page1Num - 1] = pageBody(content1Num);
objects[page2Num - 1] = pageBody(content2Num);

let pdf = "%PDF-1.4\n";
const offsets = [];
objects.forEach((body, i) => {
  offsets.push(pdf.length);
  pdf += `${i + 1} 0 obj\n${body}\nendobj\n`;
});
const xrefStart = pdf.length;
pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
for (const off of offsets) {
  pdf += `${String(off).padStart(10, "0")} 00000 n \n`;
}
pdf += `trailer\n<< /Size ${objects.length + 1} /Root ${catalogNum} 0 R >>\nstartxref\n${xrefStart}\n%%EOF\n`;

writeFileSync(join(outDir, "document.pdf"), Buffer.from(pdf, "latin1"));

console.log(
  `fixtures written: document.docx (${docxBuffer.length} bytes), document.pdf (${pdf.length} bytes)`
);
