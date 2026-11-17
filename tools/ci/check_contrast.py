#!/usr/bin/env python3
"""WCAG 2.x contrast audit for the Files Claw semantic token tier (Phase 3a gate).

Thresholds: body text >= 4.5 (AA), large-scale/bold & non-text accents >= 3.0 (AA Large / UI).
Tokens are read from Color.kt directly so the audit cannot drift from the source of truth.
Prints one line per pair (::notice for CI logs) and exits 1 if any gated pair fails.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COLOR_KT = ROOT / "app/src/main/java/com/example/ui/theme/Color.kt"

def load_tokens():
    tokens = {}
    for name, hexv in re.findall(r"val (Claude[A-Za-z0-9]+)\s*=\s*Color\(0x(?:FF)?([0-9A-Fa-f]{6})\)", COLOR_KT.read_text()):
        tokens[name] = "#" + hexv.upper()
    return tokens

def lum(hexs):
    h = hexs.lstrip("#")
    c = [int(h[i:i + 2], 16) / 255 for i in range(0, 6, 2)]
    c = [x / 12.92 if x <= 0.03928 else ((x + 0.055) / 1.055) ** 2.4 for x in c]
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]

def ratio(a, b):
    la, lb = lum(a), lum(b)
    lo, hi = min(la, lb), max(la, lb)
    return (hi + 0.05) / (lo + 0.05)

# (fg token, bg token, min ratio, role)
PAIRS = [
    ("ClaudeCharcoal", "ClaudeAlabaster", 4.5, "light body text on paper"),
    ("ClaudeOnSurfaceVariantLight", "ClaudeAlabaster", 4.5, "light secondary text on paper"),
    ("ClaudeOnSurfaceVariantLight", "ClaudeSurfaceContainerLight", 4.5, "light secondary text on container"),
    ("ClaudeOnSurfaceVariantLight", "ClaudeSelectionFillLight", 4.5, "light secondary text on selection fill"),
    ("ClaudeDangerLight", "ClaudeAlabaster", 4.5, "light danger text on paper"),
    ("ClaudeWarningLight", "ClaudeAlabaster", 4.5, "light warning text on paper"),
    ("ClaudeWarningLight", "ClaudeSurfaceContainerLight", 4.5, "light warning text on container"),
    ("ClaudeSuccessLight", "ClaudeAlabaster", 4.5, "light success text on paper"),
    ("ClaudeSoftTomato", "ClaudeAlabaster", 4.5, "light filled-button coral / link body on paper"),
    ("ClaudeAlabaster", "ClaudeSoftTomato", 4.5, "white label on filled button (light)"),
    ("ClaudeTerracotta", "ClaudeAlabaster", 3.0, "light terracotta accent, links large/non-text"),
    ("ClaudeDarkText", "ClaudeOnyx", 4.5, "dark body text on onyx"),
    ("ClaudeOnSurfaceVariantDark", "ClaudeOnyx", 4.5, "dark secondary text on onyx"),
    ("ClaudeOnSurfaceVariantDark", "ClaudeSurfaceContainerDark", 4.5, "dark secondary text on container"),
    ("ClaudeOnSurfaceVariantDark", "ClaudeSelectionFillDark", 4.5, "dark secondary text on selection fill"),
    ("ClaudeDangerDark", "ClaudeOnyx", 4.5, "dark danger text on onyx"),
    ("ClaudeWarningDark", "ClaudeOnyx", 4.5, "dark warning text on onyx"),
    ("ClaudeSuccessDark", "ClaudeOnyx", 4.5, "dark success text on onyx"),
    ("ClaudeOrangeAccent", "ClaudeOnyx", 4.5, "dark amber link/secondary on onyx"),
    ("ClaudeAlabaster", "ClaudeSoftTomato", 4.5, "white label on filled button (dark)"),
    ("ClaudeTerracotta", "ClaudeOnyx", 3.0, "dark terracotta accent, non-text"),
]

def main():
    tokens = load_tokens()
    failures = []
    for fg, bg, minimum, role in PAIRS:
        fg_hex, bg_hex = tokens.get(fg), tokens.get(bg)
        if not fg_hex or not bg_hex:
            print(f"::error title=Contrast::missing token for pair {fg}/{bg}")
            failures.append((fg, bg))
            continue
        r = ratio(fg_hex, bg_hex)
        ok = r >= minimum
        level = "notice" if ok else "error"
        print(f"::{level} title=Contrast::{r:.2f} (min {minimum}) {role} [{fg} on {bg}]")
        if not ok:
            failures.append((fg, bg))
    if failures:
        print(f"contrast audit FAILED: {len(failures)} pair(s) below AA threshold")
        return 1
    print(f"contrast audit OK: {len(PAIRS)} pairs meet AA thresholds")
    return 0

if __name__ == "__main__":
    sys.exit(main())
