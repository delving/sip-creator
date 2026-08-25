#!/usr/bin/env python3
"""
Corpus analyzer with T1/T2/T3 tier classification for Groovy mapping snippets.

Walks one or more corpus roots for `*mapping*.xml` files, extracts the text
of every `<groovy-code>` element (real mapping XML nests one line per
<string> child; a flat text form is also supported), classifies each
snippet's constructs into tiers (T1 = trivially portable, T2 = moderate,
T3 = exotic/needs bespoke handling), and writes an aggregate JSON report.

Usage:
    analyze_corpus.py ROOT [ROOT...] -o corpus-report.json

Adapted from zig-prototype/groovy_feature_extractor.py's snippet-extraction
approach (walk mapping XML, read element text), but replaces its feature
bucketing with a strict T1/T2/T3 tier classifier for the Lua mapping-engine
feasibility project.
"""

import argparse
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

# T1: string methods considered trivially portable to a target runtime.
T1_STRING_METHODS = [
    "replaceAll", "replace", "capitalize", "split", "trim", "toLowerCase",
    "toUpperCase", "toString", "toInteger", "indexOf", "contains",
    "startsWith", "endsWith", "sanitize", "sanitizeURI", "sanitizeURN",
    "isEmpty", "size", "join",
]

# Classification table kept as data so the spec can cite it directly.
# Each entry: name -> (tier, compiled regex). Regexes are matched with
# re.search against the raw snippet text.
def _build_construct_table():
    table = {}
    for method in T1_STRING_METHODS:
        table[f"method:{method}"] = (
            "T1",
            re.compile(r"\." + re.escape(method) + r"\s*\("),
        )
    table.update({
        "gstring_interpolation": ("T1", re.compile(r"\$\{[^}]*\}")),
        "property_access": ("T1", re.compile(r"\b\w+(?:_\.|\.)\w+")),
        "string_literal": ("T1", re.compile(r"""(['"]).*?\1""")),
        "ternary": ("T1", re.compile(r"\?[^:]*:")),
        "elvis": ("T1", re.compile(r"\?:")),
        "equality": ("T1", re.compile(r"(==|!=)")),

        "spread_operator": ("T2", re.compile(r"\*\.|\.\*\s")),
        "closure_literal": ("T2", re.compile(r"\{[^{}]*->")),
        "each_collect_findall": ("T2", re.compile(r"\.(each|collect|findAll)\b")),
        "regex_match": ("T2", re.compile(r"\.matches\s*\(|=~|\.matcher\b")),
        # Negative lookbehind excludes subscript indexing (`arr[0]`,
        # `input_.split(',')[1]`): a `[...]` immediately preceded by an
        # identifier char, `)`, or `]` is navigation (T1), not a list
        # literal (T2). A `[...]` at the start of an expression, or after
        # an operator/whitespace, is a genuine list literal.
        "list_literal": ("T2", re.compile(r"(?<![\w)\]])\[[^\[\]]*\]")),
        "discard_record": ("T2", re.compile(r"\bdiscardRecord\b")),
    })
    return table


CONSTRUCT_TABLE = _build_construct_table()

TIER_RANK = {"T1": 1, "T2": 2, "T3": 3}

# Method names already covered by a dedicated CONSTRUCT_TABLE rule (T1
# string methods, plus the T2 collection/regex methods). The "arbitrary
# method call -> T3" fallback below must exclude all of these, or it
# double-classifies snippets like `input.matches(...)` as T3 even though
# the dedicated `regex_match` rule already correctly tags them T2.
_DEDICATED_METHOD_NAMES = set(T1_STRING_METHODS) | {
    "each", "collect", "findAll", "matches", "matcher",
}


