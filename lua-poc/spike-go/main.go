// Command spike-go is the Lua mapping-engine feasibility spike's host
// (Task 8 of the deep-dive under
// .superpowers/sdd/2026-08-25-lua-mapping-engine-deepdive/).
//
// It runs a generated mapping.lua -- see LuaMappingGenerator in sip-core's
// test scope -- against one source record, inside gopher-lua, and prints the
// resulting RDF/XML on stdout:
//
//	spike-go [--engine DIR] mapping.lua record.xml > out.rdf.xml
//
// The engine directory defaults to $LUA_ENGINE_DIR, then to ../engine
// relative to this source tree's usual layout. Its *.lua files are loaded
// from disk through Lua's own `require`, by prepending the directory to
// package.path -- no embedding, so an engine edit is picked up without
// rebuilding the host, which is what a spike wants.
//
// gopher-lua is deliberately the host rather than PUC Lua: it is the Lua 5.1
// dialect the eventual engine would ship on, and it cannot load C modules
// (mapping-language-core.md section 9.3), so anything that runs here is
// provably pure Lua.
package main

import (
	"flag"
	"fmt"
	"os"
	"path/filepath"

	lua "github.com/yuin/gopher-lua"
)

func main() {
	engineDir := flag.String("engine", os.Getenv("LUA_ENGINE_DIR"),
		"directory holding the engine's Lua modules (node.lua, stdlib.lua, builder.lua, xml.lua, vendor/)")
	flag.Parse()

	if flag.NArg() != 2 {
		fmt.Fprintln(os.Stderr, "usage: spike-go [--engine DIR] <mapping.lua> <record.xml>")
		os.Exit(2)
	}
	mappingFile, recordFile := flag.Arg(0), flag.Arg(1)

	dir := *engineDir
	if dir == "" {
		dir = defaultEngineDir()
	}
	absDir, err := filepath.Abs(dir)
	if err != nil {
		fail("resolving engine dir: %v", err)
	}
	if _, err := os.Stat(filepath.Join(absDir, "node.lua")); err != nil {
		fail("engine dir %q does not look like the Lua engine (no node.lua): %v", absDir, err)
	}

	record, err := os.ReadFile(recordFile)
	if err != nil {
		fail("reading record: %v", err)
	}

	out, err := run(absDir, mappingFile, string(record))
	if err != nil {
		fail("%v", err)
	}
	fmt.Print(out)
}

// run loads mapping.lua in a fresh Lua state and calls the function it
// returns with the record XML, returning the RDF/XML it produces.
func run(engineDir, mappingFile, recordXML string) (string, error) {
	state := lua.NewState()
	defer state.Close()

	// Both patterns are needed: `?.lua` resolves require("node"), and
	// `?/init.lua` is Lua's usual companion. require("vendor.slaxml") turns
	// the dot into a path separator itself, so vendor/ needs no entry.
	pathSetup := fmt.Sprintf(`package.path = %q .. "/?.lua;" .. %q .. "/?/init.lua;" .. package.path`,
		engineDir, engineDir)
	if err := state.DoString(pathSetup); err != nil {
		return "", fmt.Errorf("setting package.path: %w", err)
	}

	if err := state.DoFile(mappingFile); err != nil {
		return "", fmt.Errorf("loading %s: %w", mappingFile, err)
	}
	mapper := state.Get(-1)
	state.Pop(1)
	if mapper.Type() != lua.LTFunction {
		return "", fmt.Errorf("%s returned %s, expected a function of one argument", mappingFile, mapper.Type())
	}

	if err := state.CallByParam(lua.P{Fn: mapper.(*lua.LFunction), NRet: 1, Protect: true},
		lua.LString(recordXML)); err != nil {
		return "", fmt.Errorf("running mapping: %w", err)
	}
	result := state.Get(-1)
	state.Pop(1)
	if result.Type() != lua.LTString {
		return "", fmt.Errorf("mapping returned %s, expected a string", result.Type())
	}
	return string(result.(lua.LString)), nil
}

// defaultEngineDir guesses ../engine relative to the running binary and, as a
// fallback, relative to the working directory -- enough for `go run .` from
// lua-poc/spike-go and for a built binary sitting next to it.
func defaultEngineDir() string {
	if exe, err := os.Executable(); err == nil {
		candidate := filepath.Join(filepath.Dir(exe), "..", "engine")
		if _, err := os.Stat(filepath.Join(candidate, "node.lua")); err == nil {
			return candidate
		}
	}
	return filepath.Join("..", "engine")
}

func fail(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "spike-go: "+format+"\n", args...)
	os.Exit(1)
}
