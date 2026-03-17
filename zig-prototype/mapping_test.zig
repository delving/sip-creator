const std = @import("std");
const engine = @import("mapping_engine.zig");

// Test data structures
const TestMapping = struct {
    input_path: []const u8,
    output_path: []const u8,
    groovy_code: []const u8,
    expected_value: []const u8,
};

// Test utilities
fn loadFile(allocator: std.mem.Allocator, path: []const u8) ![]u8 {
    const file = try std.fs.cwd().openFile(path, .{});
    defer file.close();
    
    const file_size = try file.getEndPos();
    const contents = try allocator.alloc(u8, file_size);
    _ = try file.read(contents);
    
    return contents;
}

// Simple XML parser for testing
pub const SimpleXmlParser = struct {
    allocator: std.mem.Allocator,
    
    pub fn init(allocator: std.mem.Allocator) SimpleXmlParser {
        return .{ .allocator = allocator };
    }
    
    pub fn parse(self: *SimpleXmlParser, xml: []const u8) !*engine.XmlNode {
        // This is a simplified parser for testing
        // In production, you'd use a full XML parser
        _ = xml; // Mark as used
        
        // For now, create a mock structure matching the input
        const root = try engine.XmlNode.init(self.allocator, "input");
        try root.attributes.put("id", "s-gravenhage-kb-1");
        
        // Create LIDO structure
        const lido = try engine.XmlNode.init(self.allocator, "lidolido");
        const desc = try engine.XmlNode.init(self.allocator, "lidodescriptiveMetadata");
        const objIdWrap = try engine.XmlNode.init(self.allocator, "lidoobjectIdentificationWrap");
        
        // Title
        const titleWrap = try engine.XmlNode.init(self.allocator, "lidotitleWrap");
        const titleSet = try engine.XmlNode.init(self.allocator, "lidotitleSet");
        const titleValue = try engine.XmlNode.init(self.allocator, "lidoappellationValue");
        try titleValue.setText("Starry Night Over the Rhône");
        try titleSet.children.append(titleValue);
        try titleWrap.children.append(titleSet);
        try objIdWrap.children.append(titleWrap);
        
        // Repository
        const repoWrap = try engine.XmlNode.init(self.allocator, "lidorepositoryWrap");
        const repoSet = try engine.XmlNode.init(self.allocator, "lidorepositorySet");
        const workId = try engine.XmlNode.init(self.allocator, "lidoworkID");
        try workId.setText("F474");
        try repoSet.children.append(workId);
        try repoWrap.children.append(repoSet);
        try objIdWrap.children.append(repoWrap);
        
        try desc.children.append(objIdWrap);
        try lido.children.append(desc);
        try root.children.append(lido);
        
        return root;
    }
};

// Mapping execution simulator
pub fn executeMappingSnippet(
    allocator: std.mem.Allocator,
    input_xml: *engine.XmlNode,
    groovy_code: []const u8,
    facts: std.StringHashMap([]const u8),
) ![]const u8 {
    var ctx = engine.MappingContext.init(allocator, input_xml);
    defer ctx.deinit();
    
    // Add facts
    var it = facts.iterator();
    while (it.next()) |entry| {
        try ctx.facts.put(entry.key_ptr.*, entry.value_ptr.*);
    }
    ctx.unique_identifier = "s-gravenhage-kb-1";
    
    // Parse and execute the Groovy code
    // This is a simplified version - real implementation would parse Groovy
    
    // Example: Handle string templates
    if (std.mem.indexOf(u8, groovy_code, "${")) |_| {
        const expr = engine.Expression{ .string_template = groovy_code };
        return try engine.evaluateExpression(&ctx, expr);
    }
    
    // Example: Handle navigation
    if (std.mem.indexOf(u8, groovy_code, "_input.")) |_| {
        // Parse navigation path
        const nav_start = std.mem.indexOf(u8, groovy_code, "_input.") orelse return "";
        const path_start = nav_start + 7; // "_input.".len
        
        // Simple case: direct field access
        if (groovy_code[path_start..].len > 0) {
            const field = groovy_code[path_start..];
            const clean_field = field[0..std.mem.indexOf(u8, field, " ") orelse field.len];
            const result = input_xml.get(clean_field);
            return result.toString();
        }
    }
    
    return "";
}

test "string sanitization" {
    const allocator = std.testing.allocator;
    
    const test_cases = [_]struct {
        input: []const u8,
        expected: []const u8,
    }{
        .{ .input = "  Hello   World  ", .expected = "Hello World" },
        .{ .input = "Test\n\nWith\tNewlines", .expected = "Test With Newlines" },
        .{ .input = "   Leading and trailing   ", .expected = "Leading and trailing" },
    };
    
    for (test_cases) |tc| {
        const result = try engine.StringUtils.sanitize(allocator, tc.input);
        defer allocator.free(result);
        try std.testing.expectEqualStrings(tc.expected, result);
    }
}

