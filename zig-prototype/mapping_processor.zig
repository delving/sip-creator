const std = @import("std");
const Allocator = std.mem.Allocator;
const XmlParser = @import("xml_parser.zig").XmlParser;
const XmlNode = @import("xml_parser.zig").XmlNode;
const XmlBuilder = @import("xml_builder.zig").XmlBuilder;
const Attribute = @import("xml_builder.zig").Attribute;
const StringFunctions = @import("string_functions.zig").StringFunctions;
const InterpolationEngine = @import("string_interpolation.zig").InterpolationEngine;
const Value = @import("string_interpolation.zig").Value;

/// Mapping rule types
pub const MappingType = enum {
    direct,          // Simple path to path mapping
    groovy_code,     // Groovy code snippet
    constant,        // Constant value
    concatenate,     // Concatenate multiple values
};

/// Single mapping rule
pub const MappingRule = struct {
    input_path: ?[]const u8,
    output_path: []const u8,
    mapping_type: MappingType,
    groovy_code: ?[]const u8,
    constant_value: ?[]const u8,
    
    pub fn deinit(self: *MappingRule, allocator: Allocator) void {
        if (self.input_path) |path| allocator.free(path);
        allocator.free(self.output_path);
        if (self.groovy_code) |code| allocator.free(code);
        if (self.constant_value) |val| allocator.free(val);
    }
};

/// Facts/variables available to all mappings
pub const Facts = struct {
    values: std.StringHashMap([]const u8),
    
    pub fn init(allocator: Allocator) Facts {
        return .{
            .values = std.StringHashMap([]const u8).init(allocator),
        };
    }
    
    pub fn deinit(self: *Facts) void {
        var iter = self.values.iterator();
        while (iter.next()) |entry| {
            self.values.allocator.free(entry.key_ptr.*);
            self.values.allocator.free(entry.value_ptr.*);
        }
        self.values.deinit();
    }
    
    pub fn put(self: *Facts, key: []const u8, value: []const u8) !void {
        const k = try self.values.allocator.dupe(u8, key);
        const v = try self.values.allocator.dupe(u8, value);
        try self.values.put(k, v);
    }
};

