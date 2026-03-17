const std = @import("std");
const Allocator = std.mem.Allocator;

/// XML parser implementation that supports SIP-Creator navigation patterns
/// Including the critical _ suffix for "first non-empty" selection

pub const XmlNode = struct {
    allocator: Allocator,
    name: []const u8,
    attributes: std.StringHashMap([]const u8),
    children: std.ArrayList(*XmlNode),
    text: ?[]const u8,
    parent: ?*XmlNode,

    pub fn init(allocator: Allocator, name: []const u8) !*XmlNode {
        const node = try allocator.create(XmlNode);
        node.* = .{
            .allocator = allocator,
            .name = try allocator.dupe(u8, name),
            .attributes = std.StringHashMap([]const u8).init(allocator),
            .children = std.ArrayList(*XmlNode).init(allocator),
            .text = null,
            .parent = null,
        };
        return node;
    }

    pub fn deinit(self: *XmlNode) void {
        self.allocator.free(self.name);
        
        var attr_iter = self.attributes.iterator();
        while (attr_iter.next()) |entry| {
            self.allocator.free(entry.key_ptr.*);
            self.allocator.free(entry.value_ptr.*);
        }
        self.attributes.deinit();
        
        for (self.children.items) |child| {
            child.deinit();
            self.allocator.destroy(child);
        }
        self.children.deinit();
        
        if (self.text) |text| {
            self.allocator.free(text);
        }
    }

    /// Add attribute to node
    pub fn setAttribute(self: *XmlNode, name: []const u8, value: []const u8) !void {
        const key = try self.allocator.dupe(u8, name);
        const val = try self.allocator.dupe(u8, value);
        try self.attributes.put(key, val);
    }

    /// Add child node
    pub fn addChild(self: *XmlNode, child: *XmlNode) !void {
        child.parent = self;
        try self.children.append(child);
    }

    /// Set text content
    pub fn setText(self: *XmlNode, text: []const u8) !void {
        if (self.text) |old_text| {
            self.allocator.free(old_text);
        }
        self.text = try self.allocator.dupe(u8, text);
    }

    /// Get text content (returns empty string if null)
    pub fn getText(self: *const XmlNode) []const u8 {
        return self.text orelse "";
    }

    /// Check if node has non-empty text
    pub fn hasNonEmptyText(self: *const XmlNode) bool {
        const text = self.getText();
        return text.len > 0 and !isWhitespace(text);
    }

    /// SIP-Creator style navigation
    pub fn get(self: *XmlNode, path: []const u8) NodeResult {
        // Handle attribute access with @
        if (path.len > 0 and path[0] == '@') {
            const attr_name = path[1..];
            if (self.attributes.get(attr_name)) |value| {
                return NodeResult{ .text = value };
            }
            return NodeResult{ .none = {} };
        }

        // Handle underscore suffix for "first non-empty"
        if (path.len > 1 and path[path.len - 1] == '_') {
            const element_name = path[0 .. path.len - 1];
            
            // Find first child with this name that has non-empty text
            for (self.children.items) |child| {
                if (std.mem.eql(u8, child.name, element_name)) {
                    if (child.hasNonEmptyText()) {
                        return NodeResult{ .single = child };
                    }
                    // Also check if it has children with non-empty text
                    if (child.hasNonEmptyChildren()) {
                        return NodeResult{ .single = child };
                    }
                }
            }
            return NodeResult{ .none = {} };
        }

        // Regular element access - return all matching children
        var matches = std.ArrayList(*XmlNode).init(self.allocator);
        for (self.children.items) |child| {
            if (std.mem.eql(u8, child.name, path)) {
                matches.append(child) catch {
                    matches.deinit();
                    return NodeResult{ .none = {} };
                };
            }
        }

        if (matches.items.len == 0) {
            matches.deinit();
            return NodeResult{ .none = {} };
        } else if (matches.items.len == 1) {
            const node = matches.items[0];
            matches.deinit();
            return NodeResult{ .single = node };
        } else {
            return NodeResult{ .multiple = matches };
        }
    }

    /// Check if node has children with non-empty content
    fn hasNonEmptyChildren(self: *const XmlNode) bool {
        for (self.children.items) |child| {
            if (child.hasNonEmptyText()) return true;
            if (child.hasNonEmptyChildren()) return true;
        }
        return false;
    }
};

