const std = @import("std");
const StringFunctions = @import("string_functions.zig").StringFunctions;
const XmlParser = @import("xml_parser.zig").XmlParser;
const XmlNode = @import("xml_parser.zig").XmlNode;
const XmlBuilder = @import("xml_builder.zig").XmlBuilder;
const Attribute = @import("xml_builder.zig").Attribute;
const MappingProcessor = @import("mapping_processor.zig").MappingProcessor;
const MappingRule = @import("mapping_processor.zig").MappingRule;
const MappingType = @import("mapping_processor.zig").MappingType;

// Minimal WASM-compatible mapping engine with proper memory handling
// This version includes XML parsing and generation

// Fixed-size allocator for WASM
const WASM_HEAP_SIZE = 1024 * 1024 * 2; // 2MB
var wasm_heap: [WASM_HEAP_SIZE]u8 = undefined;
var fba = std.heap.FixedBufferAllocator.init(&wasm_heap);
const allocator = fba.allocator();

// String functions instance
var string_functions: ?StringFunctions = null;

// String buffers
const MAX_STRING_SIZE = 1024 * 64; // 64KB
var result_buffer: [MAX_STRING_SIZE]u8 = undefined;
var temp_buffer: [MAX_STRING_SIZE]u8 = undefined;

// Clear result buffer
fn clearResultBuffer() void {
    @memset(&result_buffer, 0);
}

// Memory management exports
export fn wasmGetResultBuffer() [*]const u8 {
    return &result_buffer;
}

export fn wasmGetTempBuffer() [*]const u8 {
    return &temp_buffer;
}

// Write string to specific offset in temp buffer
export fn wasmWriteString(str_ptr: [*]const u8, str_len: u32, offset: u32) u32 {
    const str = str_ptr[0..str_len];
    if (offset + str_len > MAX_STRING_SIZE) return 0;
    
    @memcpy(temp_buffer[offset..][0..str_len], str);
    return offset + str_len;
}

// String sanitization with proper UTF-8 handling
export fn wasmSanitize(input_ptr: [*]const u8, input_len: u32) u32 {
    clearResultBuffer();
    const input = input_ptr[0..input_len];
    var output_idx: usize = 0;
    var prev_space = false;
    var is_leading = true;
    
    var i: usize = 0;
    while (i < input.len) {
        const byte = input[i];
        
        // Check if it's a space or whitespace (ASCII only)
        const is_space = byte == ' ' or byte == '\t' or byte == '\n' or byte == '\r';
        
        if (is_space) {
            if (!is_leading and !prev_space and output_idx < MAX_STRING_SIZE - 1) {
                result_buffer[output_idx] = ' ';
                output_idx += 1;
                prev_space = true;
            }
            i += 1;
        } else {
            // Copy the byte (handles UTF-8 properly)
            if (output_idx < MAX_STRING_SIZE - 1) {
                result_buffer[output_idx] = byte;
                output_idx += 1;
                prev_space = false;
                is_leading = false;
            }
            i += 1;
        }
    }
    
    // Trim trailing space
    if (output_idx > 0 and result_buffer[output_idx - 1] == ' ') {
        output_idx -= 1;
    }
    
    return @as(u32, @intCast(output_idx));
}

// URI encoding with UTF-8 safety
export fn wasmSanitizeURI(input_ptr: [*]const u8, input_len: u32) u32 {
    clearResultBuffer();
    const input = input_ptr[0..input_len];
    var output_idx: usize = 0;
    
    for (input) |byte| {
        if (output_idx >= MAX_STRING_SIZE - 3) break;
        
        switch (byte) {
            ' ' => {
                result_buffer[output_idx] = '%';
                result_buffer[output_idx + 1] = '2';
                result_buffer[output_idx + 2] = '0';
                output_idx += 3;
            },
            '[' => {
                result_buffer[output_idx] = '%';
                result_buffer[output_idx + 1] = '5';
                result_buffer[output_idx + 2] = 'B';
                output_idx += 3;
            },
            ']' => {
                result_buffer[output_idx] = '%';
                result_buffer[output_idx + 1] = '5';
                result_buffer[output_idx + 2] = 'D';
                output_idx += 3;
            },
            '\\' => {
                result_buffer[output_idx] = '%';
                result_buffer[output_idx + 1] = '5';
                result_buffer[output_idx + 2] = 'C';
                output_idx += 3;
            },
            else => {
                result_buffer[output_idx] = byte;
                output_idx += 1;
            },
        }
    }
    
    return @as(u32, @intCast(output_idx));
}

