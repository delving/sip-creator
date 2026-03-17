const std = @import("std");
const XmlParser = @import("xml_parser.zig").XmlParser;
const MappingProcessor = @import("mapping_processor.zig").MappingProcessor;
const MappingRule = @import("mapping_processor.zig").MappingRule;
const MappingType = @import("mapping_processor.zig").MappingType;

// Test the transformation from input.xml to output.xml
pub fn main() !void {
    var gpa = std.heap.GeneralPurposeAllocator(.{}){};
    defer _ = gpa.deinit();
    const allocator = gpa.allocator();
    
    // Read input XML
    const input_xml = try std.fs.cwd().readFileAlloc(allocator, "../_data/input.xml", 1024 * 1024);
    defer allocator.free(input_xml);
    
    // Read expected output XML
    const expected_output = try std.fs.cwd().readFileAlloc(allocator, "../_data/output.xml", 1024 * 1024);
    defer allocator.free(expected_output);
    
    // Create processor
    var processor = MappingProcessor.init(allocator);
    defer processor.deinit();
    
    // Add facts from mapping_edm.xml
    try processor.addFact("baseUrl", "http://data.collectienederland.nl");
    try processor.addFact("spec", "van-gogh-museum");
    try processor.addFact("dataProvider", "Van Gogh Museum");
    try processor.addFact("provider", "Rijksdienst voor het Cultureel Erfgoed");
    try processor.addFact("language", "nl");
    try processor.addFact("_uniqueIdentifier", "p0016V1962");
    
    std.debug.print("\n=== Testing LIDO to EDM Transformation ===\n", .{});
    std.debug.print("Input file: _data/input.xml\n", .{});
    std.debug.print("Expected output: _data/output.xml\n\n", .{});
    
    // Test individual mappings
    try testDirectMappings(allocator, input_xml);
    
    // Add simplified mapping rules (without collection operations for now)
    const rules = [_]MappingRule{
        // Direct mappings that should work
        .{
            .input_path = try allocator.dupe(u8, "/pocket/lido:lido/lido:descriptiveMetadata/lido:objectIdentificationWrap/lido:titleWrap/lido:titleSet/lido:appellationValue"),
            .output_path = try allocator.dupe(u8, "/edm:RDF/edm:ProvidedCHO/dc:title"),
            .mapping_type = .direct,
            .groovy_code = null,
            .constant_value = null,
        },
        .{
            .input_path = try allocator.dupe(u8, "/pocket/lido:lido/lido:descriptiveMetadata/lido:eventWrap/lido:eventSet/lido:event/lido:eventDate/lido:displayDate"),
            .output_path = try allocator.dupe(u8, "/edm:RDF/edm:ProvidedCHO/dc:date"),
            .mapping_type = .direct,
            .groovy_code = null,
            .constant_value = null,
        },
        .{
            .input_path = try allocator.dupe(u8, "/pocket/lido:lido/lido:descriptiveMetadata/lido:objectIdentificationWrap/lido:repositoryWrap/lido:repositorySet/lido:workID"),
            .output_path = try allocator.dupe(u8, "/edm:RDF/edm:ProvidedCHO/dc:identifier"),
            .mapping_type = .direct,
            .groovy_code = null,
            .constant_value = null,
        },
        .{
            .input_path = try allocator.dupe(u8, "/pocket/lido:lido/lido:descriptiveMetadata/lido:objectIdentificationWrap/lido:objectMeasurementsWrap/lido:objectMeasurementsSet/lido:displayObjectMeasurements"),
            .output_path = try allocator.dupe(u8, "/edm:RDF/edm:ProvidedCHO/dcterms:extent"),
            .mapping_type = .direct,
            .groovy_code = null,
            .constant_value = null,
        },
    };
    
    for (rules) |rule| {
        try processor.addRule(rule);
    }
    
    // Process the mapping
    const output = try processor.process(input_xml);
    defer allocator.free(output);
    
    std.debug.print("\n=== Generated Output ===\n{s}\n", .{output});
    
    // Compare with expected output
    std.debug.print("\n=== Comparison Results ===\n", .{});
    try compareOutputs(allocator, output, expected_output);
}

