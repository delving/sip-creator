const std = @import("std");
const Allocator = std.mem.Allocator;

/// XML builder for generating EDM/ESE output
pub const XmlBuilder = struct {
    allocator: Allocator,
    buffer: std.ArrayList(u8),
    indent_level: usize,
    indent_string: []const u8,
    
    pub fn init(allocator: Allocator) XmlBuilder {
        return .{
            .allocator = allocator,
            .buffer = std.ArrayList(u8).init(allocator),
            .indent_level = 0,
            .indent_string = "  ",
        };
    }
    
    pub fn deinit(self: *XmlBuilder) void {
        self.buffer.deinit();
    }
    
    /// Get the built XML as a string
    pub fn toString(self: *XmlBuilder) ![]u8 {
        return try self.buffer.toOwnedSlice();
    }
    
    /// Write raw text
    pub fn write(self: *XmlBuilder, text: []const u8) !void {
        try self.buffer.appendSlice(text);
    }
    
    /// Write with current indentation
    pub fn writeIndented(self: *XmlBuilder, text: []const u8) !void {
        try self.writeIndent();
        try self.buffer.appendSlice(text);
    }
    
    /// Write newline
    pub fn writeLine(self: *XmlBuilder) !void {
        try self.buffer.append('\n');
    }
    
    /// Write indentation
    fn writeIndent(self: *XmlBuilder) !void {
        var i: usize = 0;
        while (i < self.indent_level) : (i += 1) {
            try self.buffer.appendSlice(self.indent_string);
        }
    }
    
    /// Start XML document
    pub fn startDocument(self: *XmlBuilder) !void {
        try self.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        try self.writeLine();
    }
    
    /// Start element with optional attributes
    pub fn startElement(self: *XmlBuilder, name: []const u8, attributes: ?[]const Attribute) !void {
        try self.writeIndented("<");
        try self.write(name);
        
        if (attributes) |attrs| {
            for (attrs) |attr| {
                try self.write(" ");
                try self.write(attr.name);
                try self.write("=\"");
                try self.writeEscaped(attr.value);
                try self.write("\"");
            }
        }
        
        try self.write(">");
        try self.writeLine();
        self.indent_level += 1;
    }
    
    /// End element
    pub fn endElement(self: *XmlBuilder, name: []const u8) !void {
        self.indent_level -= 1;
        try self.writeIndented("</");
        try self.write(name);
        try self.write(">");
        try self.writeLine();
    }
    
    /// Write a complete element with text content
    pub fn writeElement(self: *XmlBuilder, name: []const u8, content: []const u8, attributes: ?[]const Attribute) !void {
        try self.writeIndented("<");
        try self.write(name);
        
        if (attributes) |attrs| {
            for (attrs) |attr| {
                try self.write(" ");
                try self.write(attr.name);
                try self.write("=\"");
                try self.writeEscaped(attr.value);
                try self.write("\"");
            }
        }
        
        if (content.len == 0) {
            try self.write("/>");
            try self.writeLine();
        } else {
            try self.write(">");
            try self.writeEscaped(content);
            try self.write("</");
            try self.write(name);
            try self.write(">");
            try self.writeLine();
        }
    }
    
    /// Write text with XML escaping
    pub fn writeText(self: *XmlBuilder, text: []const u8) !void {
        try self.writeIndented("");
        try self.writeEscaped(text);
        try self.writeLine();
    }
    
    /// Write comment
    pub fn writeComment(self: *XmlBuilder, comment: []const u8) !void {
        try self.writeIndented("<!-- ");
        try self.write(comment);
        try self.write(" -->");
        try self.writeLine();
    }
    
    /// Escape XML special characters
    fn writeEscaped(self: *XmlBuilder, text: []const u8) !void {
        for (text) |char| {
            switch (char) {
                '<' => try self.write("&lt;"),
                '>' => try self.write("&gt;"),
                '&' => try self.write("&amp;"),
                '"' => try self.write("&quot;"),
                '\'' => try self.write("&apos;"),
                else => try self.buffer.append(char),
            }
        }
    }
    
    /// Build EDM namespace declarations
    pub fn writeEdmNamespaces(self: *XmlBuilder) !void {
        const namespaces = [_]Attribute{
            .{ .name = "xmlns:dc", .value = "http://purl.org/dc/elements/1.1/" },
            .{ .name = "xmlns:dcterms", .value = "http://purl.org/dc/terms/" },
            .{ .name = "xmlns:edm", .value = "http://www.europeana.eu/schemas/edm/" },
            .{ .name = "xmlns:ore", .value = "http://www.openarchives.org/ore/terms/" },
            .{ .name = "xmlns:skos", .value = "http://www.w3.org/2004/02/skos/core#" },
            .{ .name = "xmlns:rdf", .value = "http://www.w3.org/1999/02/22-rdf-syntax-ns#" },
        };
        
        try self.startElement("edm:RDF", &namespaces);
    }
};

pub const Attribute = struct {
    name: []const u8,
    value: []const u8,
};

// Tests
test "basic XML building" {
    const allocator = std.testing.allocator;
    var builder = XmlBuilder.init(allocator);
    defer builder.deinit();
    
    try builder.startDocument();
    try builder.startElement("root", null);
    try builder.writeElement("title", "Test & Title", null);
    
    const attrs = [_]Attribute{
        .{ .name = "lang", .value = "en" },
        .{ .name = "type", .value = "main" },
    };
    try builder.writeElement("description", "A <test> description", &attrs);
    
    try builder.endElement("root");
    
    const xml = try builder.toString();
    defer allocator.free(xml);
    
    try std.testing.expect(std.mem.indexOf(u8, xml, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>") != null);
    try std.testing.expect(std.mem.indexOf(u8, xml, "<title>Test &amp; Title</title>") != null);
    try std.testing.expect(std.mem.indexOf(u8, xml, "&lt;test&gt;") != null);
}

test "EDM structure" {
    const allocator = std.testing.allocator;
    var builder = XmlBuilder.init(allocator);
    defer builder.deinit();
    
    try builder.startDocument();
    try builder.writeEdmNamespaces();
    
    // ProvidedCHO
    const cho_attrs = [_]Attribute{
        .{ .name = "rdf:about", .value = "http://example.org/cho/123" },
    };
    try builder.startElement("edm:ProvidedCHO", &cho_attrs);
    try builder.writeElement("dc:title", "Test Title", null);
    try builder.writeElement("dc:creator", "Test Creator", null);
    try builder.endElement("edm:ProvidedCHO");
    
    // Aggregation
    const agg_attrs = [_]Attribute{
        .{ .name = "rdf:about", .value = "http://example.org/aggregation/123" },
    };
    try builder.startElement("ore:Aggregation", &agg_attrs);
    
    const cho_ref = [_]Attribute{
        .{ .name = "rdf:resource", .value = "http://example.org/cho/123" },
    };
    try builder.writeElement("edm:aggregatedCHO", "", &cho_ref);
    try builder.endElement("ore:Aggregation");
    
    try builder.endElement("edm:RDF");
    
    const xml = try builder.toString();
    defer allocator.free(xml);
    
    try std.testing.expect(std.mem.indexOf(u8, xml, "xmlns:edm=") != null);
    try std.testing.expect(std.mem.indexOf(u8, xml, "<edm:ProvidedCHO") != null);
    try std.testing.expect(std.mem.indexOf(u8, xml, "<ore:Aggregation") != null);
}