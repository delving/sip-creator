# Golden Files Testing Strategy for SIP-Creator

## Overview

Golden files (also known as "golden masters" or "approval tests") provide a baseline for testing the mapping transformation engine. By storing known-good input/output pairs, we can ensure that any reimplementation maintains compatibility with the existing Groovy engine.

## Golden Files Structure

### Input File: `_data/input_vgm.xml`
A LIDO format XML file representing a Van Gogh Museum record with:
- Object identification (title, work ID)
- Event information (creator, date, materials)
- Administrative metadata (resource links)

### Output File: `_data/output_vgm.xml`
The expected EDM (Europeana Data Model) RDF/XML output containing:
- ore:Aggregation with provider metadata
- edm:ProvidedCHO with descriptive metadata
- edm:WebResource with image references
- edm:Agent with creator information
- skos:Concept for controlled vocabularies

### Mapping File: `_data/mapping_edm.xml`
Defines the transformation rules from LIDO to EDM

### Generated Code: `_data/vgm.groovy`
The complete Groovy transformation code

## Testing Approach

### 1. Unit Tests (`mapping_test.zig`)

Tests individual components:
- **String operations**: sanitize(), sanitizeURI()
- **XML navigation**: get(), get("_"), get("@attr")
- **Template interpolation**: ${variable} substitution
- **Expression evaluation**: Simple Groovy expressions

### 2. Integration Tests (`full_mapping_test.zig`)

Tests complete mapping scenarios:
- **Node mappings**: Input path to output path transformations
- **Facts injection**: Using mapping metadata in expressions
- **Loop simulation**: Processing multiple values
- **DOM building**: Constructing output XML

### 3. Golden File Validation

Compares actual output with expected output:
```zig
test "golden file transformation" {
    // 1. Parse input XML
    const input = try parseXml(input_golden);
    
    // 2. Execute mappings
    const output = try executeMappings(input, mapping_def);
    
    // 3. Compare with expected output
    try assertXmlEquals(output_golden, output);
}
```

## Key Test Scenarios

### Basic Transformations
```groovy
// String template
"${baseUrl}/resource/${spec}/${_uniqueIdentifier}"

// Expected: "http://data.collectienederland.nl/resource/van-gogh-museum/s-gravenhage-kb-1"
```

### Navigation Patterns
```groovy
// Deep navigation with loops
_input.lidolido * { _lidolido ->
    _lidolido.lidodescriptiveMetadata * { _desc ->
        // Process nested data
    }
}
```

### String Manipulation
```groovy
// URI encoding with method chaining
"urn:van-gogh-museum/${workId}".replaceAll(".jpg", "").sanitizeURI()

// Expected: "urn:van-gogh-museum/F474"
```

### Conditional Logic
```groovy
// Conditional output
if (_lidoactor.lidoactorID) {
    "${_lidoactorID}"
} else {
    createEDMAgentUri(name).sanitizeURI()
}
```

## Benefits of Golden File Testing

1. **Regression Detection**: Immediately identifies breaking changes
2. **Documentation**: Shows real-world input/output examples
3. **Compatibility Assurance**: Ensures Zig implementation matches Groovy
4. **Performance Baseline**: Compare execution times
5. **Edge Case Coverage**: Test complex, real-world mappings

## Running the Tests

```bash
# Run all tests
cd zig-prototype
zig build test

# Validate golden files
./validate_golden_files.sh

# Run specific test
zig test mapping_test.zig

# Run with output
zig test mapping_test.zig --test-filter "golden file"
```

## Adding New Golden Files

1. **Create input XML** from real dataset
2. **Run through current Groovy engine** to generate output
3. **Validate output** meets requirements
4. **Add test case** to cover new scenario
5. **Document** the test purpose

## Continuous Validation

```yaml
# Example CI configuration
test:
  script:
    - zig build test
    - ./validate_golden_files.sh
  artifacts:
    paths:
      - test-results/
    reports:
      junit: test-results/*.xml
```

## Future Enhancements

1. **Fuzzing**: Generate random valid LIDO input
2. **Property Testing**: Verify transformation properties
3. **Performance Benchmarks**: Track speed improvements
4. **Coverage Analysis**: Ensure all mapping patterns tested
5. **Differential Testing**: Compare Zig vs Groovy outputs

The golden files provide a solid foundation for ensuring the Zig implementation maintains full compatibility with the existing Groovy engine while delivering improved performance and WebAssembly support.