// Improved string interpolation with proper bounds checking
export fn wasmInterpolate(
    template_ptr: [*]const u8,
    template_len: u32,
    vars_offset: u32,
    vars_count: u32,
) u32 {
    clearResultBuffer();
    
    if (template_len == 0 or template_len > MAX_STRING_SIZE) return 0;
    
    const template = template_ptr[0..template_len];
    var output_idx: usize = 0;
    var i: usize = 0;
    
    while (i < template.len and output_idx < MAX_STRING_SIZE - 1) {
        if (i + 2 < template.len and template[i] == '$' and template[i + 1] == '{') {
            // Find closing brace
            var j = i + 2;
            while (j < template.len and template[j] != '}') : (j += 1) {}
            
            if (j < template.len) {
                const var_name = template[i + 2 .. j];
                var found = false;
                
                // Look up variable in temp buffer
                var var_offset = vars_offset;
                var var_idx: u32 = 0;
                
                while (var_idx < vars_count and var_offset < MAX_STRING_SIZE) : (var_idx += 1) {
                    // Read key length (4 bytes)
                    if (var_offset + 4 > MAX_STRING_SIZE) break;
                    const key_len = std.mem.readInt(u32, temp_buffer[var_offset..][0..4], .little);
                    var_offset += 4;
                    
                    // Read key
                    if (var_offset + key_len > MAX_STRING_SIZE) break;
                    const key = temp_buffer[var_offset..][0..key_len];
                    var_offset += key_len;
                    
                    // Read value length (4 bytes)
                    if (var_offset + 4 > MAX_STRING_SIZE) break;
                    const val_len = std.mem.readInt(u32, temp_buffer[var_offset..][0..4], .little);
                    var_offset += 4;
                    
                    // Read value
                    if (var_offset + val_len > MAX_STRING_SIZE) break;
                    const val = temp_buffer[var_offset..][0..val_len];
                    var_offset += val_len;
                    
                    if (std.mem.eql(u8, var_name, key)) {
                        const copy_len = @min(val_len, MAX_STRING_SIZE - output_idx);
                        @memcpy(result_buffer[output_idx..][0..copy_len], val[0..copy_len]);
                        output_idx += copy_len;
                        found = true;
                        break;
                    }
                }
                
                if (!found) {
                    // Variable not found - copy the placeholder as-is
                    const placeholder_len = j - i + 1;
                    const copy_len = @min(placeholder_len, MAX_STRING_SIZE - output_idx);
                    @memcpy(result_buffer[output_idx..][0..copy_len], template[i..][0..copy_len]);
                    output_idx += copy_len;
                }
                
                i = j + 1;
                continue;
            }
        }
        
        if (output_idx < MAX_STRING_SIZE) {
            result_buffer[output_idx] = template[i];
            output_idx += 1;
        }
        i += 1;
    }
    
    return @as(u32, @intCast(output_idx));
}

