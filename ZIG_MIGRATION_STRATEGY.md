# SIP-Creator Mapping Engine Migration to Zig

## Executive Summary

This document outlines strategies for migrating the SIP-Creator mapping engine from Groovy to Zig while maintaining backwards compatibility with 15 years of existing mappings. The goal is to create a WebAssembly-compatible engine that can run in browsers and be integrated with Go applications.

## Current Architecture Analysis

### Core Components

1. **Input Processing**
   - XML parsing into GroovyNode tree structure
   - Namespace-aware navigation
   - List-based child access patterns

2. **Mapping Execution**
   - Groovy script generation from mapping XML
   - Dynamic code compilation and execution
   - DSL operators for list processing

3. **Output Generation**
   - DOM building through Groovy builders
   - Namespace handling
   - Multi-value element creation

### Key Groovy Features Used

- **Dynamic typing** and runtime code execution
- **Closures** for iteration and transformation
- **String interpolation** (GStrings)
- **Builder pattern** for DOM construction
- **Category-based DSL** for domain operations
- **Operator overloading** for list operations

## Migration Strategies

### Option 1: Groovy-Compatible Expression Language

Create a minimal Groovy-compatible expression parser in Zig that supports the subset of Groovy used in mappings.

**Pros:**
- Direct compatibility with existing mappings
- No migration needed for users
- Focused implementation scope

**Cons:**
- Significant parsing/interpretation complexity
- Performance overhead from interpretation
- Need to implement Groovy semantics

**Implementation approach:**
```zig
const GroovyExpr = union(enum) {
    string_literal: []const u8,
    string_interpolation: []StringPart,
    identifier: []const u8,
    method_call: struct {
        receiver: *GroovyExpr,
        method: []const u8,
        args: []GroovyExpr,
    },
    closure: struct {
        params: [][]const u8,
        body: []Statement,
    },
    // ... other expression types
};
```

### Option 2: Transpilation Approach

Convert Groovy mapping code to Zig at build time, creating native WebAssembly modules.

**Pros:**
- Maximum performance
- Type safety at compile time
- Smaller runtime

**Cons:**
- Complex transpiler implementation
- Need to handle all Groovy constructs
- Harder to support runtime code generation

### Option 3: Hybrid DSL Approach (Recommended)

Create a new DSL that captures the essential mapping operations while providing a Groovy compatibility layer.

**Pros:**
- Clean, purpose-built design
- Optimized for performance
- Gradual migration path
- Smaller implementation scope

**Cons:**
- Requires compatibility layer
- Some manual migration might be needed

## Recommended Implementation Plan

### Phase 1: Core Data Structures (2-3 weeks)

```zig
// XML Node representation
const XmlNode = struct {
    tag: []const u8,
    namespace: ?[]const u8,
    attributes: std.StringHashMap([]const u8),
    children: std.ArrayList(*XmlNode),
    text: ?[]const u8,
    parent: ?*XmlNode,
    
    // Navigation methods matching GroovyNode
    pub fn get(self: *XmlNode, key: []const u8) NodeList {
        if (key[0] == '@') {
            // Return attribute value
        } else if (std.mem.eql(u8, key, "*")) {
            // Return all children
        } else if (key[key.len-1] == '_') {
            // Return first non-empty match
        } else {
            // Return all matching children
        }
    }
};

// Mapping context
const MappingContext = struct {
    input: *XmlNode,
    output: *DomBuilder,
    facts: std.StringHashMap([]const u8),
    unique_identifier: []const u8,
    opt_lookup: std.StringHashMap(std.StringHashMap([]const u8)),
};
```

### Phase 2: Expression Engine (3-4 weeks)

```zig
// Simple expression language for mappings
const Expr = union(enum) {
    literal: Literal,
    variable: []const u8,
    field_access: struct {
        object: *Expr,
        field: []const u8,
    },
    method_call: struct {
        object: *Expr,
        method: []const u8,
        args: []Expr,
    },
    string_template: []TemplatePart,
    conditional: struct {
        condition: *Expr,
        then_expr: *Expr,
        else_expr: ?*Expr,
    },
};

// Expression evaluator
pub fn eval(expr: Expr, ctx: *MappingContext) Value {
    switch (expr) {
        .literal => |lit| return Value{ .string = lit.value },
        .variable => |name| return ctx.lookupVariable(name),
        .field_access => |fa| {
            const obj = eval(fa.object, ctx);
            return obj.getField(fa.field);
        },
        // ... other cases
    }
}
```

### Phase 3: Groovy Compatibility Layer (2-3 weeks)

```zig
// Parse Groovy subset into our expression language
const GroovyParser = struct {
    pub fn parseExpression(groovy_code: []const u8) !Expr {
        // Parse common Groovy patterns:
        // - String interpolation: "${var}"
        // - Method calls: obj.method()
        // - Closures: list * { it.field }
        // - Safe navigation: obj?.field
        // - Elvis operator: value ?: "default"
    }
};

// Built-in functions matching Groovy/SIP-Creator
pub fn sanitizeURI(value: []const u8) []const u8 {
    // Implement URI encoding
}

pub fn sanitize(value: []const u8) []const u8 {
    // Remove extra whitespace
}
```

