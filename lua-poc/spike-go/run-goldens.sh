#!/usr/bin/env bash
# Runs the whole Lua spike pipeline over the golden cases (Task 8, step 5):
#
#   mapping.xml --LuaMappingGenerator--> mapping.lua --spike-go--> actual.rdf.xml
#                                                    --GoldenVerify--> verdict
#
# Cases the converter refuses are reported as UNSUPPORTED with the construct
# name (that refusal list is a deliverable, not a failure) and produce no
# actual file, so `make verify` reports them MISSING.
#
# Usage: lua-poc/spike-go/run-goldens.sh [outDir]
set -u

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
out="${1:-$repo/lua-poc/spike-go/out}"

mkdir -p "$out"
(cd "$here" && go build -o spike-go .) || exit 1

generated=0
refused=0
for caseDir in "$repo"/lua-poc/golden/mappings/*/; do
    name="$(basename "$caseDir")"
    verdict=$(mvn -q -f "$repo/pom.xml" -pl sip-core exec:java \
        -Dexec.classpathScope=test \
        -Dexec.mainClass=eu.delving.metadata.LuaMappingGenerator \
        -Dexec.args="$caseDir $out/$name.lua" 2>/dev/null \
        | grep -E '^(GENERATED|UNSUPPORTED)')
    case "$verdict" in
        GENERATED*)
            if "$here/spike-go" --engine "$repo/lua-poc/engine" \
                    "$out/$name.lua" "$caseDir/record.xml" > "$out/$name.rdf.xml" 2> "$out/$name.err"; then
                echo "$name: generated + ran"
                generated=$((generated+1))
            else
                echo "$name: RUNTIME ERROR $(head -1 "$out/$name.err")"
                rm -f "$out/$name.rdf.xml"
            fi
            ;;
        UNSUPPORTED*)
            echo "$name: ${verdict#UNSUPPORTED }"
            refused=$((refused+1))
            ;;
        *)
            echo "$name: GENERATOR FAILED (no verdict line)"
            ;;
    esac
done

echo "---"
echo "generated+ran: $generated, converter-refused: $refused"
echo "now: make -C $repo/lua-poc/golden verify ACTUAL_DIR=$out"