/// Result of navigation operations
pub const NodeResult = union(enum) {
    none: void,
    single: *XmlNode,
    multiple: std.ArrayList(*XmlNode),
    text: []const u8,

    pub fn deinit(self: *NodeResult) void {
        switch (self.*) {
            .multiple => |*list| list.deinit(),
            else => {},
        }
    }

    /// Get as text (for string operations)
    pub fn asText(self: NodeResult) []const u8 {
        return switch (self) {
            .none => "",
            .single => |node| node.getText(),
            .multiple => |nodes| if (nodes.items.len > 0) nodes.items[0].getText() else "",
            .text => |t| t,
        };
    }

    /// Get as single node (for further navigation)
    pub fn asNode(self: NodeResult) ?*XmlNode {
        return switch (self) {
            .none => null,
            .single => |node| node,
            .multiple => |nodes| if (nodes.items.len > 0) nodes.items[0] else null,
            .text => null,
        };
    }

    /// Get as node list
    pub fn asNodes(self: NodeResult) ?[]const *XmlNode {
        return switch (self) {
            .none => null,
            .single => |node| @as([]const *XmlNode, &.{node}),
            .multiple => |nodes| nodes.items,
            .text => null,
        };
    }

    /// Check if result is empty
    pub fn isEmpty(self: NodeResult) bool {
        return switch (self) {
            .none => true,
            .single => false,
            .multiple => |nodes| nodes.items.len == 0,
            .text => |t| t.len == 0,
        };
    }
};

/// Simple XML parser
pub const XmlParser = struct {
    allocator: Allocator,

    pub fn init(allocator: Allocator) XmlParser {
        return .{ .allocator = allocator };
    }

    /// Parse XML string into node tree
    pub fn parse(self: XmlParser, xml: []const u8) !*XmlNode {
        var pos: usize = 0;
        
        // Skip XML declaration if present
        if (std.mem.startsWith(u8, xml, "<?xml")) {
            if (std.mem.indexOf(u8, xml, "?>")) |end| {
                pos = end + 2;
            }
        }

        // Skip whitespace
        pos = skipWhitespace(xml, pos);
        
        // Parse root element
        return try self.parseElement(xml, &pos);
    }

    fn parseElement(self: XmlParser, xml: []const u8, pos: *usize) anyerror!*XmlNode {
        // Expect <
        if (pos.* >= xml.len or xml[pos.*] != '<') {
            return error.InvalidXml;
        }
        pos.* += 1;

        // Parse element name
        const name_start = pos.*;
        while (pos.* < xml.len and xml[pos.*] != ' ' and xml[pos.*] != '>' and xml[pos.*] != '/') {
            pos.* += 1;
        }
        const name = xml[name_start..pos.*];
        
        const node = try XmlNode.init(self.allocator, name);
        errdefer node.deinit();

        // Parse attributes
        while (pos.* < xml.len and xml[pos.*] != '>' and xml[pos.*] != '/') {
            pos.* = skipWhitespace(xml, pos.*);
            if (xml[pos.*] == '>' or xml[pos.*] == '/') break;
            
            try self.parseAttribute(xml, pos, node);
        }

        // Check for self-closing tag
        if (pos.* < xml.len and xml[pos.*] == '/') {
            pos.* += 1;
            if (pos.* >= xml.len or xml[pos.*] != '>') {
                return error.InvalidXml;
            }
            pos.* += 1;
            return node;
        }

        // Skip >
        if (pos.* >= xml.len or xml[pos.*] != '>') {
            return error.InvalidXml;
        }
        pos.* += 1;

        // Parse content
        try self.parseContent(xml, pos, node);

        // Parse closing tag
        if (!try self.parseClosingTag(xml, pos, name)) {
            return error.MismatchedTags;
        }

        return node;
    }

    fn parseAttribute(self: XmlParser, xml: []const u8, pos: *usize, node: *XmlNode) !void {
        _ = self;
        
        // Parse attribute name
        const name_start = pos.*;
        while (pos.* < xml.len and xml[pos.*] != '=' and xml[pos.*] != ' ') {
            pos.* += 1;
        }
        const attr_name = xml[name_start..pos.*];

        // Skip whitespace and =
        pos.* = skipWhitespace(xml, pos.*);
        if (pos.* >= xml.len or xml[pos.*] != '=') {
            return error.InvalidAttribute;
        }
        pos.* += 1;
        pos.* = skipWhitespace(xml, pos.*);

        // Parse attribute value
        if (pos.* >= xml.len) return error.InvalidAttribute;
        const quote = xml[pos.*];
        if (quote != '"' and quote != '\'') {
            return error.InvalidAttribute;
        }
        pos.* += 1;

        const value_start = pos.*;
        while (pos.* < xml.len and xml[pos.*] != quote) {
            pos.* += 1;
        }
        const attr_value = xml[value_start..pos.*];
        pos.* += 1;

        try node.setAttribute(attr_name, attr_value);
    }

    fn parseContent(self: XmlParser, xml: []const u8, pos: *usize, node: *XmlNode) !void {
        var text_start = pos.*;
        
        while (pos.* < xml.len) {
            if (xml[pos.*] == '<') {
                // Save any text content before this tag
                const text = xml[text_start..pos.*];
                if (!isWhitespace(text)) {
                    try node.setText(std.mem.trim(u8, text, " \t\n\r"));
                }

                // Check if it's a closing tag
                if (pos.* + 1 < xml.len and xml[pos.* + 1] == '/') {
                    return;
                }

                // Parse child element
                const child = try self.parseElement(xml, pos);
                try node.addChild(child);
                
                text_start = pos.*;
            } else {
                pos.* += 1;
            }
        }
    }

    fn parseClosingTag(self: XmlParser, xml: []const u8, pos: *usize, expected_name: []const u8) !bool {
        _ = self;
        
        if (pos.* + 2 >= xml.len or xml[pos.*] != '<' or xml[pos.* + 1] != '/') {
            return false;
        }
        pos.* += 2;

        const name_start = pos.*;
        while (pos.* < xml.len and xml[pos.*] != '>') {
            pos.* += 1;
        }
        const name = xml[name_start..pos.*];
        
        if (!std.mem.eql(u8, name, expected_name)) {
            return false;
        }

        pos.* += 1;
        return true;
    }
};

