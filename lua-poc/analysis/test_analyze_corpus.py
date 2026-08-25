import json, subprocess, sys, pathlib
FIX = pathlib.Path(__file__).parent / "fixtures"

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
    assert r["summary"]["mappings"] == 3
    assert r["summary"]["snippets"] == 6