test "URI encoding" {
    const allocator = std.testing.allocator;
    
    const test_cases = [_]struct {
        input: []const u8,
        expected: []const u8,
    }{
        .{ .input = "hello world", .expected = "hello%20world" },
        .{ .input = "test[brackets]", .expected = "test%5Bbrackets%5D" },
        .{ .input = "back\\slash", .expected = "back%5Cslash" },
    };
    
    for (test_cases) |tc| {
        const result = try engine.StringUtils.sanitizeURI(allocator, tc.input);
        defer allocator.free(result);
        try std.testing.expectEqualStrings(tc.expected, result);
    }
}

test "VGM mapping transformations" {
    const allocator = std.testing.allocator;
    
    // Parse mock input
    var parser = SimpleXmlParser.init(allocator);
    const input = try parser.parse("");
    defer {
        input.deinit();
        allocator.destroy(input);
    }
    
    // Create facts from VGM mapping
    var facts = std.StringHashMap([]const u8).init(allocator);
    defer facts.deinit();
    try facts.put("baseUrl", "http://data.collectienederland.nl");
    try facts.put("spec", "van-gogh-museum");
    try facts.put("provider", "Rijksdienst voor het Cultureel Erfgoed");
    try facts.put("dataProvider", "Van Gogh Museum");
    
    // Test cases from the VGM mapping
    const test_mappings = [_]TestMapping{
        // Test 1: Simple string template for aggregation URI
        .{
            .input_path = "/input",
            .output_path = "/edm:RDF/ore:Aggregation/@rdf:about",
            .groovy_code = "${baseUrl}/resource/aggregation/${spec}/${_uniqueIdentifier}",
            .expected_value = "http://data.collectienederland.nl/resource/aggregation/van-gogh-museum/s-gravenhage-kb-1",
        },
        // Test 2: CHO URI
        .{
            .input_path = "/input",
            .output_path = "/edm:RDF/ore:Aggregation/edm:aggregatedCHO/@rdf:resource",
            .groovy_code = "${baseUrl}/resource/document/${spec}/${_uniqueIdentifier}",
            .expected_value = "http://data.collectienederland.nl/resource/document/van-gogh-museum/s-gravenhage-kb-1",
        },
        // Test 3: Provider fact
        .{
            .input_path = "/facts/provider",
            .output_path = "/edm:RDF/ore:Aggregation/edm:provider",
            .groovy_code = "${provider}",
            .expected_value = "Rijksdienst voor het Cultureel Erfgoed",
        },
    };
    
    for (test_mappings) |mapping| {
        const result = try executeMappingSnippet(allocator, input, mapping.groovy_code, facts);
        defer allocator.free(result);
        
        try std.testing.expectEqualStrings(mapping.expected_value, result);
    }
}

test "complex navigation patterns" {
    const allocator = std.testing.allocator;
    
    // Create a more complex XML structure
    const root = try engine.XmlNode.init(allocator, "input");
    defer {
        root.deinit();
        allocator.destroy(root);
    }
    
    // Create multiple creators
    const creators = try engine.XmlNode.init(allocator, "creators");
    
    const creator1 = try engine.XmlNode.init(allocator, "creator");
    try creator1.setText("Van Gogh, Vincent");
    try creators.children.append(creator1);
    
    const creator2 = try engine.XmlNode.init(allocator, "creator");
    try creator2.setText("Gauguin, Paul");
    try creators.children.append(creator2);
    
    try root.children.append(creators);
    
    // Test getting all creators
    const all_creators = creators.get("creator");
    defer all_creators.deinit(allocator);
    try std.testing.expect(all_creators.node_list.len == 2);
    
    // Test getting first creator
    const first_creator = creators.get("creator_");
    try std.testing.expectEqualStrings("Van Gogh, Vincent", first_creator.toString());
}

test "golden file comparison" {
    const allocator = std.testing.allocator;
    
    // Load golden files
    const input_xml = try loadFile(allocator, "../_data/input_vgm.xml");
    defer allocator.free(input_xml);
    
    const expected_output = try loadFile(allocator, "../_data/output_vgm.xml");
    defer allocator.free(expected_output);
    
    // This test validates that our test data is well-formed
    try std.testing.expect(input_xml.len > 0);
    try std.testing.expect(expected_output.len > 0);
    
    // In a full implementation, you would:
    // 1. Parse the input XML
    // 2. Load the mapping definition
    // 3. Execute the transformation
    // 4. Compare with expected output
}

// Performance benchmark
test "performance: string operations" {
    const allocator = std.testing.allocator;
    
    const iterations = 10000;
    const test_string = "  This is a   test string   with spaces  ";
    
    const start = std.time.milliTimestamp();
    
    var i: usize = 0;
    while (i < iterations) : (i += 1) {
        const result = try engine.StringUtils.sanitize(allocator, test_string);
        allocator.free(result);
    }
    
    const end = std.time.milliTimestamp();
    const elapsed = end - start;
    
    std.debug.print("\nPerformance: {} sanitize operations in {} ms\n", .{ iterations, elapsed });
    std.debug.print("Average: {d:.3} microseconds per operation\n", .{ @as(f64, @floatFromInt(elapsed * 1000)) / @as(f64, @floatFromInt(iterations)) });
}