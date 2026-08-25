# Golden-record capture harness (Task 4 of the Lua mapping-engine
# feasibility project). See
# .superpowers/sdd/2026-08-25-lua-mapping-engine-deepdive/task-4-brief.md
#
# `make golden` maps each case's record.xml through mapping.xml (validated
# against recdef.xml) with the real Groovy mapping engine and writes the
# result to expected/<case>.rdf.xml. These are the golden files Task 5's
# verifier diffs a candidate Lua engine's output against.

MAKEFILE_DIR := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
REPO_ROOT    := $(realpath $(MAKEFILE_DIR)../..)

CASES_DIR    := $(MAKEFILE_DIR)mappings
EXPECTED_DIR := $(MAKEFILE_DIR)expected
CASES        := $(notdir $(wildcard $(CASES_DIR)/*))

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

.PHONY: clean
clean:
	rm -rf $(EXPECTED_DIR)

.PHONY: list
list:
	@for case in $(CASES); do echo $$case; done
