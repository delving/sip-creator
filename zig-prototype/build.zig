const std = @import("std");

pub fn build(b: *std.Build) void {
    const target = b.standardTargetOptions(.{});
    const optimize = b.standardOptimizeOption(.{});

    // Native executable for testing
    const exe = b.addExecutable(.{
        .name = "mapping-engine",
        .root_source_file = b.path("mapping_engine.zig"),
        .target = target,
        .optimize = optimize,
    });
    b.installArtifact(exe);

    // Mapping converter executable
    const converter = b.addExecutable(.{
        .name = "mapping-converter",
        .root_source_file = b.path("mapping_xml_to_groovy.zig"),
        .target = target,
        .optimize = optimize,
    });
    b.installArtifact(converter);

    // WebAssembly library
    const wasm_target = b.resolveTargetQuery(.{
        .cpu_arch = .wasm32,
        .os_tag = .freestanding,
    });
    
    const wasm_lib = b.addExecutable(.{
        .name = "mapping-engine",
        .root_source_file = b.path("mapping_engine_wasm.zig"),
        .target = wasm_target,
        .optimize = .ReleaseSmall,
    });
    wasm_lib.entry = .disabled;
    wasm_lib.rdynamic = true;
    wasm_lib.export_memory = true;
    
    const wasm_install = b.addInstallArtifact(wasm_lib, .{
        .dest_dir = .{ .override = .{ .custom = "wasm" } },
    });
    
    const wasm_step = b.step("wasm", "Build WebAssembly module");
    wasm_step.dependOn(&wasm_install.step);

    // Run command
    const run_cmd = b.addRunArtifact(exe);
    run_cmd.step.dependOn(b.getInstallStep());
    if (b.args) |args| {
        run_cmd.addArgs(args);
    }
    const run_step = b.step("run", "Run the app");
    run_step.dependOn(&run_cmd.step);

    // Tests
    const unit_tests = b.addTest(.{
        .root_source_file = b.path("mapping_engine.zig"),
        .target = target,
        .optimize = optimize,
    });
    const run_unit_tests = b.addRunArtifact(unit_tests);
    
    const mapping_tests = b.addTest(.{
        .root_source_file = b.path("mapping_test.zig"),
        .target = target,
        .optimize = optimize,
    });
    const run_mapping_tests = b.addRunArtifact(mapping_tests);
    
    const full_tests = b.addTest(.{
        .root_source_file = b.path("full_mapping_test.zig"),
        .target = target,
        .optimize = optimize,
    });
    const run_full_tests = b.addRunArtifact(full_tests);
    
    const test_step = b.step("test", "Run unit tests");
    test_step.dependOn(&run_unit_tests.step);
    test_step.dependOn(&run_mapping_tests.step);
    test_step.dependOn(&run_full_tests.step);
}