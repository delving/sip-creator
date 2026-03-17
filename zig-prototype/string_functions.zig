const std = @import("std");
const Allocator = std.mem.Allocator;

/// String functions based on actual usage analysis from 521 mapping files
/// These cover the most frequently used operations (46,000+ calls)

pub const StringFunctions = struct {
    allocator: Allocator,

    pub fn init(allocator: Allocator) StringFunctions {
        return .{ .allocator = allocator };
    }

    /// Replace all occurrences of a pattern (13,606 uses)
    /// For now, handles literal strings. TODO: Add regex support
    pub fn replaceAll(self: StringFunctions, str: []const u8, search: []const u8, replacement: []const u8) ![]u8 {
        if (search.len == 0) return try self.allocator.dupe(u8, str);
        
        // Count occurrences
        var count: usize = 0;
        var pos: usize = 0;
        while (std.mem.indexOf(u8, str[pos..], search)) |index| {
            count += 1;
            pos = pos + index + search.len;
        }
        
        if (count == 0) return try self.allocator.dupe(u8, str);
        
        // Calculate new size
        const new_len = str.len + (replacement.len * count) - (search.len * count);
        var result = try self.allocator.alloc(u8, new_len);
        
        // Perform replacement
        var src_pos: usize = 0;
        var dst_pos: usize = 0;
        while (std.mem.indexOf(u8, str[src_pos..], search)) |index| {
            const abs_index = src_pos + index;
            
            // Copy prefix
            @memcpy(result[dst_pos..][0..index], str[src_pos..abs_index]);
            dst_pos += index;
            
            // Copy replacement
            @memcpy(result[dst_pos..][0..replacement.len], replacement);
            dst_pos += replacement.len;
            
            src_pos = abs_index + search.len;
        }
        
        // Copy remaining
        if (src_pos < str.len) {
            @memcpy(result[dst_pos..], str[src_pos..]);
        }
        
        return result;
    }

    /// Replace first occurrence (12,628 uses)
    pub fn replace(self: StringFunctions, str: []const u8, search: []const u8, replacement: []const u8) ![]u8 {
        if (search.len == 0) return try self.allocator.dupe(u8, str);
        
        if (std.mem.indexOf(u8, str, search)) |index| {
            const new_len = str.len - search.len + replacement.len;
            var result = try self.allocator.alloc(u8, new_len);
            
            // Copy prefix
            @memcpy(result[0..index], str[0..index]);
            
            // Copy replacement
            @memcpy(result[index..][0..replacement.len], replacement);
            
            // Copy suffix
            const suffix_start = index + search.len;
            @memcpy(result[index + replacement.len..], str[suffix_start..]);
            
            return result;
        }
        
        return try self.allocator.dupe(u8, str);
    }

    /// Capitalize first letter (2,278 uses)
    pub fn capitalize(self: StringFunctions, str: []const u8) ![]u8 {
        if (str.len == 0) return try self.allocator.dupe(u8, str);
        
        var result = try self.allocator.dupe(u8, str);
        
        // Find first letter (skip whitespace)
        for (result, 0..) |*char, i| {
            if (std.ascii.isAlphabetic(char.*)) {
                result[i] = std.ascii.toUpper(char.*);
                break;
            }
        }
        
        return result;
    }

    /// Split string by delimiter (1,707 uses)
    pub fn split(self: StringFunctions, str: []const u8, delimiter: []const u8) ![][]u8 {
        if (delimiter.len == 0) {
            var result = try self.allocator.alloc([]u8, 1);
            result[0] = try self.allocator.dupe(u8, str);
            return result;
        }
        
        // Count occurrences
        var count: usize = 1;
        var it = std.mem.tokenizeAny(u8, str, delimiter);
        while (it.next()) |_| {
            count += 1;
        }
        
        // Allocate result
        var result = try self.allocator.alloc([]u8, count);
        
        // Split
        it = std.mem.tokenizeAny(u8, str, delimiter);
        var i: usize = 0;
        while (it.next()) |part| {
            result[i] = try self.allocator.dupe(u8, part);
            i += 1;
        }
        
        return result[0..i];
    }

    /// Convert to string (2,954 uses)
    pub fn toString(self: StringFunctions, value: anytype) ![]u8 {
        const T = @TypeOf(value);
        
        return switch (@typeInfo(T)) {
            .Int, .Float => try std.fmt.allocPrint(self.allocator, "{}", .{value}),
            .Pointer => |ptr| switch (ptr.size) {
                .Slice => try self.allocator.dupe(u8, value),
                else => try std.fmt.allocPrint(self.allocator, "{any}", .{value}),
            },
            else => try std.fmt.allocPrint(self.allocator, "{any}", .{value}),
        };
    }

    /// Parse integer (1,006 uses)
    pub fn toInteger(self: StringFunctions, str: []const u8) !i32 {
        _ = self;
        const trimmed = std.mem.trim(u8, str, " \t\n\r");
        return try std.fmt.parseInt(i32, trimmed, 10);
    }

    /// Check if string matches pattern (2,140 uses)
    /// TODO: Implement full regex support
    pub fn matches(self: StringFunctions, str: []const u8, pattern: []const u8) bool {
        _ = self;
        
        // For now, handle simple patterns
        if (std.mem.eql(u8, pattern, ".*")) return true;
        
        // Handle year pattern: ([0-9]{4})
        if (std.mem.eql(u8, pattern, "([0-9]{4})") or std.mem.eql(u8, pattern, "[0-9]{4}")) {
            if (str.len != 4) return false;
            for (str) |c| {
                if (!std.ascii.isDigit(c)) return false;
            }
            return true;
        }
        
        // Default to simple equality
        return std.mem.eql(u8, str, pattern);
    }

    /// Trim whitespace (35 uses but likely underreported)
    pub fn trim(self: StringFunctions, str: []const u8) ![]u8 {
        const trimmed = std.mem.trim(u8, str, " \t\n\r");
        return try self.allocator.dupe(u8, trimmed);
    }

    /// Convert to lowercase (48 uses)
    pub fn toLowerCase(self: StringFunctions, str: []const u8) ![]u8 {
        var result = try self.allocator.alloc(u8, str.len);
        for (str, 0..) |char, i| {
            result[i] = std.ascii.toLower(char);
        }
        return result;
    }

    /// Convert to uppercase (3 uses)
    pub fn toUpperCase(self: StringFunctions, str: []const u8) ![]u8 {
        var result = try self.allocator.alloc(u8, str.len);
        for (str, 0..) |char, i| {
            result[i] = std.ascii.toUpper(char);
        }
        return result;
    }

    /// Find index of substring (264 uses)
    pub fn indexOf(self: StringFunctions, str: []const u8, search: []const u8) ?usize {
        _ = self;
        return std.mem.indexOf(u8, str, search);
    }

    /// Check if string contains substring (55 uses)
    pub fn contains(self: StringFunctions, str: []const u8, search: []const u8) bool {
        _ = self;
        return std.mem.indexOf(u8, str, search) != null;
    }

    /// Check if string starts with prefix (4 uses)
    pub fn startsWith(self: StringFunctions, str: []const u8, prefix: []const u8) bool {
        _ = self;
        return std.mem.startsWith(u8, str, prefix);
    }

    /// Check if string ends with suffix (3 uses)
    pub fn endsWith(self: StringFunctions, str: []const u8, suffix: []const u8) bool {
        _ = self;
        return std.mem.endsWith(u8, str, suffix);
    }
    
    /// Sanitize string for URI usage (custom SIP-Creator function)
    pub fn sanitizeURI(self: StringFunctions, str: []const u8) []const u8 {
        _ = self;
        // For now, return as-is. Full implementation would encode special chars
        return str;
    }
    
    /// Sanitize string by normalizing whitespace (custom SIP-Creator function)
    pub fn sanitize(self: StringFunctions, str: []const u8) []const u8 {
        _ = self;
        // For now, return as-is. Full implementation would normalize spaces
        return str;
    }
};

