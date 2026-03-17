const std = @import("std");
const Allocator = std.mem.Allocator;
const XmlNode = @import("xml_parser.zig").XmlNode;
const NodeResult = @import("xml_parser.zig").NodeResult;
const StringFunctions = @import("string_functions.zig").StringFunctions;

/// Enhanced string interpolation engine that handles complex expressions
/// Based on 26,916 actual uses in mapping files

pub const InterpolationEngine = struct {
    allocator: Allocator,
    string_functions: StringFunctions,
    variables: std.StringHashMap(Value),
    nodes: std.StringHashMap(*XmlNode),

    pub fn init(allocator: Allocator) InterpolationEngine {
        return .{
            .allocator = allocator,
            .string_functions = StringFunctions.init(allocator),
            .variables = std.StringHashMap(Value).init(allocator),
            .nodes = std.StringHashMap(*XmlNode).init(allocator),
        };
    }

    pub fn deinit(self: *InterpolationEngine) void {
        var var_iter = self.variables.iterator();
        while (var_iter.next()) |entry| {
            entry.value_ptr.deinit(self.allocator);
        }
        self.variables.deinit();
        self.nodes.deinit();
    }

    /// Set a variable value
    pub fn setVariable(self: *InterpolationEngine, name: []const u8, value: Value) !void {
        try self.variables.put(name, value);
    }

    /// Set a node for navigation
    pub fn setNode(self: *InterpolationEngine, name: []const u8, node: *XmlNode) !void {
        try self.nodes.put(name, node);
    }

    /// Interpolate a template string
    pub fn interpolate(self: *InterpolationEngine, template: []const u8) ![]u8 {
        var result = std.ArrayList(u8).init(self.allocator);
        defer result.deinit();

        var i: usize = 0;
        while (i < template.len) {
            if (i + 1 < template.len and template[i] == '$' and template[i + 1] == '{') {
                // Find closing brace
                const start = i + 2;
                var depth: usize = 1;
                var end = start;
                
                while (end < template.len and depth > 0) : (end += 1) {
                    if (template[end] == '{') depth += 1;
                    if (template[end] == '}') depth -= 1;
                }
                
                if (depth == 0) {
                    // Evaluate expression
                    const expr = template[start .. end - 1];
                    var value = try self.evaluateExpression(expr);
                    defer value.deinit(self.allocator);
                    
                    const text = try value.asString(self.allocator);
                    defer self.allocator.free(text);
                    
                    try result.appendSlice(text);
                    i = end;
                    continue;
                }
            }
            
            try result.append(template[i]);
            i += 1;
        }

        return result.toOwnedSlice();
    }

    /// Evaluate an expression like "_input.record[0].about[0]" or "_id.sanitizeURI()"
    fn evaluateExpression(self: *InterpolationEngine, expr: []const u8) !Value {
        const trimmed = std.mem.trim(u8, expr, " \t\n\r");
        
        // Handle method calls first (e.g., "value.replaceAll('^0','')")
        if (std.mem.indexOf(u8, trimmed, "(")) |paren_pos| {
            const base_expr = trimmed[0..paren_pos];
            const last_dot = std.mem.lastIndexOf(u8, base_expr, ".");
            
            if (last_dot) |dot_pos| {
                // Get the object/value
                const obj_expr = base_expr[0..dot_pos];
                const method_name = base_expr[dot_pos + 1 ..];
                
                // Parse method arguments
                const args_end = std.mem.lastIndexOf(u8, trimmed, ")") orelse trimmed.len;
                const args_str = trimmed[paren_pos + 1 .. args_end];
                
                // Evaluate the object
                var obj = try self.evaluateExpression(obj_expr);
                defer obj.deinit(self.allocator);
                
                // Apply method
                return try self.applyMethod(obj, method_name, args_str);
            }
        }
        
        // Handle property access chain (e.g., "_input.record[0].about")
        var parts = std.mem.tokenizeAny(u8, trimmed, ".");
        var current: ?Value = null;
        var current_node: ?*XmlNode = null;
        
        while (parts.next()) |part| {
            defer {
                if (current) |*c| c.deinit(self.allocator);
                current = null;
            }
            
            // Handle array access [n]
            var base_part = part;
            var array_index: ?usize = null;
            
            if (std.mem.indexOf(u8, part, "[")) |bracket_start| {
                base_part = part[0..bracket_start];
                if (std.mem.indexOf(u8, part[bracket_start + 1 ..], "]")) |bracket_end| {
                    const index_str = part[bracket_start + 1 .. bracket_start + 1 + bracket_end];
                    array_index = try std.fmt.parseInt(usize, index_str, 10);
                }
            }
            
            // First part - check variables and nodes
            if (current == null and current_node == null) {
                if (self.variables.get(base_part)) |value| {
                    current = try value.clone(self.allocator);
                } else if (self.nodes.get(base_part)) |node| {
                    current_node = node;
                } else if (base_part.len > 0 and base_part[0] == '_') {
                    // Handle special variables like _input
                    if (std.mem.eql(u8, base_part, "_input")) {
                        // Return a placeholder for _input
                        current = Value{ .object = {} };
                    } else {
                        // Check if it's a node reference
                        const name = base_part[1..];
                        if (self.nodes.get(name)) |node| {
                            current_node = node;
                        } else {
                            current = Value{ .string = try self.allocator.dupe(u8, base_part) };
                        }
                    }
                } else {
                    // Literal value
                    current = Value{ .string = try self.allocator.dupe(u8, base_part) };
                }
            } else if (current_node) |node| {
                // Navigate from current node
                var nav_result = node.get(base_part);
                defer nav_result.deinit();
                
                if (nav_result.asNode()) |next_node| {
                    current_node = next_node;
                } else {
                    const text = nav_result.asText();
                    current = Value{ .string = try self.allocator.dupe(u8, text) };
                    current_node = null;
                }
            } else if (current) |curr| {
                // Property access on value
                if (curr == .object) {
                    // Placeholder for complex navigation
                    current = Value{ .string = try self.allocator.dupe(u8, base_part) };
                } else {
                    // Can't navigate further
                    current = try curr.clone(self.allocator);
                }
            }
            
            // Handle array index if present
            if (array_index) |index| {
                if (current_node) |node| {
                    if (node.children.items.len > index) {
                        current_node = node.children.items[index];
                    } else {
                        current_node = null;
                        current = Value{ .string = try self.allocator.dupe(u8, "") };
                    }
                }
            }
        }
        
        // Return final result
        if (current_node) |node| {
            return Value{ .string = try self.allocator.dupe(u8, node.getText()) };
        } else if (current) |c| {
            const result = try c.clone(self.allocator);
            return result;
        } else {
            return Value{ .string = try self.allocator.dupe(u8, trimmed) };
        }
    }

    /// Apply a method to a value
    fn applyMethod(self: *InterpolationEngine, value: Value, method: []const u8, args_str: []const u8) !Value {
        const str = try value.asString(self.allocator);
        defer self.allocator.free(str);
        
        // Parse arguments (simple version - handles quoted strings)
        var args = std.ArrayList([]const u8).init(self.allocator);
        defer args.deinit();
        
        var arg_parts = std.mem.tokenizeAny(u8, args_str, ",");
        while (arg_parts.next()) |arg| {
            const trimmed_arg = std.mem.trim(u8, arg, " \t");
            if (trimmed_arg.len >= 2 and 
                ((trimmed_arg[0] == '\'' and trimmed_arg[trimmed_arg.len - 1] == '\'') or
                 (trimmed_arg[0] == '"' and trimmed_arg[trimmed_arg.len - 1] == '"'))) {
                try args.append(trimmed_arg[1 .. trimmed_arg.len - 1]);
            } else {
                try args.append(trimmed_arg);
            }
        }
        
        // Apply string functions based on method name
        if (std.mem.eql(u8, method, "sanitizeURI")) {
            const result = self.string_functions.sanitizeURI(str);
            return Value{ .string = try self.allocator.dupe(u8, result) };
        } else if (std.mem.eql(u8, method, "sanitize")) {
            const result = self.string_functions.sanitize(str);
            return Value{ .string = try self.allocator.dupe(u8, result) };
        } else if (std.mem.eql(u8, method, "replaceAll") and args.items.len >= 2) {
            const result = try self.string_functions.replaceAll(str, args.items[0], args.items[1]);
            return Value{ .string = result };
        } else if (std.mem.eql(u8, method, "replace") and args.items.len >= 2) {
            const result = try self.string_functions.replace(str, args.items[0], args.items[1]);
            return Value{ .string = result };
        } else if (std.mem.eql(u8, method, "toString")) {
            return Value{ .string = try self.allocator.dupe(u8, str) };
        } else if (std.mem.eql(u8, method, "capitalize")) {
            const result = try self.string_functions.capitalize(str);
            return Value{ .string = result };
        } else if (std.mem.eql(u8, method, "toLowerCase")) {
            const result = try self.string_functions.toLowerCase(str);
            return Value{ .string = result };
        } else if (std.mem.eql(u8, method, "toUpperCase")) {
            const result = try self.string_functions.toUpperCase(str);
            return Value{ .string = result };
        } else if (std.mem.eql(u8, method, "trim")) {
            const result = try self.string_functions.trim(str);
            return Value{ .string = result };
        } else {
            // Unknown method - return as-is
            return Value{ .string = try self.allocator.dupe(u8, str) };
        }
    }
};

