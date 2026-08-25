#!/usr/bin/env python3
"""
Regex-dialect gap analysis for the Lua mapping-engine spike.

`replaceAll`, `split` and `matches` in the mapping language take
`java.util.regex` patterns (see docs/specs/mapping-language-core.md §4.3).
The candidate Lua host (gopher-lua) offers only Lua patterns, which are a
strictly weaker grammar: no alternation, no counted repetition `{n,m}`, no
inline flags or groups `(?...)`, no lazy/possessive quantifiers, no
backreferences. This script measures how much of the real corpus that
actually costs us.

Method
======
1. Reduce the corpus to canonical mappings (one representative per logical
   mapping) using `dedup_canonical.dedup_to_canonical` — the same population
   `corpus-report-canonical.json` is computed over.
2. Extract Groovy snippets with `analyze_corpus.extract_snippets` (no
   extraction logic is duplicated here).
3. Find calls of the form `.replaceAll(` / `.split(` / `.matches(` whose
   FIRST argument is a string literal, and take that literal's raw source
   text.
4. Undo Groovy string escaping (`\\\\` -> `\\` etc.), then scan the resulting
   regex with a small state machine that tracks character-class context, so
   that a `|` or `{` *inside* `[...]` is not mistaken for alternation or
   counted repetition.
5. Bucket each pattern by the *strongest* feature it uses. Buckets are
   ordered by severity: a pattern that needs a backreference is reported as
   `backreference` even if it also uses alternation.

Buckets
=======
- `literal`             : no regex metacharacters at all
- `lua_expressible`     : uses only features Lua patterns can express
                          (anchors, `.`, greedy `*`/`+`/`-`, `?`, classes)
- `alternation`         : uses `|` outside a character class
- `counted_repetition`  : uses `{n[,m]}` outside a character class
- `inline_group`        : uses `(?...)` — inline flags or lookaround
- `lazy_possessive`     : uses `*?`, `+?`, `??`, `*+`, `++`
- `backreference`       : uses `\\1`..`\\9`
- `java_escape`         : uses a Java-specific escape (`\\b`, `\\A`, `\\Q`, `\\p`, ...)
- `extraction_artifact` : the literal was truncated by the extractor at an
                          escaped quote (see Caveats) and is not a real pattern

`literal` + `lua_expressible` is the share reachable with Lua patterns alone;
everything else is the gap.

Caveats
=======
- Only string-literal FIRST arguments are counted. Slashy regex literals
  (`/.../`), `~/.../` pattern literals and patterns held in variables are
  not included, so the gap percentage is a floor over the literal
  population, not over all regex use.
- The literal extractor does not handle a quote escaped inside a literal of
  the same quote kind (`"[\\"']"`); such literals are truncated. Those are
  detected and reported separately as `extraction_artifact` rather than
  being silently misclassified.

Usage
=====
    regex_gap.py ROOT [ROOT...] -o regex-gap-report.json
"""

import argparse
import json
import re
from pathlib import Path

from analyze_corpus import find_mapping_files, extract_snippets
from dedup_canonical import dedup_to_canonical

#: Methods whose first argument is a java.util.regex pattern.
REGEX_METHODS = ("replaceAll", "split", "matches")

#: `.method('literal'` or `.method("literal"` — first argument only.
CALL_RE = re.compile(
    r"\.(" + "|".join(REGEX_METHODS) + r")\s*\(\s*(['\"])(.*?)\2"
)

#: Escapes that exist in java.util.regex but have no Lua-pattern equivalent.
JAVA_ONLY_ESCAPES = set("bBAZzGQEpPXRhHVv")

#: Bucket severity order — the first matching bucket wins.
SEVERITY = (
    "extraction_artifact",
    "backreference",
    "java_escape",
    "inline_group",
    "lazy_possessive",
    "counted_repetition",
    "alternation",
)

_GROOVY_ESCAPES = {
    "n": "\n", "t": "\t", "r": "\r", "b": "\b", "f": "\f",
    "\\": "\\", "'": "'", '"': '"', "$": "$",
}


def unescape_groovy(text):
    """Undo Groovy single/double-quoted string escaping.

    Unknown escapes (`\\d`, `\\s`, `\\[` ...) are passed through with the
    backslash intact, because in a Groovy string those *are* the regex's
    own escapes.
    """
    out = []
    i = 0
    while i < len(text):
        ch = text[i]
        if ch == "\\" and i + 1 < len(text):
            nxt = text[i + 1]
            out.append(_GROOVY_ESCAPES.get(nxt, "\\" + nxt))
            i += 2
        else:
            out.append(ch)
            i += 1
    return "".join(out)


