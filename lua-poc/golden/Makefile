# Golden-record capture harness (Task 4 of the Lua mapping-engine
# feasibility project). See
# .superpowers/sdd/2026-08-25-lua-mapping-engine-deepdive/task-4-brief.md
#
# `make golden` maps each case's record.xml through mapping.xml (validated
# against recdef.xml) with the real Groovy mapping engine and writes the
# result to expected/<case>.rdf.xml. These are the golden files Task 5's
# verifier diffs a candidate Lua engine's output against.
#
# `make verify ACTUAL_DIR=<dir>` (Task 5) compares <dir>/<case>.rdf.xml
# against expected/<case>.rdf.xml for every case, via GoldenVerify
# (Jena-based: IDENTICAL/SEMANTIC pass, MISMATCH or a missing actual file
# fails). Used by Task 8 to check a candidate Lua engine's output against
# the goldens.

MAKEFILE_DIR := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
REPO_ROOT    := $(realpath $(MAKEFILE_DIR)../..)

CASES_DIR    := $(MAKEFILE_DIR)mappings
EXPECTED_DIR := $(MAKEFILE_DIR)expected
# Only directories are cases; mappings/ also holds a README.md that isn't
# one (and has no expected/<name>.rdf.xml counterpart).
CASES        := $(notdir $(shell find $(CASES_DIR) -mindepth 1 -maxdepth 1 -type d))

.PHONY: golden
golden:
	@mkdir -p $(EXPECTED_DIR)
	@for case in $(CASES); do \
		echo "== capturing $$case =="; \
		mvn -q -f $(REPO_ROOT)/pom.xml -pl sip-app exec:java \
			-Dexec.classpathScope=test \
			-Dexec.mainClass=eu.delving.sip.golden.GoldenCapture \
			-Dexec.args="$(CASES_DIR)/$$case $(EXPECTED_DIR)/$$case.rdf.xml" \
			|| exit 1; \
	done
	@echo "captured $(words $(CASES)) cases to $(EXPECTED_DIR)/"

.PHONY: verify
verify:
ifndef ACTUAL_DIR
	$(error ACTUAL_DIR is not set; usage: make verify ACTUAL_DIR=<dir>)
endif
	@failures=0; total=0; \
	for case in $(CASES); do \
		total=$$((total+1)); \
		actual="$(ACTUAL_DIR)/$$case.rdf.xml"; \
		expected="$(EXPECTED_DIR)/$$case.rdf.xml"; \
		if [ ! -f "$$actual" ]; then \
			echo "$$case: MISSING (no $$actual)"; \
			failures=$$((failures+1)); \
			continue; \
		fi; \
		verdict=$$(mvn -q -f $(REPO_ROOT)/pom.xml -pl sip-app exec:java \
			-Dexec.classpathScope=test \
			-Dexec.mainClass=eu.delving.sip.golden.GoldenVerify \
			-Dexec.args="$$expected $$actual" 2>/dev/null \
			| grep -E '^(IDENTICAL|SEMANTIC|MISMATCH)'); \
		echo "$$case: $$verdict"; \
		case "$$verdict" in \
			MISMATCH*|"") failures=$$((failures+1));; \
		esac; \
	done; \
	passed=$$((total-failures)); \
	echo "verified $$passed/$$total cases ($$failures failed)"; \
	[ $$failures -eq 0 ]

.PHONY: clean
clean:
	rm -rf $(EXPECTED_DIR)

.PHONY: list
list:
	@for case in $(CASES); do echo $$case; done
