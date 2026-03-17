const std = @import("std");
const engine = @import("mapping_engine.zig");

// Represents the mapping XML structure
const MappingXml = struct {
    prefix: []const u8,
    schema_version: []const u8,
    facts: std.StringHashMap([]const u8),
    functions: []MappingFunction,
    node_mappings: []NodeMapping,
};

const MappingFunction = struct {
    name: []const u8,
    code: []const u8,
};

const NodeMapping = struct {
    input_path: []const u8,
    output_path: []const u8,
    groovy_code: ?[]const u8 = null,
    dictionary: ?std.StringHashMap([]const u8) = null,
    operator: ?[]const u8 = null,
};

pub const GroovyGenerator = struct {
    allocator: std.mem.Allocator,
    out: std.ArrayList(u8),
    indent_level: usize = 0,

    pub fn init(allocator: std.mem.Allocator) GroovyGenerator {
        return .{
            .allocator = allocator,
            .out = std.ArrayList(u8).init(allocator),
        };
    }

    pub fn deinit(self: *GroovyGenerator) void {
        self.out.deinit();
    }

    fn indent(self: *GroovyGenerator) void {
        for (0..self.indent_level * 4) |_| {
            self.out.append(' ') catch {};
        }
    }

    fn line(self: *GroovyGenerator, text: []const u8) void {
        self.indent();
        self.out.appendSlice(text) catch {};
        self.out.append('\n') catch {};
    }

    fn append(self: *GroovyGenerator, text: []const u8) void {
        self.out.appendSlice(text) catch {};
    }

    pub fn generateGroovy(self: *GroovyGenerator, mapping: MappingXml, record_def: []const u8) ![]u8 {
        // Header
        self.line("// SIP-Creator Generated Mapping Code");
        self.line("// ----------------------------------");
        self.line("// Discarding:");
        self.line("import eu.delving.groovy.DiscardRecordException");
        self.line("import eu.delving.metadata.OptList");
        self.line("def discard = { reason -> throw new DiscardRecordException(reason.toString()) }");
        self.line("def discardIf = { thing, reason ->  if (thing) throw new DiscardRecordException(reason.toString()) }");
        self.line("def discardIfNot = { thing, reason ->  if (!thing) throw new DiscardRecordException(reason.toString()) }");
        self.line("Object _facts = WORLD._facts");
        self.line("Object _optLookup = WORLD._optLookup");
        
        // Facts as variables
        var it = mapping.facts.iterator();
        while (it.next()) |entry| {
            self.indent();
            self.append("String ");
            self.append(entry.key_ptr.*);
            self.append(" = '''");
            self.append(entry.value_ptr.*);
            self.append("'''\n");
        }
        
        self.line("String _uniqueIdentifier = 'UNIQUE_IDENTIFIER'");
        self.line("");
        
        // Functions from mapping
        if (mapping.functions.len > 0) {
            self.line("// Functions from Mapping:");
            for (mapping.functions) |func| {
                self.line("def ");
                self.append(func.name);
                self.append(" = { it ->\n");
                self.indent_level += 1;
                self.line(func.code);
                self.indent_level -= 1;
                self.line("}");
            }
        }
        
        self.line("// Functions from Record Definition:");
        self.line("// (Record definition functions would be added here)");
        self.line("");
        
        self.line("// DSL Category wraps Builder call:");
        self.line("boolean _absent_ = true");
        self.line("def outputNode");
        self.line("use (MappingCategory) {");
        self.indent_level += 1;
        
        // Generate the main mapping structure
        self.line("WORLD.input * { _input ->");
        self.indent_level += 1;
        
        // Set unique identifier
        self.line("_uniqueIdentifier = _input['@id'][0].toString()");
        self.line("_absent_ = true");
        
        // Parse record definition to understand output structure
        const output_root = try self.parseOutputRoot(record_def);
        
        // Start building output
        self.indent();
        self.append("outputNode = WORLD.output.'");
        self.append(output_root);
        self.append("' {\n");
        self.indent_level += 1;
        
        // Group mappings by output element
        try self.generateMappingsByOutput(mapping.node_mappings);
        
        self.indent_level -= 1;
        self.line("}");
        
        self.indent_level -= 1;
        self.line("}");
        
        self.line("outputNode");
        
        self.indent_level -= 1;
        self.line("}");
        self.line("// ----------------------------------");
        
        return self.out.toOwnedSlice();
    }

    fn parseOutputRoot(self: *GroovyGenerator, record_def: []const u8) ![]const u8 {
        _ = self;
        _ = record_def;
        // For now, return a default - in real implementation would parse record def
        return "edm:RDF";
    }

    fn generateMappingsByOutput(self: *GroovyGenerator, mappings: []NodeMapping) !void {
        // Group mappings by their output structure
        // This is a simplified version - real implementation would build proper tree
        
        // First, handle root-level mappings
        for (mappings) |mapping| {
            if (std.mem.indexOf(u8, mapping.output_path, "/edm:RDF/ore:Aggregation") != null) {
                try self.generateAggregationMappings(mappings);
                break;
            }
        }
        
        // Add other top-level elements
        for (mappings) |mapping| {
            if (std.mem.indexOf(u8, mapping.output_path, "/edm:RDF/edm:ProvidedCHO") != null) {
                try self.generateProvidedCHOMappings(mappings);
                break;
            }
        }
    }

    fn generateAggregationMappings(self: *GroovyGenerator, mappings: []NodeMapping) !void {
        self.line("'ore:Aggregation' (");
        self.indent_level += 1;
        
        // Find attribute mappings for Aggregation
        for (mappings) |mapping| {
            if (std.mem.eql(u8, mapping.output_path, "/edm:RDF/ore:Aggregation/@rdf:about")) {
                self.indent();
                self.append("'rdf:about' : {\n");
                self.indent_level += 1;
                if (mapping.groovy_code) |code| {
                    self.line(code);
                }
                self.indent_level -= 1;
                self.line("}");
            }
        }
        
        self.indent_level -= 1;
        self.line(") {");
        self.indent_level += 1;
        
        // Add child elements
        for (mappings) |mapping| {
            if (std.mem.indexOf(u8, mapping.output_path, "/edm:RDF/ore:Aggregation/") != null and
                std.mem.indexOf(u8, mapping.output_path, "@") == null) {
                try self.generateSimpleMapping(mapping);
            }
        }
        
        self.indent_level -= 1;
        self.line("}");
    }

    fn generateProvidedCHOMappings(self: *GroovyGenerator, mappings: []NodeMapping) !void {
        self.line("'edm:ProvidedCHO' (");
        self.indent_level += 1;
        
        // Find attribute mappings
        for (mappings) |mapping| {
            if (std.mem.eql(u8, mapping.output_path, "/edm:RDF/edm:ProvidedCHO/@rdf:about")) {
                self.indent();
                self.append("'rdf:about' : {\n");
                self.indent_level += 1;
                if (mapping.groovy_code) |code| {
                    self.line(code);
                }
                self.indent_level -= 1;
                self.line("}");
            }
        }
        
        self.indent_level -= 1;
        self.line(") {");
        self.indent_level += 1;
        
        // Add child elements
        for (mappings) |mapping| {
            if (std.mem.indexOf(u8, mapping.output_path, "/edm:RDF/edm:ProvidedCHO/") != null and
                std.mem.indexOf(u8, mapping.output_path, "@") == null) {
                try self.generateSimpleMapping(mapping);
            }
        }
        
        self.indent_level -= 1;
        self.line("}");
    }

    fn generateSimpleMapping(self: *GroovyGenerator, mapping: NodeMapping) !void {
        // Extract element name from path
        const last_slash = std.mem.lastIndexOf(u8, mapping.output_path, "/") orelse return;
        const element = mapping.output_path[last_slash + 1 ..];
        
        if (mapping.input_path.len > 0) {
            const input = mapping.input_path;
            if (std.mem.startsWith(u8, input, "/facts/")) {
                // Fact-based mapping
                const fact_name = input[7..];
                self.line("_absent_ = true");
                self.indent();
                self.append("_facts.");
                self.append(fact_name);
                self.append(" * { _");
                self.append(fact_name);
                self.append(" ->\n");
                self.indent_level += 1;
                
                self.indent();
                self.append("'");
                self.append(element);
                self.append("' { _absent_ = false\n");
                self.indent_level += 1;
                self.indent();
                self.append("\"${_");
                self.append(fact_name);
                self.append("}\"\n");
                self.indent_level -= 1;
                self.line("}");
                
                self.indent_level -= 1;
                self.line("}");
            } else if (mapping.groovy_code) |code| {
                // Complex mapping with code
                self.indent();
                self.append("'");
                self.append(element);
                self.append("' {\n");
                self.indent_level += 1;
                
                // Handle multi-line groovy code
                var lines = std.mem.tokenizeScalar(u8, code, '\n');
                while (lines.next()) |line_text| {
                    self.line(line_text);
                }
                
                self.indent_level -= 1;
                self.line("}");
            }
        }
    }
};

