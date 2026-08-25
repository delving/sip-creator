#!/usr/bin/env python3
"""Measures the tier classifier's bare-function-call blind spot (Task 8).

`analyze_corpus.py`'s "arbitrary method call -> T3" fallback matches
`\\.(\\w+)\\s*\\(` -- a call *with a receiver*. A bare call to a
mapping-defined or rec-def-defined Groovy function, such as
`reverseNames("${a} ${b}")`, has no receiver, so no rule fires and the
snippet is left in T1. It is not T1: the function's own Groovy body has to
be ported too, and those bodies are routinely T2/T3 (`reverseNames` alone
uses assignment, array indexing and `.length`).

Task 8 hit this concretely: `coll-schraven`, selected into the golden set as
a T1-only mapping, is the one T1 golden the T1 converter cannot handle.

This script re-runs the canonical-corpus population (same helpers, same
dedup rule as `dedup_canonical.py`) and counts how many T1-classified
snippets contain such a call.

Usage:
    bare_call_gap.py ROOT [ROOT...] [-o bare-call-gap-report.json]
"""

import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from analyze_corpus import classify_snippet, extract_snippets, find_mapping_files
from dedup_canonical import dedup_to_canonical

# Groovy/Java control-flow keywords and the generated-code helpers that are
# always in scope (CodeGenerator.java:212-215). Neither is a user function.
KEYWORDS = {
    "if", "for", "while", "switch", "return", "catch", "else", "elseif", "do",
    "try", "assert", "new", "in", "instanceof", "not", "and", "or",
    "print", "println", "super", "this",
}
GENERATED_HELPERS = {"discard", "discardIf", "discardIfNot"}

# An identifier followed by "(" that is NOT preceded by a dot (which would
# make it a method call the existing classifier already sees) or by another
# identifier character.
BARE_CALL = re.compile(r"(?<![\w.$])([A-Za-z_]\w*)\s*\(")

TIER_RANK = {"T1": 1, "T2": 2, "T3": 3}


def bare_calls(code):
    return [
        name for name in BARE_CALL.findall(code)
        if name not in KEYWORDS and name not in GENERATED_HELPERS
    ]


def analyze(roots):
    files = dedup_to_canonical([p for p in find_mapping_files(roots) if extract_snippets(p)])
    t1_snippets = 0
    t1_snippets_with_bare_call = 0
    t1_only_mappings = 0
    t1_only_mappings_with_bare_call = 0
    names = {}

    for path in files:
        max_tier = "T1"
        mapping_has_bare_call = False
        for code in extract_snippets(path):
            tier, _ = classify_snippet(code)
            if TIER_RANK[tier] > TIER_RANK[max_tier]:
                max_tier = tier
            if tier != "T1":
                continue
            t1_snippets += 1
            found = bare_calls(code)
            if found:
                t1_snippets_with_bare_call += 1
                mapping_has_bare_call = True
                for name in found:
                    names[name] = names.get(name, 0) + 1
        if max_tier == "T1":
            t1_only_mappings += 1
            if mapping_has_bare_call:
                t1_only_mappings_with_bare_call += 1

    return {
        "canonical_mappings": len(files),
        "t1_snippets": t1_snippets,
        "t1_snippets_with_bare_call": t1_snippets_with_bare_call,
        "t1_snippets_with_bare_call_pct": round(100.0 * t1_snippets_with_bare_call / t1_snippets, 2)
        if t1_snippets else 0.0,
        "t1_only_mappings": t1_only_mappings,
        "t1_only_mappings_with_bare_call": t1_only_mappings_with_bare_call,
        "top_function_names": sorted(names.items(), key=lambda kv: -kv[1])[:20],
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("roots", nargs="+")
    parser.add_argument("-o", "--output")
    args = parser.parse_args(argv)

    report = analyze(args.roots)
    text = json.dumps(report, indent=2)
    if args.output:
        Path(args.output).write_text(text + "\n", encoding="utf-8")
    print(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
