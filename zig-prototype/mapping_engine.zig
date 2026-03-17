const std = @import("std");

// Core XML node structure matching GroovyNode behavior
pub const XmlNode = struct {
    allocator: std.mem.Allocator,
    tag: []const u8,
    namespace: ?[]const u8 = null,
    attributes: std.StringHashMap([]const u8),
    children: std.ArrayList(*XmlNode),
    text: []const u8 = "",
    parent: ?*XmlNode = null,

    pub fn init(allocator: std.mem.Allocator, tag: []const u8) !*XmlNode {
        const node = try allocator.create(XmlNode);
        node.* = .{
            .allocator = allocator,
            .tag = try allocator.dupe(u8, tag),
            .attributes = std.StringHashMap([]const u8).init(allocator),
            .children = std.ArrayList(*XmlNode).init(allocator),
        };
        return node;
    }

    pub fn deinit(self: *XmlNode) void {
        self.allocator.free(self.tag);
        self.attributes.deinit();
        for (self.children.items) |child| {
            child.deinit();
            self.allocator.destroy(child);
        }
        self.children.deinit();
        if (self.text.len > 0) {
            self.allocator.free(self.text);
        }
    }

    // Get method matching GroovyNode behavior
    pub fn get(self: *XmlNode, key: []const u8) NodeResult {
        // Handle attribute access
        if (key.len > 0 and key[0] == '@') {
            const attr_name = key[1..];
            if (self.attributes.get(attr_name)) |value| {
                return NodeResult{ .string_value = value };
            }
            return NodeResult{ .string_value = "" };
        }

        // Handle wildcard - return all children
        if (std.mem.eql(u8, key, "*")) {
            return NodeResult{ .node_list = self.children.items };
        }

        // Handle first non-empty match (underscore suffix)
        if (key.len > 0 and key[key.len - 1] == '_') {
            const tag_name = key[0 .. key.len - 1];
            const first = self.findFirstMatch(tag_name);
            if (first) |node| {
                return NodeResult{ .single_node = node };
            }
            return NodeResult{ .single_node = null };
        }

        // Return all matching children
        var matches = std.ArrayList(*XmlNode).init(self.allocator);
        defer matches.deinit();
        for (self.children.items) |child| {
            if (std.mem.eql(u8, child.tag, key)) {
                matches.append(child) catch {};
            }
        }
        // Create a static slice that references existing nodes
        const slice = self.allocator.alloc(*XmlNode, matches.items.len) catch return NodeResult{ .node_list = &[_]*XmlNode{} };
        @memcpy(slice, matches.items);
        return NodeResult{ .node_list = slice };
    }

    fn findFirstMatch(self: *XmlNode, tag_name: []const u8) ?*XmlNode {
        for (self.children.items) |child| {
            if (std.mem.eql(u8, child.tag, tag_name) and child.text.len > 0) {
                return child;
            }
            if (child.findFirstMatch(tag_name)) |found| {
                return found;
            }
        }
        return null;
    }

    pub fn setText(self: *XmlNode, text: []const u8) !void {
        if (self.text.len > 0) {
            self.allocator.free(self.text);
        }
        self.text = try self.allocator.dupe(u8, text);
    }
};

// Result type for get operations
pub const NodeResult = union(enum) {
    single_node: ?*XmlNode,
    node_list: []*XmlNode,
    string_value: []const u8,

    pub fn toString(self: NodeResult) []const u8 {
        switch (self) {
            .single_node => |node| {
                if (node) |n| return n.text;
                return "";
            },
            .string_value => |val| return val,
            .node_list => return "",
        }
    }
    
    pub fn deinit(self: NodeResult, allocator: std.mem.Allocator) void {
        switch (self) {
            .node_list => |list| {
                if (list.len > 0) {
                    allocator.free(list);
                }
            },
            else => {},
        }
    }
};

// String manipulation functions matching SIP-Creator
pub const StringUtils = struct {
    pub fn sanitize(allocator: std.mem.Allocator, input: []const u8) ![]const u8 {
        var result = std.ArrayList(u8).init(allocator);
        defer result.deinit();

        var prev_space = false;
        var is_leading = true;
        
        for (input) |c| {
            if (c == '\n' or c == '\r' or c == '\t' or c == ' ') {
                if (!is_leading and !prev_space) {
                    try result.append(' ');
                    prev_space = true;
                }
            } else {
                try result.append(c);
                prev_space = false;
                is_leading = false;
            }
        }

        // Trim trailing space
        if (result.items.len > 0 and result.items[result.items.len - 1] == ' ') {
            _ = result.pop();
        }

        return result.toOwnedSlice();
    }

    pub fn sanitizeURI(allocator: std.mem.Allocator, input: []const u8) ![]const u8 {
        var result = std.ArrayList(u8).init(allocator);
        defer result.deinit();

        for (input) |c| {
            switch (c) {
                ' ' => try result.appendSlice("%20"),
                '[' => try result.appendSlice("%5B"),
                ']' => try result.appendSlice("%5D"),
                '\\' => try result.appendSlice("%5C"),
                else => try result.append(c),
            }
        }

        return result.toOwnedSlice();
    }
};