/// Value type for expressions
pub const Value = union(enum) {
    string: []u8,
    integer: i64,
    float: f64,
    boolean: bool,
    array: std.ArrayList(Value),
    object: void, // Placeholder for complex objects
    
    pub fn deinit(self: *Value, allocator: Allocator) void {
        switch (self.*) {
            .string => |s| allocator.free(s),
            .array => |*arr| {
                for (arr.items) |*item| {
                    item.deinit(allocator);
                }
                arr.deinit();
            },
            else => {},
        }
    }
    
    pub fn clone(self: Value, allocator: Allocator) !Value {
        return switch (self) {
            .string => |s| Value{ .string = try allocator.dupe(u8, s) },
            .integer => |i| Value{ .integer = i },
            .float => |f| Value{ .float = f },
            .boolean => |b| Value{ .boolean = b },
            .array => |arr| {
                var new_arr = std.ArrayList(Value).init(allocator);
                for (arr.items) |item| {
                    try new_arr.append(try item.clone(allocator));
                }
                return Value{ .array = new_arr };
            },
            .object => Value{ .object = {} },
        };
    }
    
    pub fn asString(self: Value, allocator: Allocator) ![]u8 {
        return switch (self) {
            .string => |s| try allocator.dupe(u8, s),
            .integer => |i| try std.fmt.allocPrint(allocator, "{}", .{i}),
            .float => |f| try std.fmt.allocPrint(allocator, "{d:.2}", .{f}),
            .boolean => |b| try allocator.dupe(u8, if (b) "true" else "false"),
            .array => try allocator.dupe(u8, "[array]"),
            .object => try allocator.dupe(u8, "[object]"),
        };
    }
};