// Tests based on actual usage patterns
test "replaceAll" {
    const allocator = std.testing.allocator;
    const sf = StringFunctions.init(allocator);
    
    // Common pattern: remove path prefix
    const result1 = try sf.replaceAll("C:\\\\path\\\\to\\\\file.txt", ".*\\\\", "");
    defer allocator.free(result1);
    try std.testing.expectEqualStrings("file.txt", result1);
    
    // Common pattern: normalize spaces
    const result2 = try sf.replaceAll("hello   world", "  ", " ");
    defer allocator.free(result2);
    try std.testing.expectEqualStrings("hello world", result2);
}

test "capitalize" {
    const allocator = std.testing.allocator;
    const sf = StringFunctions.init(allocator);
    
    const result = try sf.capitalize("van gogh");
    defer allocator.free(result);
    try std.testing.expectEqualStrings("Van gogh", result);
}

test "split" {
    const allocator = std.testing.allocator;
    const sf = StringFunctions.init(allocator);
    
    const parts = try sf.split("2023-11-25", "-");
    defer {
        for (parts) |part| allocator.free(part);
        allocator.free(parts);
    }
    
    try std.testing.expectEqual(@as(usize, 3), parts.len);
    try std.testing.expectEqualStrings("2023", parts[0]);
    try std.testing.expectEqualStrings("11", parts[1]);
    try std.testing.expectEqualStrings("25", parts[2]);
}

test "year pattern matching" {
    const sf = StringFunctions.init(std.testing.allocator);
    
    try std.testing.expect(sf.matches("2023", "([0-9]{4})"));
    try std.testing.expect(!sf.matches("23", "([0-9]{4})"));
    try std.testing.expect(!sf.matches("abcd", "([0-9]{4})"));
}