// Simple XML attribute extraction
export fn wasmExtractAttribute(
    xml_ptr: [*]const u8,
    xml_len: u32,
    attr_name_ptr: [*]const u8,
    attr_name_len: u32,
) u32 {
    clearResultBuffer();
    const xml = xml_ptr[0..xml_len];
    const attr_name = attr_name_ptr[0..attr_name_len];
    
    var i: usize = 0;
    while (i < xml.len) : (i += 1) {
        if (i + attr_name.len + 2 < xml.len) {
            if (std.mem.eql(u8, xml[i..i + attr_name.len], attr_name) and
                xml[i + attr_name.len] == '=' and
                xml[i + attr_name.len + 1] == '"') {
                
                const start = i + attr_name.len + 2;
                var end = start;
                while (end < xml.len and xml[end] != '"') : (end += 1) {}
                
                if (end < xml.len and end > start) {
                    const value = xml[start..end];
                    const copy_len = @min(value.len, MAX_STRING_SIZE);
                    @memcpy(result_buffer[0..copy_len], value[0..copy_len]);
                    return @as(u32, @intCast(copy_len));
                }
            }
        }
    }
    
    return 0;
}

// Version info
export fn wasmGetVersion() u32 {
    clearResultBuffer();
    const version = "0.5.0-xml";
    @memcpy(result_buffer[0..version.len], version);
    return @as(u32, @intCast(version.len));
}

// Memory info for debugging
export fn wasmGetMemoryInfo() u32 {
    clearResultBuffer();
    const info = "heap=2MB,string=64KB,temp=64KB";
    @memcpy(result_buffer[0..info.len], info);
    return @as(u32, @intCast(info.len));
}

// Initialize string functions
fn ensureStringFunctions() void {
    if (string_functions == null) {
        fba.reset();
        string_functions = StringFunctions.init(allocator);
    }
}

// String replace function (12,628 uses in your mappings)
export fn wasmReplace(
    str_ptr: [*]const u8,
    str_len: u32,
    search_ptr: [*]const u8,
    search_len: u32,
    replace_ptr: [*]const u8,
    replace_len: u32,
) u32 {
    clearResultBuffer();
    ensureStringFunctions();
    
    const str = str_ptr[0..str_len];
    const search = search_ptr[0..search_len];
    const replacement = replace_ptr[0..replace_len];
    
    if (string_functions) |sf| {
        const result = sf.replace(str, search, replacement) catch return 0;
        defer allocator.free(result);
        
        const copy_len = @min(result.len, MAX_STRING_SIZE);
        @memcpy(result_buffer[0..copy_len], result[0..copy_len]);
        return @as(u32, @intCast(copy_len));
    }
    
    return 0;
}

// String replaceAll function (13,606 uses in your mappings)
export fn wasmReplaceAll(
    str_ptr: [*]const u8,
    str_len: u32,
    search_ptr: [*]const u8,
    search_len: u32,
    replace_ptr: [*]const u8,
    replace_len: u32,
) u32 {
    clearResultBuffer();
    ensureStringFunctions();
    
    const str = str_ptr[0..str_len];
    const search = search_ptr[0..search_len];
    const replacement = replace_ptr[0..replace_len];
    
    if (string_functions) |sf| {
        const result = sf.replaceAll(str, search, replacement) catch return 0;
        defer allocator.free(result);
        
        const copy_len = @min(result.len, MAX_STRING_SIZE);
        @memcpy(result_buffer[0..copy_len], result[0..copy_len]);
        return @as(u32, @intCast(copy_len));
    }
    
    return 0;
}

// Capitalize function (2,278 uses)
export fn wasmCapitalize(str_ptr: [*]const u8, str_len: u32) u32 {
    clearResultBuffer();
    ensureStringFunctions();
    
    const str = str_ptr[0..str_len];
    
    if (string_functions) |sf| {
        const result = sf.capitalize(str) catch return 0;
        defer allocator.free(result);
        
        const copy_len = @min(result.len, MAX_STRING_SIZE);
        @memcpy(result_buffer[0..copy_len], result[0..copy_len]);
        return @as(u32, @intCast(copy_len));
    }
    
    return 0;
}