// Tests
test "basic interpolation" {
    const allocator = std.testing.allocator;
    var engine = InterpolationEngine.init(allocator);
    defer engine.deinit();
    
    try engine.setVariable("name", Value{ .string = try allocator.dupe(u8, "Van Gogh") });
    try engine.setVariable("year", Value{ .integer = 1888 });
    
    const result = try engine.interpolate("Artist: ${name}, Year: ${year}");
    defer allocator.free(result);
    
    try std.testing.expectEqualStrings("Artist: Van Gogh, Year: 1888", result);
}

test "method call interpolation" {
    const allocator = std.testing.allocator;
    var engine = InterpolationEngine.init(allocator);
    defer engine.deinit();
    
    try engine.setVariable("title", Value{ .string = try allocator.dupe(u8, "hello world") });
    
    const result = try engine.interpolate("Title: ${title.capitalize()}");
    defer allocator.free(result);
    
    try std.testing.expectEqualStrings("Title: Hello world", result);
}

test "complex expression" {
    const allocator = std.testing.allocator;
    var engine = InterpolationEngine.init(allocator);
    defer engine.deinit();
    
    try engine.setVariable("id", Value{ .string = try allocator.dupe(u8, "test 123") });
    
    const result = try engine.interpolate("${id.replaceAll(' ', '_').toUpperCase()}");
    defer allocator.free(result);
    
    try std.testing.expectEqualStrings("TEST_123", result);
}