def _is_truncated_literal(raw):
    """True when the extractor stopped at an escaped quote.

    `CALL_RE` is non-greedy and quote-unaware, so a literal such as
    `"[\\"']"` is captured as `[\\` — i.e. it ends with an odd number of
    trailing backslashes. A genuine pattern never does, because a trailing
    lone backslash is not a valid regex.
    """
    trailing = len(raw) - len(raw.rstrip("\\"))
    return trailing % 2 == 1


def scan_features(raw):
    """Return the set of regex feature names used by a raw Groovy literal."""
    if _is_truncated_literal(raw):
        return {"extraction_artifact"}

    pattern = unescape_groovy(raw)
    feats = set()
    in_class = False
    i = 0
    while i < len(pattern):
        ch = pattern[i]
        if ch == "\\":
            nxt = pattern[i + 1] if i + 1 < len(pattern) else ""
            if nxt.isdigit() and nxt != "0":
                feats.add("backreference")
            elif nxt in JAVA_ONLY_ESCAPES:
                feats.add("java_escape")
            i += 2
            continue
        if in_class:
            if ch == "]":
                in_class = False
            i += 1
            continue
        if ch == "[":
            in_class = True
            feats.add("meta")
            i += 1
            continue
        if ch == "|":
            feats.add("alternation")
        elif ch == "(":
            feats.add("inline_group" if pattern[i:i + 2] == "(?" else "meta")
        elif ch == "{" and re.match(r"\{\d", pattern[i:]):
            feats.add("counted_repetition")
        elif ch in "*+?" and i + 1 < len(pattern) and pattern[i + 1] in "?+":
            feats.add("lazy_possessive")
        elif ch in ".*+?^$":
            feats.add("meta")
        i += 1
    return feats


def bucket_for(raw):
    """Classify a raw literal into exactly one bucket."""
    feats = scan_features(raw)
    for name in SEVERITY:
        if name in feats:
            return name
    return "lua_expressible" if feats else "literal"


def collect_patterns(canonical_files):
    """Return {(method, raw_literal): occurrence_count} over the corpus."""
    counts = {}
    for path in canonical_files:
        for code in extract_snippets(path):
            for match in CALL_RE.finditer(code):
                key = (match.group(1), match.group(3))
                counts[key] = counts.get(key, 0) + 1
    return counts


def analyze_regex_gap(canonical_files):
    counts = collect_patterns(canonical_files)

    buckets = {}
    for (method, raw), n in counts.items():
        name = bucket_for(raw)
        entry = buckets.setdefault(name, {"occurrences": 0, "distinct": 0, "examples": []})
        entry["occurrences"] += n
        entry["distinct"] += 1
        entry["examples"].append({"method": method, "pattern": raw, "occurrences": n})

    total = sum(e["occurrences"] for e in buckets.values()) or 1
    for entry in buckets.values():
        entry["pct"] = round(100.0 * entry["occurrences"] / total, 2)
        entry["examples"] = sorted(
            entry["examples"], key=lambda e: -e["occurrences"]
        )[:10]

    reachable = sum(
        buckets.get(name, {}).get("occurrences", 0)
        for name in ("literal", "lua_expressible")
    )
    gap = total - reachable - buckets.get("extraction_artifact", {}).get("occurrences", 0)

    return {
        "summary": {
            "canonical_mappings": len(canonical_files),
            "distinct_patterns": len(counts),
            "total_occurrences": total,
            "lua_reachable_occurrences": reachable,
            "lua_reachable_pct": round(100.0 * reachable / total, 2),
            "gap_occurrences": gap,
            "gap_pct": round(100.0 * gap / total, 2),
        },
        "methods": sorted(REGEX_METHODS),
        "buckets": dict(sorted(buckets.items(), key=lambda kv: -kv[1]["occurrences"])),
    }


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Measure how much of the corpus's java.util.regex usage "
                    "is expressible with Lua patterns.")
    parser.add_argument("roots", nargs="+", help="Corpus root directories (or files) to scan.")
    parser.add_argument("-o", "--output", required=True, help="Path to write the JSON report to.")
    args = parser.parse_args(argv)

    snippet_bearing = [p for p in find_mapping_files(args.roots) if extract_snippets(p)]
    canonical_files = sorted(dedup_to_canonical(snippet_bearing))

    report = analyze_regex_gap(canonical_files)

    out_path = Path(args.output)
    out_path.write_text(json.dumps(report, indent=2))
    s = report["summary"]
    print(f"{s['canonical_mappings']} canonical mappings, "
          f"{s['distinct_patterns']} distinct patterns, "
          f"{s['total_occurrences']} occurrences: "
          f"{s['lua_reachable_pct']}% Lua-reachable, {s['gap_pct']}% gap -> {out_path}")


if __name__ == "__main__":
    main()