fn testDirectMappings(allocator: std.mem.Allocator, xml: []const u8) !void {
    const parser = XmlParser.init(allocator);
    const root = try parser.parse(xml);
    defer {
        root.deinit();
        allocator.destroy(root);
    }
    
    std.debug.print("=== Testing Direct Path Extractions ===\n", .{});
    
    const test_paths = [_]struct { path: []const u8, name: []const u8 }{
        .{ .path = "@id", .name = "Record ID" },
        .{ .path = "lido:lido/lido:descriptiveMetadata/lido:objectIdentificationWrap/lido:titleWrap/lido:titleSet/lido:appellationValue", .name = "Title" },
        .{ .path = "lido:lido/lido:descriptiveMetadata/lido:eventWrap/lido:eventSet/lido:event/lido:eventDate/lido:displayDate", .name = "Date" },
        .{ .path = "lido:lido/lido:descriptiveMetadata/lido:objectIdentificationWrap/lido:repositoryWrap/lido:repositorySet/lido:workID", .name = "Work ID" },
    };
    
    for (test_paths) |test| {
        var result = root.get(test.path);
        defer result.deinit();
        
        const value = result.asText();
        std.debug.print("{s}: \"{s}\"\n", .{ test.name, value });
    }
    
    std.debug.print("\n", .{});
}

fn compareOutputs(allocator: std.mem.Allocator, actual: []const u8, expected: []const u8) !void {
    // Parse both XMLs
    const parser = XmlParser.init(allocator);
    
    const actual_root = try parser.parse(actual);
    defer {
        actual_root.deinit();
        allocator.destroy(actual_root);
    }
    
    const expected_root = try parser.parse(expected);
    defer {
        expected_root.deinit();
        allocator.destroy(expected_root);
    }
    
    // Check key elements
    const checks = [_]struct { path: []const u8, name: []const u8 }{
        .{ .path = "edm:ProvidedCHO/dc:title", .name = "Title" },
        .{ .path = "edm:ProvidedCHO/dc:date", .name = "Date" },
        .{ .path = "edm:ProvidedCHO/dc:identifier", .name = "Identifier" },
        .{ .path = "edm:ProvidedCHO/dcterms:extent", .name = "Extent" },
        .{ .path = "ore:Aggregation/edm:dataProvider", .name = "Data Provider" },
    };
    
    var passed: usize = 0;
    var failed: usize = 0;
    
    for (checks) |check| {
        var actual_result = actual_root.get(check.path);
        defer actual_result.deinit();
        var expected_result = expected_root.get(check.path);
        defer expected_result.deinit();
        
        const actual_value = actual_result.asText();
        const expected_value = expected_result.asText();
        
        if (std.mem.eql(u8, actual_value, expected_value)) {
            std.debug.print("✓ {s}: \"{s}\"\n", .{ check.name, actual_value });
            passed += 1;
        } else {
            std.debug.print("✗ {s}: expected \"{s}\", got \"{s}\"\n", .{ check.name, expected_value, actual_value });
            failed += 1;
        }
    }
    
    std.debug.print("\nPassed: {d}/{d}\n", .{ passed, passed + failed });
    
    // List missing features
    std.debug.print("\n=== Missing Features for Full Compatibility ===\n", .{});
    std.debug.print("1. Collection operations (* operator) - used for multiple actors\n", .{});
    std.debug.print("2. Complex Groovy code execution\n", .{});
    std.debug.print("3. Dynamic URI generation with string interpolation\n", .{});
    std.debug.print("4. Conditional logic (if/else in Groovy)\n", .{});
    std.debug.print("5. Method calls like getValueNodes()\n", .{});
    std.debug.print("6. Multiple output nodes from single input (e.g., multiple dc:creator)\n", .{});
}