# SIP-Creator Zig Implementation - Focused TODO

Based on analysis of **521 mapping files** containing **51,846 Groovy snippets**.

## Week 1: Critical String Operations (46,000+ uses)

### String Functions (Must Have)
- [ ] `replaceAll(pattern, replacement)` - 13,606 uses
- [ ] `replace(search, replacement)` - 12,628 uses  
- [ ] `capitalize()` - 2,278 uses
- [ ] `split(delimiter)` - 1,707 uses
- [ ] `trim()` - 35 uses (but likely underreported)

### Type Conversions  
- [ ] `toString()` - 2,954 uses
- [ ] `toInteger()` - 1,006 uses

### Pattern Matching
- [ ] `matches(pattern)` - 2,140 uses
- [ ] `matcher(pattern)` and match groups - 1,420 uses

## Week 2: String Interpolation (26,916 uses)

Current implementation only handles `${simple}`. Need:
- [ ] Nested property access: `${_input.record[0].about[0]}`
- [ ] Method calls: `${_id.sanitizeURI()}`
- [ ] Complex expressions: `${start.replaceAll('^0','')}`
- [ ] Underscore suffix handling: `${_field_}`

## Week 3: Collection Operations

### The Star Operator (CRITICAL)
- [ ] `*` operator with closures - 3,047 uses
  ```groovy
  _image.thumbnaillarge * { thumbnail ->
      // process each
  }
  ```

### Collection Methods
- [ ] `unique()` - 1,537 uses
- [ ] `size()` - 1,226 uses  
- [ ] `first()` - 619 uses
- [ ] `isEmpty()` - 519 uses
- [ ] `append()` - 735 uses
- [ ] `last()` - 254 uses

## Week 4: XML Processing

### Navigation with Special Patterns
- [ ] `node.get("field")` - all children
- [ ] `node.get("field_")` - first with non-empty text (CRITICAL pattern)
- [ ] `node.get("@attr")` - attribute access
- [ ] Array access: `node[0]`

### XML Generation
- [ ] EDM/ESE/RDF output with namespaces
- [ ] Conditional elements
- [ ] Attribute generation

## Week 5: Supporting Features

### Additional String Functions
- [ ] `indexOf()` - 264 uses
- [ ] `contains()` - 55 uses
- [ ] `toLowerCase()` - 48 uses
- [ ] `substring()`, `startsWith()`, `endsWith()` - rare but needed

### JSON Support
- [ ] `JsonSlurper` for parsing - 208 uses

### Special Functions
- [ ] `lookup()` - 2 uses (but likely critical for vocabularies)
- [ ] Custom URL builders for EDM

## What We DON'T Need (0 uses in your mappings)

❌ Safe navigation (`?.`)  
❌ Spread operator (`*.`)  
❌ Range operator (`..`)  
❌ Method references (`.&`)  
❌ Complex Groovy collections (groupBy, inject, etc.)  
❌ For/while loops  
❌ Switch statements  

## Performance Targets

Based on usage patterns:
- String operations: Must be FAST (80% of all operations)
- Memory: Efficient handling of large XML files
- WASM size: Keep under 100KB if possible

## Testing Strategy

1. Create test cases from most common patterns in your data
2. Use actual mapping snippets as test inputs
3. Compare output with current Groovy implementation
4. Focus on the 20 most-used functions first (covers 90% of usage)