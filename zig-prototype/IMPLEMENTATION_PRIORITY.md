# Implementation Priority Based on Actual Usage Analysis

Based on analysis of 521 mapping files containing 51,846 Groovy code snippets, here's what actually needs to be implemented:

## Critical Statistics
- **String interpolation**: 26,916 uses (most critical feature!)
- **String operations**: ~33,000+ calls to replace/replaceAll/sanitize functions
- **Loop operator `*`**: 3,047 uses (critical for collections)
- **Property access with `_` suffix**: Heavily used pattern

## Phase 1: Absolute Must-Have Features (Week 1-2)

### 1. String Operations (46,000+ uses)
```zig
// Top priority functions by usage:
pub fn replaceAll(str: []const u8, pattern: []const u8, replacement: []const u8) []const u8  // 13,606 uses
pub fn replace(str: []const u8, search: []const u8, replacement: []const u8) []const u8      // 12,628 uses
pub fn sanitizeURI(str: []const u8) []const u8   // 4,468 uses - ALREADY IMPLEMENTED ✓
pub fn sanitize(str: []const u8) []const u8       // 2,234 uses - ALREADY IMPLEMENTED ✓
pub fn capitalize(str: []const u8) []const u8     // 2,278 uses
pub fn split(str: []const u8, delimiter: []const u8) [][]const u8  // 1,707 uses
```

### 2. String Interpolation Engine (26,916 uses)
Current implementation only handles simple `${var}`. Need to support:
- Nested property access: `${_input.record[0].about[0]}`
- Method calls in templates: `${_id.sanitizeURI()}`
- Complex expressions: `${start.replaceAll('^0','')}`

### 3. Property Navigation with Special Suffixes
```zig
// The _ suffix pattern for "first non-empty"
node.get("field_")     // Gets first child with non-empty text
node.get("field")      // Gets all children
node.get("@attribute") // Gets attribute value
```

### 4. Basic Type Conversion (4,000+ uses)
```zig
pub fn toString(value: Value) []const u8    // 2,954 uses
pub fn toInteger(str: []const u8) ?i32      // 1,006 uses
```

## Phase 2: Collection Operations (Week 3-4)

### 1. The `*` Operator (3,047 uses)
This is THE most important collection operator:
```groovy
// Real example from your mappings:
_image.thumbnaillarge * { _thumbnaillarge ->
    // Process each thumbnail
}
```

### 2. Collection Methods (3,000+ uses)
```zig
pub fn unique(items: []Value) []Value        // 1,537 uses
pub fn size(collection: []Value) usize       // 1,226 uses
pub fn first(collection: []Value) ?Value     // 619 uses
pub fn isEmpty(collection: []Value) bool     // 519 uses
pub fn last(collection: []Value) ?Value      // 254 uses
```

### 3. Other Operators (Rarely Used)
- `>>` operator: Only 5 uses
- `|` operator: Only 2 uses  
- `?:` elvis: Only 2 uses (surprisingly rare!)

## Phase 3: Pattern Matching (Week 5)

### 1. Regular Expression Support (3,500+ uses)
```zig
pub fn matches(str: []const u8, pattern: []const u8) bool     // 2,140 uses
pub fn matcher(pattern: []const u8, str: []const u8) Matcher  // 1,420 uses
```

Common patterns found:
- Year extraction: `~/([0-9]{4})/`
- Date parsing: `~/([0-9]{4})-[0-9]{2}-[0-9]{2})/`

### 2. String Search Functions
```zig
pub fn indexOf(str: []const u8, search: []const u8) ?usize   // 264 uses
pub fn contains(str: []const u8, search: []const u8) bool    // 55 uses
pub fn startsWith(str: []const u8, prefix: []const u8) bool  // 4 uses
pub fn endsWith(str: []const u8, suffix: []const u8) bool    // 3 uses
```

## Phase 4: Advanced Features (Week 6)

### 1. JSON Support (208 uses)
```zig
// JsonSlurper for parsing JSON data
pub fn parseJson(json: []const u8) Value
```

### 2. List Operations
```zig
pub fn append(list: *ArrayList(Value), item: Value) void  // 735 uses
pub fn push(list: *ArrayList(Value), item: Value) void    // 244 uses
pub fn get(list: []Value, index: usize) ?Value           // 76 uses
```

### 3. Rarely Used But May Be Critical
```zig
pub fn lookup(table: []const u8, key: []const u8) ?[]const u8  // 2 uses (but likely important!)
```

## Features We Can Skip/Simplify

Based on low usage:
- Safe navigation `?.` - Not found in analysis!
- Spread operator `*.` - Not found
- Range operator `..` - Not found
- Method references `.&` - Not found
- Complex Groovy collections (groupBy, inject, etc.) - Only 1 collect() call

## Key Insights from Your Data

1. **String manipulation dominates** - Over 80% of operations are string-related
2. **Simple patterns** - Most Groovy usage is straightforward, not using advanced features
3. **The `*` operator is critical** - It's used 3,047 times for collection processing
4. **Regex is important** - Used for date/year extraction primarily
5. **JSON parsing needed** - 208 JsonSlurper uses indicate external data integration

## Recommended Implementation Order

1. **Week 1**: String operations (replace, replaceAll, capitalize, split)
2. **Week 2**: Proper string interpolation with nested property access
3. **Week 3**: The `*` operator and basic collection methods
4. **Week 4**: Regular expression support
5. **Week 5**: JSON parsing and remaining string functions
6. **Week 6**: Edge cases and optimization

This is much more focused than the original plan - we can ignore many complex Groovy features and focus on the string manipulation and collection operations that make up 95% of actual usage.