// Split function (1,707 uses)
export fn wasmSplit(
    str_ptr: [*]const u8,
    str_len: u32,
    delimiter_ptr: [*]const u8,
    delimiter_len: u32,
) u32 {
    clearResultBuffer();
    ensureStringFunctions();
    
    const str = str_ptr[0..str_len];
    const delimiter = delimiter_ptr[0..delimiter_len];
    
    if (string_functions) |sf| {
        const parts = sf.split(str, delimiter) catch return 0;
        defer {
            for (parts) |part| allocator.free(part);
            allocator.free(parts);
        }
        
        // Join parts with null bytes as separator
        var offset: usize = 0;
        for (parts) |part| {
            if (offset + part.len + 1 > MAX_STRING_SIZE) break;
            
            @memcpy(result_buffer[offset..][0..part.len], part);
            offset += part.len;
            result_buffer[offset] = 0; // null separator
            offset += 1;
        }
        
        // Store count at the beginning
        if (offset > 0) {
            offset -= 1; // Remove last null
        }
        
        return @as(u32, @intCast(offset));
    }
    
    return 0;
}

// Trim function
export fn wasmTrim(str_ptr: [*]const u8, str_len: u32) u32 {
    clearResultBuffer();
    ensureStringFunctions();
    
    const str = str_ptr[0..str_len];
    
    if (string_functions) |sf| {
        const result = sf.trim(str) catch return 0;
        defer allocator.free(result);
        
        const copy_len = @min(result.len, MAX_STRING_SIZE);
        @memcpy(result_buffer[0..copy_len], result[0..copy_len]);
        return @as(u32, @intCast(copy_len));
    }
    
    return 0;
}

// toLowerCase function
export fn wasmToLowerCase(str_ptr: [*]const u8, str_len: u32) u32 {
    clearResultBuffer();
    ensureStringFunctions();
    
    const str = str_ptr[0..str_len];
    
    if (string_functions) |sf| {
        const result = sf.toLowerCase(str) catch return 0;
        defer allocator.free(result);
        
        const copy_len = @min(result.len, MAX_STRING_SIZE);
        @memcpy(result_buffer[0..copy_len], result[0..copy_len]);
        return @as(u32, @intCast(copy_len));
    }
    
    return 0;
}

// toUpperCase function
export fn wasmToUpperCase(str_ptr: [*]const u8, str_len: u32) u32 {
    clearResultBuffer();
    ensureStringFunctions();
    
    const str = str_ptr[0..str_len];
    
    if (string_functions) |sf| {
        const result = sf.toUpperCase(str) catch return 0;
        defer allocator.free(result);
        
        const copy_len = @min(result.len, MAX_STRING_SIZE);
        @memcpy(result_buffer[0..copy_len], result[0..copy_len]);
        return @as(u32, @intCast(copy_len));
    }
    
    return 0;
}

// XML parsing function
export fn wasmParseXml(xml_ptr: [*]const u8, xml_len: u32) u32 {
    clearResultBuffer();
    
    const xml = xml_ptr[0..xml_len];
    const parser = XmlParser.init(allocator);
    
    const root = parser.parse(xml) catch |err| {
        const err_msg = switch (err) {
            error.InvalidXml => "Invalid XML",
            error.OutOfMemory => "Out of memory",
            error.InvalidAttribute => "Invalid attribute",
            error.MismatchedTags => "Mismatched tags",
            else => "Unknown error",
        };
        @memcpy(result_buffer[0..err_msg.len], err_msg);
        return @as(u32, @intCast(err_msg.len));
    };
    defer {
        root.deinit();
        allocator.destroy(root);
    }
    
    // For now, return success message
    const msg = "XML parsed successfully";
    @memcpy(result_buffer[0..msg.len], msg);
    return @as(u32, @intCast(msg.len));
}

// Navigate XML and get value
export fn wasmXmlGet(
    xml_ptr: [*]const u8,
    xml_len: u32,
    path_ptr: [*]const u8,
    path_len: u32,
) u32 {
    clearResultBuffer();
    
    const xml = xml_ptr[0..xml_len];
    const path = path_ptr[0..path_len];
    
    const parser = XmlParser.init(allocator);
    const root = parser.parse(xml) catch return 0;
    defer {
        root.deinit();
        allocator.destroy(root);
    }
    
    // Navigate path
    var result = root.get(path);
    defer result.deinit();
    
    const text = result.asText();
    const copy_len = @min(text.len, MAX_STRING_SIZE);
    @memcpy(result_buffer[0..copy_len], text[0..copy_len]);
    
    return @as(u32, @intCast(copy_len));
}

