#!/usr/bin/env python3
"""
Canonical-mapping dedup wrapper around analyze_corpus.py.

The real PocketMapper corpus stores, per dataset collection, one "live"
mapping file (`mapping_<prefix>.xml`) plus a timestamped snapshot copy
(`<YYYY-MM-DDTHHMMSS>__mapping_<prefix>.xml`) created every time the
mapping is edited and re-saved. Feeding all of those into analyze_corpus.py
counts the same logical mapping design many times over (once per historical
edit), which inflates file/snippet counts relative to "how many distinct
mappings are there and what tier are they."

This script groups mapping files by canonical identity (same directory,
same filename once any `<timestamp>__` snapshot prefix is stripped), picks
one representative file per group, and runs the same T1/T2/T3 analysis
(imported directly from analyze_corpus.py) over just the representatives.

Grouping happens over the same file population analyze_corpus.py actually
analyzes — files with at least one extractable `<groovy-code>` snippet
(3,578 of them for the full corpus; matches `corpus-report.json`'s
`per_mapping` count) — not the raw glob (which also includes unparsable or
empty-content files). This keeps the canonical count and the all-versions
count directly comparable: every group is guaranteed to have a
snippet-bearing representative, so no group is silently dropped for being
empty.

Representative choice per group: the bare (non-timestamped) file if one
exists (it's the file the tool currently considers "live"); otherwise the
lexicographically last snapshot filename, which is also the chronologically
latest since the timestamp format (`YYYY-MM-DDTHHMMSS`) sorts as a string
in date order.

Note: filenames with a non-timestamp `<hash>__` prefix (e.g. content-hashed
snapshots seen in some repo test fixtures, `AB12...__mapping_x.xml`) are
NOT snapshot copies of a shared canonical identity in this scheme — they
don't match the ISO-timestamp prefix pattern, so they pass through as their
own distinct canonical identity, same as analyze_corpus.py would already
treat them.

Usage:
    dedup_canonical.py ROOT [ROOT...] -o corpus-report-canonical.json
"""

import argparse
import json
import re
from pathlib import Path

from analyze_corpus import find_mapping_files, extract_snippets, analyze

_SNAPSHOT_PREFIX_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{6}__")


def canonical_name(filename):
    """Strip a leading '<YYYY-MM-DDTHHMMSS>__' snapshot prefix, if present."""
    return _SNAPSHOT_PREFIX_RE.sub("", filename)


def dedup_to_canonical(paths):
    """Group mapping file paths into canonical identities and return one
    representative Path per group (see module docstring for the rule)."""
    groups = {}
    for p in paths:
        key = (p.parent, canonical_name(p.name))
        groups.setdefault(key, []).append(p)

    representatives = []
    for (_parent, cname), group in groups.items():
        bare = [p for p in group if p.name == cname]
        if bare:
            representatives.append(bare[0])
        else:
            representatives.append(sorted(group, key=lambda p: p.name)[-1])
    return representatives


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Dedup a mapping corpus to one canonical file per "
                     "logical mapping (stripping timestamped snapshot "
                     "copies), then run the same T1/T2/T3 analysis as "
                     "analyze_corpus.py.")
    parser.add_argument("roots", nargs="+", help="Corpus root directories (or files) to scan.")
    parser.add_argument("-o", "--output", required=True, help="Path to write the JSON report to.")
    args = parser.parse_args(argv)

    all_files = find_mapping_files(args.roots)
    snippet_bearing = [p for p in all_files if extract_snippets(p)]
    canonical_files = sorted(dedup_to_canonical(snippet_bearing))

    report = analyze(canonical_files)

    out_path = Path(args.output)
    out_path.write_text(json.dumps(report, indent=2))
    print(f"{len(all_files)} files matched, {len(snippet_bearing)} contain at least one "
          f"groovy-code snippet, deduped to {len(canonical_files)} canonical mappings. "
          f"Analyzed {report['summary']['mappings']} mappings, "
          f"{report['summary']['snippets']} snippets -> {out_path}")


if __name__ == "__main__":
    main()
