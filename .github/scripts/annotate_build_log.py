#!/usr/bin/env python3
# ArchiveTune (2026) — GPL-3.0
"""Turn the interesting part of build logs into GitHub check-run annotations.

Usage: annotate_build_log.py LOG [LOG ...]
Emits up to 9 single-line `::error::` annotations for lines that look like compiler/linker
errors, plus one multi-line annotation with the tail of each log, so a failed build can be
diagnosed from the Checks API alone.
"""
import os
import re
import sys

PATTERNS = re.compile(
    r"(^e: |error:|\berror\b: |What went wrong|FAILURE:|BUILD FAILED|\*\* BUILD FAILED|"
    r"Could not resolve|Could not find|Unresolved reference|ld: |Undefined symbol|"
    r"No such module|Exception in thread|Caused by:)",
)


def escape(text: str) -> str:
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def main() -> None:
    emitted = 0
    for path in sys.argv[1:]:
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8", errors="replace") as handle:
            lines = handle.read().splitlines()
        if not lines:
            continue
        name = os.path.basename(path)
        hits = [line.strip() for line in lines if PATTERNS.search(line)]
        seen = set()
        for hit in hits:
            if emitted >= 9:
                break
            key = hit[:300]
            if key in seen:
                continue
            seen.add(key)
            print(f"::error title={name}::{escape(key)}")
            emitted += 1
        tail = "\n".join(lines[-80:])
        print(f"::error title={name} (tail)::{escape(tail[-60000:])}")


if __name__ == "__main__":
    main()