fn skipWhitespace(xml: []const u8, start: usize) usize {
    var pos = start;
    while (pos < xml.len and (xml[pos] == ' ' or xml[pos] == '\t' or xml[pos] == '\n' or xml[pos] == '\r')) {
        pos += 1;
    }
    return pos;
}

fn isWhitespace(text: []const u8) bool {
    for (text) |c| {
        if (c != ' ' and c != '\t' and c != '\n' and c != '\r') {
            return false;
        }
    }
    return true;
}

// Tests
test "basic XML parsing" {
    const allocator = std.testing.allocator;
    const parser = XmlParser.init(allocator);
    
    const xml =
        \\<record>
        \\  <title>Test Title</title>
        \\  <creator>Van Gogh</creator>
        \\</record>
    ;
    
    const root = try parser.parse(xml);
    defer {
        root.deinit();
        allocator.destroy(root);
    }
    
    try std.testing.expectEqualStrings("record", root.name);
    try std.testing.expectEqual(@as(usize, 2), root.children.items.len);
}

test "underscore suffix navigation" {
    const allocator = std.testing.allocator;
    const parser = XmlParser.init(allocator);
    
    const xml =
        \\<record>
        \\  <title></title>
        \\  <title>First Title</title>
        \\  <title>Second Title</title>
        \\</record>
    ;
    
    const root = try parser.parse(xml);
    defer {
        root.deinit();
        allocator.destroy(root);
    }
    
    // Test _ suffix gets first non-empty
    const result = root.get("title_");
    defer result.deinit();
    
    try std.testing.expect(result == .single);
    try std.testing.expectEqualStrings("First Title", result.asText());
}

test "attribute access" {
    const allocator = std.testing.allocator;
    const parser = XmlParser.init(allocator);
    
    const xml =
        \\<record id="123" type="artwork">
        \\  <title>Test</title>
        \\</record>
    ;
    
    const root = try parser.parse(xml);
    defer {
        root.deinit();
        allocator.destroy(root);
    }
    
    const id_result = root.get("@id");
    try std.testing.expectEqualStrings("123", id_result.asText());
    
    const type_result = root.get("@type");
    try std.testing.expectEqualStrings("artwork", type_result.asText());
}