// Simple string interpolation support
pub fn interpolate(allocator: std.mem.Allocator, template: []const u8, vars: std.StringHashMap([]const u8)) ![]const u8 {
    var result = std.ArrayList(u8).init(allocator);
    defer result.deinit();

    var i: usize = 0;
    while (i < template.len) {
        if (i + 1 < template.len and template[i] == '$' and template[i + 1] == '{') {
            // Find closing brace
            const start = i + 2;
            var end = start;
            while (end < template.len and template[end] != '}') : (end += 1) {}
            
            if (end < template.len) {
                const var_name = template[start..end];
                if (vars.get(var_name)) |value| {
                    try result.appendSlice(value);
                }
                i = end + 1;
                continue;
            }
        }
        try result.append(template[i]);
        i += 1;
    }

    return result.toOwnedSlice();
}

// Mapping context
pub const MappingContext = struct {
    allocator: std.mem.Allocator,
    input: *XmlNode,
    facts: std.StringHashMap([]const u8),
    unique_identifier: []const u8,

    pub fn init(allocator: std.mem.Allocator, input: *XmlNode) MappingContext {
        return .{
            .allocator = allocator,
            .input = input,
            .facts = std.StringHashMap([]const u8).init(allocator),
            .unique_identifier = "test-id", // Default for testing
        };
    }

    pub fn deinit(self: *MappingContext) void {
        self.facts.deinit();
    }
};

// Simple expression evaluator for mapping code
pub const Expression = union(enum) {
    literal: []const u8,
    variable: []const u8,
    field_access: struct {
        object: []const u8,
        field: []const u8,
    },
    string_template: []const u8,
};

pub fn evaluateExpression(ctx: *MappingContext, expr: Expression) ![]const u8 {
    switch (expr) {
        .literal => |val| return val,
        .variable => |name| {
            if (ctx.facts.get(name)) |value| {
                return value;
            }
            return "";
        },
        .string_template => |template| {
            var vars = std.StringHashMap([]const u8).init(ctx.allocator);
            defer vars.deinit();
            
            // Add context variables
            try vars.put("_uniqueIdentifier", ctx.unique_identifier);
            var it = ctx.facts.iterator();
            while (it.next()) |entry| {
                try vars.put(entry.key_ptr.*, entry.value_ptr.*);
            }
            
            return try interpolate(ctx.allocator, template, vars);
        },
        .field_access => |fa| {
            // Simple field access implementation
            if (std.mem.eql(u8, fa.object, "_input")) {
                const result = ctx.input.get(fa.field);
                return result.toString();
            }
            return "";
        },
    }
}

// Example usage and test
pub fn main() !void {
    var gpa = std.heap.GeneralPurposeAllocator(.{}){};
    defer _ = gpa.deinit();
    const allocator = gpa.allocator();

    // Create sample XML structure
    const root = try XmlNode.init(allocator, "input");
    defer {
        root.deinit();
        allocator.destroy(root);
    }
    try root.attributes.put("id", "001");

    const title = try XmlNode.init(allocator, "title");
    try title.setText("Starry Night");
    try root.children.append(title);

    const creator = try XmlNode.init(allocator, "creator");
    try creator.setText("Vincent van Gogh");
    try root.children.append(creator);

    // Test navigation
    const title_result = root.get("title_");
    std.debug.print("Title (first): {s}\n", .{title_result.toString()});

    const id_result = root.get("@id");
    std.debug.print("ID attribute: {s}\n", .{id_result.toString()});

    // Test string utilities
    const messy_text = "  Hello   \n\n  World  \t ";
    const clean_text = try StringUtils.sanitize(allocator, messy_text);
    defer allocator.free(clean_text);
    std.debug.print("Sanitized: '{s}' (from '{s}')\n", .{clean_text, messy_text});

    const uri_text = "hello [world] test";
    const uri_safe = try StringUtils.sanitizeURI(allocator, uri_text);
    defer allocator.free(uri_safe);
    std.debug.print("URI safe: '{s}'\n", .{uri_safe});

    // Test context and expression evaluation
    var ctx = MappingContext.init(allocator, root);
    defer ctx.deinit();
    try ctx.facts.put("baseUrl", "http://example.com");
    try ctx.facts.put("spec", "test-collection");
    ctx.unique_identifier = "12345";

    const expr = Expression{ 
        .string_template = "${baseUrl}/resource/${spec}/${_uniqueIdentifier}" 
    };
    const result = try evaluateExpression(&ctx, expr);
    defer allocator.free(result);
    std.debug.print("Interpolated: {s}\n", .{result});
}

// Tests
test "XmlNode navigation" {
    const allocator = std.testing.allocator;
    
    const root = try XmlNode.init(allocator, "root");
    defer {
        root.deinit();
        allocator.destroy(root);
    }
    
    const child1 = try XmlNode.init(allocator, "child");
    try child1.setText("value1");
    try root.children.append(child1);
    
    const child2 = try XmlNode.init(allocator, "child");
    try child2.setText("value2");
    try root.children.append(child2);
    
    // Test get all children
    const all_children = root.get("child");
    defer all_children.deinit(allocator);
    try std.testing.expect(all_children.node_list.len == 2);
    
    // Test get first non-empty
    const first = root.get("child_");
    try std.testing.expectEqualStrings("value1", first.toString());
}

test "String sanitization" {
    const allocator = std.testing.allocator;
    
    const input = "  Hello   \n\n  World  \t ";
    const result = try StringUtils.sanitize(allocator, input);
    defer allocator.free(result);
    
    try std.testing.expectEqualStrings("Hello World", result);
}

test "URI encoding" {
    const allocator = std.testing.allocator;
    
    const input = "hello [world] test\\path";
    const result = try StringUtils.sanitizeURI(allocator, input);
    defer allocator.free(result);
    
    try std.testing.expectEqualStrings("hello%20%5Bworld%5D%20test%5Cpath", result);
}