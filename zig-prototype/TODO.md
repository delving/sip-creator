# SIP-Creator Zig Prototype TODO List

## UPDATE: Based on Analysis of 521 Mapping Files (51,846 Groovy Snippets)

### 1. String Operations 🔴 CRITICAL (46,000+ uses)
- [ ] `replaceAll(pattern, replacement)` - 13,606 uses
- [ ] `replace(search, replacement)` - 12,628 uses  
- [x] `sanitizeURI()` - 4,468 uses ✓
- [ ] `capitalize()` - 2,278 uses
- [x] `sanitize()` - 2,234 uses ✓
- [ ] `split(delimiter)` - 1,707 uses
- [ ] `toString()` - 2,954 uses
- [ ] `toInteger()` - 1,006 uses
- [ ] `matches(pattern)` - 2,140 uses
- [ ] `matcher(pattern)` - 1,420 uses

### 2. String Interpolation Engine 🔴 CRITICAL (26,916 uses)
- [x] Basic `${variable}` interpolation ✓
- [ ] Nested property access: `${_input.record[0].about[0]}`
- [ ] Method calls in templates: `${_id.sanitizeURI()}`
- [ ] Complex expressions: `${start.replaceAll('^0','')}`
- [ ] Handle underscore suffix: `${_field_}` (first non-empty)

### 3. XML Processing ⚠️ HIGH PRIORITY
- [ ] Implement full XML parser (currently using mock data)
  - [ ] Support the `_` suffix pattern for "first non-empty"
  - [ ] Handle attribute access with `@`
  - [ ] Support namespaces
- [ ] Implement XML serializer for output

### 4. Collection Operations ⚠️ HIGH PRIORITY
- [ ] The `*` operator - 3,047 uses (CRITICAL for collection processing)
- [ ] `unique()` - 1,537 uses
- [ ] `size()` - 1,226 uses
- [ ] `first()` - 619 uses
- [ ] `isEmpty()` - 519 uses
- [ ] `last()` - 254 uses
- [ ] `append()` - 735 uses
- [ ] `push()` - 244 uses
- [ ] `>>` operator - 5 uses (low priority)
- [ ] `|` operator - 2 uses (low priority)

### 5. Additional String Functions
- [ ] `indexOf()` - 264 uses
- [ ] `contains()` - 55 uses
- [ ] `toLowerCase()` - 48 uses
- [ ] `trim()` - 35 uses
- [ ] `substring()` - 3 uses
- [ ] `startsWith()` - 4 uses
- [ ] `endsWith()` - 3 uses
- [ ] `toUpperCase()` - 3 uses
- [ ] `length()` - 3 uses

### 6. Advanced Features (Lower Priority)
- [ ] JSON parsing (`JsonSlurper`) - 208 uses
- [ ] Regular expression objects - for complex patterns
- [ ] `lookup()` function - 2 uses (but may be critical)
- [ ] Type conversion (`as` operator) - rare but needed
- [ ] Elvis operator `?:` - only 2 uses!

### 7. DOM Builder
- [ ] Generate EDM/ESE/RDF output
- [ ] Handle namespaces properly
- [ ] Support conditional elements
  - [ ] Language tags (`xml:lang`)
  - [ ] Datatype attributes

## What We Can Skip (Based on Zero/Minimal Usage)

### Not Found in Analysis:
- Safe navigation (`?.`) - 0 uses
- Spread operator (`*.`) - 0 uses  
- Range operator (`..`) - 0 uses
- Method references (`.&`) - 0 uses
- Complex closures with multiple parameters
- Groovy's advanced collection methods (groupBy, inject, etc.)
- Switch/case statements
- For/while loops (everything uses operators like `*`)

### Extremely Rare:
- Elvis operator (`?:`) - only 2 uses
- Tuple operator (`|`) - only 2 uses
- Type casting (`as`) - very rare

## Implementation Strategy

Based on actual usage, focus on:
1. **String manipulation** (80% of all operations)
2. **The `*` operator** (critical for collections)
3. **Property navigation** with `_` suffix
4. **Basic regex** for year/date extraction

This is much simpler than full Groovy support!
- [ ] Port all built-in functions from Groovy
  - [ ] String manipulation
    - [x] `sanitize()`
    - [x] `sanitizeURI()`
    - [ ] `replaceAll()`
    - [ ] `split()`
    - [ ] `substring()`
    - [ ] `toLowerCase()` / `toUpperCase()`
    - [ ] `trim()`
    - [ ] `contains()` / `startsWith()` / `endsWith()`
  - [ ] Date functions
    - [ ] `calculateAge()`
    - [ ] `calculateAgeRange()`
    - [ ] Date parsing and formatting
  - [ ] URI/URL functions
    - [ ] `createOreAggregationUri()`
    - [ ] `createEDMAgentUri()`
    - [ ] `createEDMPlaceUri()`
    - [ ] URL encoding/decoding
  - [ ] Custom transformation functions
    - [ ] `convertToUTM()` / `convertToLATLONG()`
    - [ ] `reverseNames()`
    - [ ] Image URL transformations

### 6. Mapping File Support
- [x] Basic mapping XML structure parsing (partial)
- [ ] Complete mapping XML parser
  - [ ] Facts extraction
  - [ ] Functions parsing
  - [ ] Node mappings with all attributes
  - [ ] Dictionary mappings
  - [ ] Dynamic options (dyn-opts)