def classify_snippet(code):
    """Return (tier, sorted list of construct names found) for a snippet."""
    found = []
    tier = "T1"

    for name, (construct_tier, pattern) in CONSTRUCT_TABLE.items():
        if pattern.search(code):
            found.append(name)
            if TIER_RANK[construct_tier] > TIER_RANK[tier]:
                tier = construct_tier

    # T3 fallback signals: def/class/import/try-catch, or a method call
    # that isn't in the T1 list and isn't one of the recognized T2 methods.
    t3_signals = [
        (r"\bdef\b", "def_keyword"),
        (r"\bclass\b", "class_keyword"),
        (r"\bimport\b", "import_keyword"),
        (r"\btry\b", "try_catch"),
        (r"\bcatch\b", "try_catch"),
        (r"\bnew\s+[\w.]+\s*\(", "constructor_call"),
    ]
    for pattern, name in t3_signals:
        if re.search(pattern, code):
            found.append(name)
            tier = "T3"

    # Arbitrary method calls not already covered by a dedicated rule
    # (_DEDICATED_METHOD_NAMES = T1 string methods + T2 collection/regex
    # methods) are T3. Capitalized names are excluded: they are class
    # references (e.g. the `Date` in `new java.util.Date()`), already
    # covered by the constructor_call signal above, not real method calls.
    for method_name in re.findall(r"\.(\w+)\s*\(", code):
        if method_name in _DEDICATED_METHOD_NAMES:
            continue
        if method_name[:1].isupper():
            continue
        found.append(f"other_method:{method_name}")
        tier = "T3"

    return tier, sorted(set(found))


def find_mapping_files(roots):
    files = []
    for root in roots:
        root = Path(root)
        if root.is_file():
            if "mapping" in root.name and root.suffix == ".xml":
                files.append(root)
            continue
        for path in sorted(root.rglob("*mapping*.xml")):
            if path.is_file():
                files.append(path)
    return files


def _local_tag(elem):
    return elem.tag.split("}")[-1]  # strip namespace if present


def extract_snippets(xml_path):
    """Return list of <groovy-code> text snippets in an XML mapping file.

    Real mapping XML (see sip-core/src/test/resources/ese/mapping_ese.xml
    and NodeMapping.java's `List<String> groovyCode`) stores each line of a
    snippet as a separate <string> child of <groovy-code>:

        <groovy-code>
          <string>line one</string>
          <string>line two</string>
        </groovy-code>

    That nested form is the primary case: <string> children are joined with
    newlines into a single snippet. A flat `<groovy-code>text</groovy-code>`
    form (no <string> children) is also supported as a fallback, matching
    the shape used by the task's own minimal test fixtures.
    """
    snippets = []
    try:
        tree = ET.parse(xml_path)
    except ET.ParseError:
        return snippets
    root = tree.getroot()
    for elem in root.iter():
        if _local_tag(elem) != "groovy-code":
            continue
        string_children = [c for c in elem if _local_tag(c) == "string"]
        if string_children:
            snippet = "\n".join(c.text or "" for c in string_children).strip()
        elif elem.text and elem.text.strip():
            snippet = elem.text.strip()
        else:
            snippet = ""
        if snippet:
            snippets.append(snippet)
    return snippets


def analyze(roots):
    mapping_files = find_mapping_files(roots)

    per_mapping = []
    tier_counts = {"T1": 0, "T2": 0, "T3": 0}
    tier_constructs = {"T1": {}, "T2": {}, "T3": {}}
    total_snippets = 0

    for path in mapping_files:
        snippets = extract_snippets(path)
        if not snippets:
            continue
        max_tier = "T1"
        for code in snippets:
            tier, constructs = classify_snippet(code)
            total_snippets += 1
            tier_counts[tier] += 1
            for c in constructs:
                tier_constructs[tier][c] = tier_constructs[tier].get(c, 0) + 1
            if TIER_RANK[tier] > TIER_RANK[max_tier]:
                max_tier = tier
        per_mapping.append({
            "path": str(path),
            "max_tier": max_tier,
            "snippets": len(snippets),
        })

    total_tiered = sum(tier_counts.values()) or 1
    tiers = {}
    for tier in ("T1", "T2", "T3"):
        tiers[tier] = {
            "count": tier_counts[tier],
            "pct": round(100.0 * tier_counts[tier] / total_tiered, 2),
            "constructs": dict(sorted(tier_constructs[tier].items())),
        }

    report = {
        "summary": {
            "mappings": len(per_mapping),
            "snippets": total_snippets,
        },
        "tiers": tiers,
        "per_mapping": per_mapping,
    }
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Analyze a Groovy mapping-snippet corpus and classify "
                     "snippets into T1/T2/T3 tiers.")
    parser.add_argument("roots", nargs="+", help="Corpus root directories (or files) to scan.")
    parser.add_argument("-o", "--output", required=True, help="Path to write the JSON report to.")
    args = parser.parse_args(argv)

    report = analyze(args.roots)

    out_path = Path(args.output)
    out_path.write_text(json.dumps(report, indent=2))
    print(f"Analyzed {report['summary']['mappings']} mappings, "
          f"{report['summary']['snippets']} snippets -> {out_path}")


if __name__ == "__main__":
    main()
