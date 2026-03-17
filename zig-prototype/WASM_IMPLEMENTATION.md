# WASM Implementation Guide

This document describes the WebAssembly implementation of the SIP-Creator mapping engine.

## Overview

The WASM implementation provides core string manipulation and templating functions from the SIP-Creator mapping engine in a tiny, portable format that runs in browsers and server environments.

## Architecture

### Memory Management
- **Fixed Buffers**: 
  - `result_buffer` (64KB): For function outputs
  - `temp_buffer` (64KB): For temporary data and variables
  - 2MB heap for allocator
- **Clear Separation**: Result buffer is cleared before each operation
- **No Dynamic Allocation**: All memory is pre-allocated for predictability

### Core Functions

#### 1. String Sanitization (`wasmSanitize`)
- Removes extra whitespace and newlines
- Preserves single spaces between words
- Handles UTF-8 correctly (multi-byte characters)
- Trims leading and trailing whitespace

#### 2. URI Encoding (`wasmSanitizeURI`)
- Encodes special characters for URIs:
  - Space → `%20`
  - `[` → `%5B`
  - `]` → `%5D`
  - `\` → `%5C`
- Preserves UTF-8 characters unchanged

#### 3. Template Interpolation (`wasmInterpolate`)
- Replaces `${variable}` placeholders with values
- Supports arbitrary number of variables
- Variables stored as length-prefixed key-value pairs
- Missing variables left as placeholders

#### 4. XML Attribute Extraction (`wasmExtractAttribute`)
- Simple attribute value extraction
- Finds `name="value"` patterns
- Returns the value without quotes

## JavaScript Integration

### Basic Usage
```javascript
// Load WASM module
const response = await fetch('mapping-engine.wasm');
const wasmModule = await WebAssembly.compile(await response.arrayBuffer());
const wasmInstance = await WebAssembly.instantiate(wasmModule);

// String sanitization
const input = stringToWasm("  Hello   World  ");
const resultLen = wasmInstance.exports.wasmSanitize(input.ptr, input.len);
const result = wasmToString(resultPtr, resultLen); // "Hello World"
```

### Variable Format for Interpolation
```javascript
function writeVariables(vars) {
    const encoder = new TextEncoder();
    const tempBuffer = new Uint8Array(memory.buffer, wasmInstance.exports.wasmGetTempBuffer(), 65536);
    let offset = 0;
    
    for (const [key, value] of Object.entries(vars)) {
        // Write key length (4 bytes, little endian)
        new DataView(tempBuffer.buffer, tempBuffer.byteOffset + offset, 4).setUint32(0, key.length, true);
        offset += 4;
        
        // Write key and value with lengths
        tempBuffer.set(encoder.encode(key), offset);
        offset += key.length;
        // ... similar for value
    }
    
    return { offset: 0, count: Object.keys(vars).length };
}
```

## Go Integration

```go
engine, err := NewMappingEngine("mapping-engine.wasm")
result, err := engine.Sanitize("  Hello World  ")
interpolated, err := engine.Interpolate("${greeting}, ${name}!", map[string]string{
    "greeting": "Hello",
    "name": "World",
})
```

## UTF-8 Support

The implementation fully supports UTF-8:
- ✅ European languages (é, ö, ñ)
- ✅ CJK characters (中文, 日本語, 한국어)
- ✅ Arabic/RTL scripts (العربية)
- ✅ Emoji (🎨, 🌻)

All operations work at the byte level, preserving UTF-8 encoding throughout.

## Building

```bash
zig build wasm
```

This produces `zig-out/wasm/mapping-engine.wasm` (2.08KB).

## Version

Current version: `0.3.0` - Stable implementation with full UTF-8 support and robust memory management.