- [ ] Record definition parser
  - [ ] Element definitions with attributes
  - [ ] Namespace handling
  - [ ] Validation rules
  - [ ] Documentation extraction
  - [ ] Option lists
  - [ ] Templates

### 7. Validation
- [ ] XSD validation
  - [ ] Load and parse XSD schemas
  - [ ] Validate output against schema
  - [ ] Generate meaningful error messages
- [ ] SHACL validation for RDF
  - [ ] Parse SHACL shapes
  - [ ] Validate RDF output
- [ ] Custom assertions
  - [ ] Execute assertion tests
  - [ ] Report validation failures

### 8. Error Handling
- [ ] Comprehensive error reporting
  - [ ] Line numbers in Groovy code
  - [ ] Input XML path for errors
  - [ ] Validation error details
  - [ ] Stack traces for debugging
- [ ] Error recovery
  - [ ] Continue processing after errors
  - [ ] Collect all errors for reporting
  - [ ] Skip invalid records option

### 9. Performance Optimizations
- [ ] Implement caching
  - [ ] Compiled Groovy expressions
  - [ ] XPath results
  - [ ] Regex patterns
- [ ] Memory optimizations
  - [ ] Streaming for large files
  - [ ] Arena allocators for temporary data
  - [ ] Pool allocators for repeated structures
- [ ] Parallel processing
  - [ ] Multi-threaded record processing
  - [ ] Work stealing queue
  - [ ] Thread-safe output writing

### 10. WebAssembly Support 🔧
- [ ] Fix WASM build issues
  - [ ] Handle null pointers properly
  - [ ] Fix memory allocation for WASM32
  - [ ] Remove OS-specific dependencies
- [ ] Optimize WASM size
  - [ ] Dead code elimination
  - [ ] Use wasm-opt
  - [ ] Minimize allocator overhead
- [ ] JavaScript bindings
  - [ ] TypeScript definitions
  - [ ] Promise-based API
  - [ ] Streaming support
- [ ] Go bindings improvements
  - [ ] Better memory management
  - [ ] Concurrent execution support

### 11. Testing
- [x] Basic unit tests
- [ ] Comprehensive test coverage
  - [ ] All function implementations
  - [ ] Edge cases (empty input, malformed XML, etc.)
  - [ ] Performance benchmarks
  - [ ] Memory leak detection
  - [ ] Fuzzing tests
- [ ] Integration tests
  - [ ] Real mapping files from production
  - [ ] Large dataset processing
  - [ ] Various metadata formats (LIDO, DC, MODS, etc.)
- [ ] Comparison tests
  - [ ] Output identical to Groovy engine
  - [ ] Performance comparisons
  - [ ] Memory usage comparisons

### 12. Documentation
- [x] Basic README
- [x] Migration strategy document
- [ ] API documentation
  - [ ] Public functions and types
  - [ ] Usage examples
  - [ ] Migration guide from Groovy
- [ ] Architecture documentation
  - [ ] Component diagrams
  - [ ] Data flow diagrams
  - [ ] Performance characteristics

### 13. Tooling
- [ ] CLI interface
  - [ ] Command-line argument parsing
  - [ ] Progress reporting
  - [ ] Batch processing
  - [ ] Watch mode for development
- [ ] Debugging support
  - [ ] Debug output for expressions
  - [ ] Trace mapping execution
  - [ ] Memory usage reporting
- [ ] Development tools
  - [ ] Mapping validator
  - [ ] Expression tester
  - [ ] Performance profiler

### 14. Compatibility Features
- [ ] Groovy compatibility mode
  - [ ] Support all used Groovy features
  - [ ] Compatible error messages
  - [ ] Same output formatting
- [ ] Migration tools
  - [ ] Detect unsupported Groovy features
  - [ ] Suggest alternatives
  - [ ] Automated conversion where possible

### 15. Advanced Features
- [ ] Streaming mode
  - [ ] Process records without loading full file
  - [ ] Memory-bounded processing
  - [ ] Progress callbacks
- [ ] Incremental processing
  - [ ] Resume from checkpoint
  - [ ] Process only changed records
- [ ] Custom extensions
  - [ ] Plugin system for functions
  - [ ] Custom validators
  - [ ] Output format plugins

## Priority Order

1. **Critical** (Required for basic functionality):
   - Full XML parser
   - Groovy expression parser (basic subset)
   - DOM builder completion
   - Core string functions

2. **High** (Required for real-world usage):
   - Loop operators
   - Complete function library
   - Mapping file parser
   - Basic validation

3. **Medium** (Important for production):
   - WebAssembly support
   - Performance optimizations
   - Comprehensive testing
   - Error handling improvements

4. **Low** (Nice to have):
   - Advanced tooling
   - Streaming mode
   - Plugin system
   - Full Groovy compatibility

## Next Steps

1. Implement XML parser using a library like `xml-zig` or write custom parser
2. Create basic Groovy expression parser for common patterns
3. Complete DOM builder for output generation
4. Port essential functions from the Groovy codebase
5. Create integration tests with real mapping files

## Notes

- Focus on the subset of Groovy actually used in mappings (80/20 rule)
- Prioritize compatibility over features not used in practice
- Consider using existing Zig libraries where appropriate
- Keep WebAssembly constraints in mind during implementation