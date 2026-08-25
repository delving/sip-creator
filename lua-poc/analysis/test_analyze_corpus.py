import json, subprocess, sys, pathlib
FIX = pathlib.Path(__file__).parent / "fixtures"

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from analyze_corpus import classify_snippet, extract_snippets  # noqa: E402


def run(*roots):
    out = FIX.parent / "out.json"
    subprocess.run([sys.executable, str(FIX.parent / "analyze_corpus.py"),
                    *map(str, roots), "-o", str(out)], check=True)
    return json.loads(out.read_text())

def test_t1_only_mapping_classified_t1():
    r = run(FIX)
    by_path = {m["path"]: m for m in r["per_mapping"]}
    assert by_path[str(FIX / "t1-only-mapping.xml")]["max_tier"] == "T1"

def test_t2_spread_classified_t2():
    r = run(FIX)
    by_path = {m["path"]: m for m in r["per_mapping"]}
    assert by_path[str(FIX / "t2-spread-mapping.xml")]["max_tier"] == "T2"

def test_t3_def_classified_t3():
    r = run(FIX)
    by_path = {m["path"]: m for m in r["per_mapping"]}
    assert by_path[str(FIX / "t3-exotic-mapping.xml")]["max_tier"] == "T3"

def test_summary_counts():
    r = run(FIX)
    # 4 fixture mappings: t1-only (2), t2-spread (2), t3-exotic (2),
    # t1-multiline (1 snippet spread across 2 <string> lines) = 7 snippets.
    assert r["summary"]["mappings"] == 4
    assert r["summary"]["snippets"] == 7


# --- real-schema nested <string> extraction (regression for the bug where
# extract_snippets only read <groovy-code> flat text and found 0 snippets
# on every real mapping, which uses one <string> child per code line) ---

def test_nested_string_children_extracted_and_classified():
    r = run(FIX)
    by_path = {m["path"]: m for m in r["per_mapping"]}
    entry = by_path[str(FIX / "t1-multiline-mapping.xml")]
    # The two <string> lines belong to one <groovy-code> element, so they
    # must be joined into a single snippet, not counted as two snippets.
    assert entry["snippets"] == 1
    assert entry["max_tier"] == "T1"

def test_extract_snippets_joins_nested_string_children_with_newline():
    snippets = extract_snippets(FIX / "t1-multiline-mapping.xml")
    assert snippets == [
        "\"${input_.sanitizeURI()}\"\ninput_.replaceAll('^0+','')"
    ]

def test_extract_snippets_still_supports_flat_text_form(tmp_path):
    flat = tmp_path / "flat-mapping.xml"
    flat.write_text(
        "<mapping><node-mapping><groovy-code>"
        "input_.trim()"
        "</groovy-code></node-mapping></mapping>"
    )
    assert extract_snippets(flat) == ["input_.trim()"]


# --- matches()/matcher() must classify T2, not be double-classified T3 by
# the generic "unknown method call" fallback ---

def test_matches_call_classified_t2():
    tier, constructs = classify_snippet('input.matches("^[0-9]+$")')
    assert tier == "T2"
    assert "regex_match" in constructs

def test_matcher_call_classified_t2():
    tier, constructs = classify_snippet("pattern.matcher(input_)")
    assert tier == "T2"
    assert "regex_match" in constructs


# --- list literal vs. subscript indexing ---

def test_subscript_indexing_is_t1():
    tier, constructs = classify_snippet("input_[0]")
    assert tier == "T1"
    assert "list_literal" not in constructs

def test_split_then_index_is_t1():
    tier, constructs = classify_snippet("input_.split(',')[1]")
    assert tier == "T1"
    assert "list_literal" not in constructs

def test_list_literal_is_t2():
    tier, constructs = classify_snippet("['a','b']")
    assert tier == "T2"
    assert "list_literal" in constructs