// Example usage
pub fn main() !void {
    var gpa = std.heap.GeneralPurposeAllocator(.{}){};
    defer _ = gpa.deinit();
    const allocator = gpa.allocator();

    // Create sample mapping
    var facts = std.StringHashMap([]const u8).init(allocator);
    defer facts.deinit();
    try facts.put("baseUrl", "http://data.collectienederland.nl");
    try facts.put("spec", "van-gogh-museum");
    try facts.put("provider", "Rijksdienst voor het Cultureel Erfgoed");
    try facts.put("dataProvider", "Van Gogh Museum");

    var mappings = [_]NodeMapping{
        .{
            .input_path = "/input",
            .output_path = "/edm:RDF/ore:Aggregation/@rdf:about",
            .groovy_code = "\"${baseUrl}/resource/aggregation/${spec}/${_uniqueIdentifier}\"",
        },
        .{
            .input_path = "/facts/dataProvider",
            .output_path = "/edm:RDF/ore:Aggregation/edm:dataProvider",
        },
        .{
            .input_path = "/facts/provider",
            .output_path = "/edm:RDF/ore:Aggregation/edm:provider",
        },
    };

    const mapping = MappingXml{
        .prefix = "edm",
        .schema_version = "5.2.6",
        .facts = facts,
        .functions = &[_]MappingFunction{},
        .node_mappings = mappings[0..],
    };

    var generator = GroovyGenerator.init(allocator);
    defer generator.deinit();

    const groovy_code = try generator.generateGroovy(mapping, "");
    defer allocator.free(groovy_code);

    std.debug.print("Generated Groovy:\n{s}\n", .{groovy_code});
}