/** Runs every viewer test; exits nonzero if any fails. */
import { spawnSync } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const tests = ["test-docx.js", "test-pdf.js"];
let failed = 0;
for (const t of tests) {
  console.log(`\n===== ${t} =====`);
  const r = spawnSync("node", [join(here, t)], { stdio: "inherit" });
  if (r.status !== 0) failed++;
}
console.log(failed === 0 ? "\nALL VIEWER TESTS PASS" : `\n${failed} TEST FILE(S) FAILED`);
process.exit(failed === 0 ? 0 : 1);