### Phase 4: Mapping Execution Engine (2-3 weeks)

```zig
const MappingEngine = struct {
    allocator: std.mem.Allocator,
    
    pub fn executeMapping(
        self: *MappingEngine,
        input_xml: []const u8,
        mapping_def: MappingDefinition,
        record_def: RecordDefinition,
    ) ![]const u8 {
        // 1. Parse input XML
        const input = try XmlParser.parse(self.allocator, input_xml);
        
        // 2. Create output builder
        var output = DomBuilder.init(self.allocator);
        
        // 3. Create context
        var ctx = MappingContext{
            .input = input,
            .output = &output,
            .facts = mapping_def.facts,
            // ...
        };
        
        // 4. Execute node mappings
        for (mapping_def.node_mappings) |node_mapping| {
            try self.executeNodeMapping(&ctx, node_mapping);
        }
        
        // 5. Serialize output
        return output.toXml();
    }
};
```

### Phase 5: WebAssembly Integration (1-2 weeks)

```zig
// WASM exports
export fn createEngine() *MappingEngine {
    return MappingEngine.init(wasm_allocator);
}

export fn executeMapping(
    engine: *MappingEngine,
    input_ptr: [*]const u8,
    input_len: usize,
    mapping_ptr: [*]const u8,
    mapping_len: usize,
) [*]const u8 {
    const input = input_ptr[0..input_len];
    const mapping = mapping_ptr[0..mapping_len];
    
    const result = engine.executeMapping(input, mapping) catch |err| {
        // Handle error
        return null;
    };
    
    return result.ptr;
}
```

### Phase 6: Go Integration (1 week)

```go
// Go wrapper for WASM engine
type MappingEngine struct {
    wasmInstance *wasmer.Instance
    createEngine func() int32
    executeMapping func(engine, inputPtr, inputLen, mappingPtr, mappingLen int32) int32
}

func NewMappingEngine(wasmBytes []byte) (*MappingEngine, error) {
    engine := wasmer.NewEngine()
    store := wasmer.NewStore(engine)
    module, err := wasmer.NewModule(store, wasmBytes)
    if err != nil {
        return nil, err
    }
    
    instance, err := wasmer.NewInstance(module, wasmer.NewImportObject())
    if err != nil {
        return nil, err
    }
    
    return &MappingEngine{
        wasmInstance: instance,
        createEngine: instance.Exports["createEngine"],
        executeMapping: instance.Exports["executeMapping"],
    }, nil
}

func (m *MappingEngine) ExecuteMapping(input, mapping string) (string, error) {
    // Allocate memory in WASM
    // Copy input and mapping to WASM memory
    // Call executeMapping
    // Read result from WASM memory
    return result, nil
}
```

## Testing Strategy

1. **Unit Tests**: Test each component in isolation
2. **Compatibility Tests**: Run existing mappings through both engines
3. **Performance Benchmarks**: Compare with Groovy implementation
4. **Fuzzing**: Test parser robustness

## Migration Path for Users

1. **Phase 1**: Zig engine supports core mappings (80% coverage)
2. **Phase 2**: Compatibility layer for complex Groovy features
3. **Phase 3**: Optional transpiler for performance-critical mappings
4. **Phase 4**: Gradual migration tools and documentation

## Performance Targets

- **Parsing**: 10x faster than Groovy
- **Execution**: 5-10x faster for typical mappings
- **Memory**: 50% reduction in memory usage
- **WASM size**: < 1MB compressed

## Risk Mitigation

1. **Compatibility**: Maintain Groovy engine in parallel
2. **Testing**: Extensive test suite with real-world mappings
3. **Rollback**: Version all changes for easy rollback
4. **Documentation**: Comprehensive migration guide

## Prototype Implementation

A minimal prototype demonstrating the core concepts:

```zig
// Save as mapping_engine_prototype.zig
const std = @import("std");

pub fn main() !void {
    // Example of core functionality
    var gpa = std.heap.GeneralPurposeAllocator(.{}){};
    defer _ = gpa.deinit();
    
    const allocator = gpa.allocator();
    
    // Parse simple XML
    const input_xml =
        \\<input id="001">
        \\  <title>Test Title</title>
        \\  <creator>Test Creator</creator>
        \\</input>
    ;
    
    var parser = XmlParser.init(allocator);
    const input = try parser.parse(input_xml);
    defer input.deinit();
    
    // Simple mapping execution
    const title = input.get("title_");
    std.debug.print("Title: {s}\n", .{title.text()});
}
```

## Next Steps

1. **Proof of Concept**: Build minimal working prototype
2. **Performance Testing**: Validate performance assumptions
3. **User Feedback**: Test with real mapping examples
4. **Iterative Development**: Build features incrementally
5. **Documentation**: Create migration guides

This strategy provides a clear path forward while minimizing risk and maintaining compatibility with existing mappings.