# SIP-Creator Mapping Engine Zig Prototype

This prototype demonstrates how the SIP-Creator mapping engine could be reimplemented in Zig with WebAssembly support.

## Features Demonstrated

1. **XML Node Navigation** - GroovyNode-compatible navigation patterns
2. **String Utilities** - sanitize() and sanitizeURI() functions
3. **String Interpolation** - Template variable substitution
4. **WebAssembly Export** - Functions accessible from JavaScript and Go
5. **Memory Management** - Safe memory handling for WASM

## Building

### Prerequisites
- Zig 0.11.0 or later

### Build Commands

```bash
# Build native executable
zig build

# Build WebAssembly module
zig build -Dtarget=wasm32-freestanding

# Run tests
zig build test

# Run the example
zig build run
```

## Integration Examples

### Browser Integration
Open `example_browser_integration.html` in a web browser after building the WASM module.

### Go Integration
```bash
# Install Wasmer Go SDK
go get github.com/wasmerio/wasmer-go

# Run the Go example
go run example_go_integration.go
```

## Architecture Overview

### Core Components

1. **XmlNode** - Represents XML elements with GroovyNode-compatible navigation
2. **StringUtils** - String manipulation functions matching SIP-Creator
3. **MappingContext** - Execution context with facts and variables
4. **Expression** - Simple expression evaluation for mapping code

### Key Design Decisions

1. **Memory Management** - Manual allocation tracking for WASM compatibility
2. **Navigation API** - Matches GroovyNode's get() method behavior
3. **String Handling** - Zero-copy where possible, explicit ownership
4. **Error Handling** - Using Zig's error unions for safety

## Next Steps

1. **XML Parser** - Implement full XML parsing
2. **Expression Parser** - Parse Groovy subset for compatibility
3. **DOM Builder** - Generate output XML/RDF
4. **Loop Operators** - Implement *, **, >> operators
5. **Function Library** - Port all SIP-Creator functions

## Performance Considerations

- **Memory Allocation** - Use arena allocators for batch operations
- **String Operations** - Minimize copies with careful ownership
- **WASM Size** - Use wasm-opt for optimization
- **Parsing** - Consider streaming for large documents

## Compatibility Notes

This prototype aims to be compatible with existing SIP-Creator mappings by:
- Matching the GroovyNode navigation API
- Supporting the same string manipulation functions
- Providing equivalent variable interpolation
- Maintaining the same execution model

The main differences are:
- Static typing instead of dynamic
- Explicit memory management
- Compiled instead of interpreted
- Smaller runtime footprint