/// Main mapping processor
pub const MappingProcessor = struct {
    allocator: Allocator,
    parser: XmlParser,
    string_functions: StringFunctions,
    interpolation: *InterpolationEngine,
    facts: Facts,
    rules: std.ArrayList(MappingRule),
    
    pub fn init(allocator: Allocator) MappingProcessor {
        const interp = allocator.create(InterpolationEngine) catch unreachable;
        interp.* = InterpolationEngine.init(allocator);
        
        return .{
            .allocator = allocator,
            .parser = XmlParser.init(allocator),
            .string_functions = StringFunctions.init(allocator),
            .interpolation = interp,
            .facts = Facts.init(allocator),
            .rules = std.ArrayList(MappingRule).init(allocator),
        };
    }
    
    pub fn deinit(self: *MappingProcessor) void {
        for (self.rules.items) |*rule| {
            rule.deinit(self.allocator);
        }
        self.rules.deinit();
        self.facts.deinit();
        self.interpolation.deinit();
        self.allocator.destroy(self.interpolation);
    }
    
    /// Add a fact/variable
    pub fn addFact(self: *MappingProcessor, key: []const u8, value: []const u8) !void {
        try self.facts.put(key, value);
        try self.interpolation.setVariable(key, Value{ .string = try self.allocator.dupe(u8, value) });
    }
    
    /// Add a mapping rule
    pub fn addRule(self: *MappingProcessor, rule: MappingRule) !void {
        try self.rules.append(rule);
    }
    
    /// Process input XML and generate output
    pub fn process(self: *MappingProcessor, input_xml: []const u8) ![]u8 {
        // Parse input XML
        const input_root = try self.parser.parse(input_xml);
        defer {
            input_root.deinit();
            self.allocator.destroy(input_root);
        }
        
        // Set input node for interpolation
        try self.interpolation.setNode("_input", input_root);
        
        // Create output builder
        var builder = XmlBuilder.init(self.allocator);
        defer builder.deinit();
        
        try builder.startDocument();
        try builder.writeEdmNamespaces();
        
        // Process mapping rules
        try self.processRules(input_root, &builder);
        
        try builder.endElement("edm:RDF");
        
        return try builder.toString();
    }
    
    /// Process all mapping rules
    fn processRules(self: *MappingProcessor, input: *XmlNode, builder: *XmlBuilder) !void {
        // Group rules by output element
        var provided_cho_started = false;
        var aggregation_started = false;
        
        for (self.rules.items) |rule| {
            // Determine which section this rule belongs to
            const is_cho = std.mem.startsWith(u8, rule.output_path, "/edm:RDF/edm:ProvidedCHO");
            const is_agg = std.mem.startsWith(u8, rule.output_path, "/edm:RDF/ore:Aggregation");
            
            // Start elements as needed
            if (is_cho and !provided_cho_started) {
                const about = try self.generateUri("cho");
                const attrs = [_]Attribute{
                    .{ .name = "rdf:about", .value = about },
                };
                try builder.startElement("edm:ProvidedCHO", &attrs);
                provided_cho_started = true;
            } else if (is_agg and !aggregation_started) {
                if (provided_cho_started) {
                    try builder.endElement("edm:ProvidedCHO");
                    provided_cho_started = false;
                }
                
                const about = try self.generateUri("aggregation");
                const attrs = [_]Attribute{
                    .{ .name = "rdf:about", .value = about },
                };
                try builder.startElement("ore:Aggregation", &attrs);
                aggregation_started = true;
            }
            
            // Process the rule
            try self.processRule(input, rule, builder);
        }
        
        // Close any open elements
        if (provided_cho_started) {
            try builder.endElement("edm:ProvidedCHO");
        }
        if (aggregation_started) {
            try builder.endElement("ore:Aggregation");
        }
    }
    
    /// Process a single mapping rule
    fn processRule(self: *MappingProcessor, input: *XmlNode, rule: MappingRule, builder: *XmlBuilder) !void {
        const value = switch (rule.mapping_type) {
            .direct => try self.extractDirectValue(input, rule.input_path.?),
            .groovy_code => try self.executeGroovyCode(rule.groovy_code.?),
            .constant => rule.constant_value.?,
            .concatenate => try self.concatenateValues(input, rule),
        };
        defer if (rule.mapping_type != .constant) self.allocator.free(value);
        
        if (value.len == 0) return;
        
        // Extract element name from path
        const element_name = self.getElementName(rule.output_path);
        
        // Check if this is an attribute
        if (std.mem.indexOf(u8, element_name, "@")) |_| {
            // Handle attribute - this would need special handling in builder
            // For now, skip attributes
            return;
        }
        
        // Write element
        try builder.writeElement(element_name, value, null);
    }
    
    /// Extract value using direct path
    fn extractDirectValue(self: *MappingProcessor, input: *XmlNode, path: []const u8) ![]u8 {
        // Handle absolute paths starting with /
        var current = input;
        var segments = std.mem.tokenizeAny(u8, path, "/");
        
        // Skip first segment if it's "pocket" (root element)
        if (segments.peek()) |first| {
            if (std.mem.eql(u8, first, "pocket")) {
                _ = segments.next();
            }
        }
        
        while (segments.next()) |segment| {
            if (segment.len == 0) continue;
            
            // Handle attribute access
            if (segment[0] == '@') {
                if (current.attributes.get(segment[1..])) |value| {
                    return try self.allocator.dupe(u8, value);
                }
                return try self.allocator.dupe(u8, "");
            }
            
            // Navigate to child
            var found = false;
            for (current.children.items) |child| {
                if (std.mem.eql(u8, child.name, segment)) {
                    current = child;
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                return try self.allocator.dupe(u8, "");
            }
        }
        
        return try self.allocator.dupe(u8, current.getText());
    }
    
    /// Execute Groovy code (simplified)
    fn executeGroovyCode(self: *MappingProcessor, code: []const u8) ![]u8 {
        // For now, just handle string interpolation
        return try self.interpolation.interpolate(code);
    }
    
    /// Concatenate multiple values
    fn concatenateValues(self: *MappingProcessor, input: *XmlNode, rule: MappingRule) ![]u8 {
        _ = input;
        _ = rule;
        // TODO: Implement concatenation
        return try self.allocator.dupe(u8, "");
    }
    
    /// Generate URI based on type
    fn generateUri(self: *MappingProcessor, uri_type: []const u8) ![]u8 {
        const base_url = self.facts.values.get("baseUrl") orelse "http://example.org";
        const spec = self.facts.values.get("spec") orelse "unknown";
        const id = self.facts.values.get("_uniqueIdentifier") orelse "unknown";
        
        // Sanitize ID
        const sanitized_id = self.string_functions.sanitizeURI(id);
        
        if (std.mem.eql(u8, uri_type, "cho")) {
            return try std.fmt.allocPrint(self.allocator, "{s}/resource/document/{s}/{s}", .{ base_url, spec, sanitized_id });
        } else {
            return try std.fmt.allocPrint(self.allocator, "{s}/resource/aggregation/{s}/{s}", .{ base_url, spec, sanitized_id });
        }
    }
    
    /// Extract element name from output path
    fn getElementName(self: *MappingProcessor, path: []const u8) []const u8 {
        _ = self;
        
        // Find last /
        if (std.mem.lastIndexOf(u8, path, "/")) |last_slash| {
            return path[last_slash + 1 ..];
        }
        return path;
    }
};

// Tests
test "basic mapping processing" {
    const allocator = std.testing.allocator;
    var processor = MappingProcessor.init(allocator);
    defer processor.deinit();
    
    // Add facts
    try processor.addFact("baseUrl", "http://example.org");
    try processor.addFact("spec", "test-spec");
    try processor.addFact("_uniqueIdentifier", "test-123");
    
    // Add rules
    const title_rule = MappingRule{
        .input_path = try allocator.dupe(u8, "/pocket/title"),
        .output_path = try allocator.dupe(u8, "/edm:RDF/edm:ProvidedCHO/dc:title"),
        .mapping_type = .direct,
        .groovy_code = null,
        .constant_value = null,
    };
    try processor.addRule(title_rule);
    
    const type_rule = MappingRule{
        .input_path = null,
        .output_path = try allocator.dupe(u8, "/edm:RDF/edm:ProvidedCHO/edm:type"),
        .mapping_type = .constant,
        .groovy_code = null,
        .constant_value = "IMAGE",
    };
    try processor.addRule(type_rule);
    
    // Process
    const input_xml =
        \\<pocket id="test-123">
        \\  <title>Test Title</title>
        \\</pocket>
    ;
    
    const output = try processor.process(input_xml);
    defer allocator.free(output);
    
    try std.testing.expect(std.mem.indexOf(u8, output, "<dc:title>Test Title</dc:title>") != null);
    try std.testing.expect(std.mem.indexOf(u8, output, "<edm:type>IMAGE</edm:type>") != null);
}