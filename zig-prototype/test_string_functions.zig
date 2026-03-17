const std = @import("std");
const StringFunctions = @import("string_functions.zig").StringFunctions;

pub fn main() !void {
    const allocator = std.heap.page_allocator;
    const sf = StringFunctions.init(allocator);
    
    // Test replaceAll
    {
        const input = "hello world world";
        const result = try sf.replaceAll(input, "world", "universe");
        defer allocator.free(result);
        std.debug.print("replaceAll: '{s}' -> '{s}'\n", .{ input, result });
    }
    
    // Test replace
    {
        const input = "hello world world";
        const result = try sf.replace(input, "world", "universe");
        defer allocator.free(result);
        std.debug.print("replace: '{s}' -> '{s}'\n", .{ input, result });
    }
    
    // Test with backslashes
    {
        const input = "C:\\path\\to\\file.txt";
        const result = try sf.replaceAll(input, "\\", "/");
        defer allocator.free(result);
        std.debug.print("backslash replace: '{s}' -> '{s}'\n", .{ input, result });
    }
    
    // Test capitalize
    {
        const input = "van gogh";
        const result = try sf.capitalize(input);
        defer allocator.free(result);
        std.debug.print("capitalize: '{s}' -> '{s}'\n", .{ input, result });
    }
}