// Build XML element
export fn wasmBuildXml() u32 {
    clearResultBuffer();
    
    var builder = XmlBuilder.init(allocator);
    defer builder.deinit();
    
    builder.startDocument() catch return 0;
    builder.writeEdmNamespaces() catch return 0;
    
    // Add sample content
    const cho_attrs = [_]Attribute{
        .{ .name = "rdf:about", .value = "http://example.org/cho/123" },
    };
    builder.startElement("edm:ProvidedCHO", &cho_attrs) catch return 0;
    builder.writeElement("dc:title", "Sample Title", null) catch return 0;
    builder.endElement("edm:ProvidedCHO") catch return 0;
    
    builder.endElement("edm:RDF") catch return 0;
    
    const xml = builder.toString() catch return 0;
    defer allocator.free(xml);
    
    const copy_len = @min(xml.len, MAX_STRING_SIZE);
    @memcpy(result_buffer[0..copy_len], xml[0..copy_len]);
    
    return @as(u32, @intCast(copy_len));
}

// Full mapping processor
var mapping_processor: ?MappingProcessor = null;

export fn wasmInitProcessor() void {
    fba.reset();
    mapping_processor = MappingProcessor.init(allocator);
    global_processor = mapping_processor;
}

export fn wasmAddFact(
    key_ptr: [*]const u8,
    key_len: u32,
    value_ptr: [*]const u8,
    value_len: u32,
) u32 {
    if (mapping_processor) |*processor| {
        const key = key_ptr[0..key_len];
        const value = value_ptr[0..value_len];
        
        processor.addFact(key, value) catch return 0;
        return 1;
    }
    return 0;
}

// Global processor instance for reuse
var global_processor: ?MappingProcessor = null;

export fn wasmAddMappingRule(
    input_path_ptr: [*]const u8,
    input_path_len: u32,
    output_path_ptr: [*]const u8,
    output_path_len: u32,
    rule_type: u32,
    constant_value_ptr: [*]const u8,
    constant_value_len: u32
) u32 {
    const input_path = if (input_path_len > 0) 
        input_path_ptr[0..input_path_len]
    else 
        null;
    const output_path = output_path_ptr[0..output_path_len];
    
    const mapping_type: MappingType = switch (rule_type) {
        0 => .direct,
        1 => .groovy_code,
        2 => .constant,
        3 => .concatenate,
        else => return 0,
    };
    
    const constant_value = if (constant_value_len > 0 and mapping_type == .constant)
        allocator.dupe(u8, constant_value_ptr[0..constant_value_len]) catch return 0
    else
        null;
    
    const rule = MappingRule{
        .input_path = if (input_path) |path| 
            allocator.dupe(u8, path) catch return 0
        else 
            null,
        .output_path = allocator.dupe(u8, output_path) catch return 0,
        .mapping_type = mapping_type,
        .groovy_code = null,
        .constant_value = constant_value,
    };
    
    if (global_processor) |*processor| {
        processor.addRule(rule) catch return 0;
        return 1;
    }
    
    return 0;
}

export fn wasmProcessMapping(
    xml_ptr: [*]const u8,
    xml_len: u32,
) u32 {
    clearResultBuffer();
    
    if (mapping_processor) |*processor| {
        const xml = xml_ptr[0..xml_len];
        
        const output = processor.process(xml) catch return 0;
        defer allocator.free(output);
        
        const copy_len = @min(output.len, MAX_STRING_SIZE);
        @memcpy(result_buffer[0..copy_len], output[0..copy_len]);
        
        return @as(u32, @intCast(copy_len));
    }
    
    return 0;
}