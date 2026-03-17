# Groovy Code Analysis Guide

This guide explains how to analyze your existing mapping files to understand what Groovy features need to be implemented in the Zig migration.

## Overview

We have two analysis scripts that will scan all your mapping files and extract:
1. All Groovy code patterns used
2. Frequency of different features
3. Implementation priority based on actual usage

## Testing Your Directory Structure

Before running the full analysis, you can test that the scripts find your mapping files correctly:

```bash
# Test file discovery
python3 test_find_mappings.py ~/PocketMapper/lila
```

This will show:
- How many mapping files were found
- Which organizations have mapping files
- Which collections have the most mappings
- Sample file paths to verify correctness

## Running the Analysis

### 1. Comprehensive Analysis (`analyze_mappings.py`)

This script provides a detailed analysis of all Groovy code found in your mappings.

```bash
# Run from the zig-prototype directory
python3 analyze_mappings.py ~/PocketMapper/lila

# Or make it executable and run directly
./analyze_mappings.py ~/PocketMapper/lila
```

**Output files:**
- `groovy_analysis_report.json` - Summary statistics and patterns
- `groovy_snippets.json` - All extracted Groovy code snippets

**What it analyzes:**
- String interpolation patterns (`${...}`)
- Method calls and their frequency
- Operators (arithmetic, logical, comparison)
- Loop operators (`*`, `**`, `>>`, `|`)
- Property access patterns
- Field paths (e.g., `_input.record.field`)
- Special functions (sanitize, lookup, etc.)
- Closures and conditionals
- Regular expressions

### 2. Implementation Guide (`groovy_feature_extractor.py`)

This script categorizes features by implementation priority.

```bash
# Run from the zig-prototype directory
python3 groovy_feature_extractor.py ~/PocketMapper/lila

# Or make it executable and run directly
./groovy_feature_extractor.py ~/PocketMapper/lila
```

**Output files:**
- `groovy_implementation_guide.json` - Features grouped by implementation phase
- `GROOVY_FEATURES_REPORT.md` - Human-readable implementation guide

**Implementation phases:**
1. **Phase 1 (Critical)** - Core features needed for basic functionality
2. **Phase 2 (Common)** - Commonly used patterns in mappings
3. **Phase 3 (Advanced)** - Advanced features for full compatibility
4. **Phase 4 (Optional)** - Rarely used features

## Understanding the Results

### From `groovy_analysis_report.json`:

```json
{
  "summary": {
    "total_files_analyzed": 150,
    "total_groovy_snippets": 2500,
    "unique_field_paths": 180
  },
  "function_calls": {
    "trim": 450,
    "sanitize": 380,
    "split": 250,
    ...
  },
  "operators": {
    "?. (safe navigation)": 320,
    "?: (elvis)": 180,
    "+ (addition/concat)": 450
  },
  ...
}
```

This tells you:
- How many mapping files you have
- Which functions are used most (implement these first!)
- Which operators are common in your mappings
- Sample code showing how features are used

### From `GROOVY_FEATURES_REPORT.md`:

This provides a readable guide showing:
- Examples of each feature found
- Sample code from actual mappings
- Suggested implementation order

## Using Results for Implementation

1. **Start with Phase 1 features** - These are critical for basic functionality
2. **Use frequency data** - Implement most-used functions first
3. **Reference sample code** - Real examples from your mappings show exact usage patterns
4. **Test with your data** - Use the extracted snippets to create test cases

## Directory Structure Expected

The scripts expect this structure:
```
root_directory/
├── brabantcloud/
│   └── PocketMapper/
│       └── work/
│           ├── bhic/
│           │   ├── mapping.xml
│           │   └── mapping_edm.xml
│           ├── brabant-collectie/
│           │   └── mapping.xml
│           └── ...
├── datahub/
│   └── PocketMapper/
│       └── work/
│           ├── adlib-collect/
│           │   └── mapping.xml
│           └── ...
└── dcn/
    └── PocketMapper/
        ├── work/
        │   ├── amsterdam-museum/
        │   │   └── mapping.xml
        │   └── ...
        └── work2/
            └── ...
```

The scripts will:
- Look for all `org_id/PocketMapper/work*/collection_name/` directories
- Find all `mapping*.xml` files in each collection directory
- Take the latest version when multiple mapping files exist (e.g., `mapping_edm.xml` vs `mapping_ese.xml`)

## Interpreting Feature Categories

### Basic Features (Must Have)
- String literals and interpolation
- Property access (dot notation)
- Method calls
- Basic operators (+, -, ==, !=)

### Intermediate Features (Common)
- Safe navigation (`?.`)
- Elvis operator (`?:`)
- List operations with `it`
- Array/List access
- Common string methods

### Advanced Features
- Regular expressions
- Complex conditionals
- Type casting (`as`)
- Collection methods (collect, findAll, etc.)

### Rare Features
- Spread operator (`*.`)
- Range operator (`..`)
- Method references (`.&`)

## Next Steps

After running the analysis:

1. Review the generated reports to understand your Groovy usage patterns
2. Update the TODO.md with specific functions and features from your analysis
3. Create test cases using the extracted snippets
4. Prioritize implementation based on frequency of use
5. Consider if rarely-used features can be refactored or omitted

The analysis will give you a clear picture of exactly what subset of Groovy you need to implement for your specific mapping files.