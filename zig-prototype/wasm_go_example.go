package main

import (
	"fmt"
	"io/ioutil"
	"log"

	"github.com/wasmerio/wasmer-go/wasmer"
)

// MappingEngineMinimal wraps the minimal WASM mapping engine
type MappingEngineMinimal struct {
	instance *wasmer.Instance
	memory   *wasmer.Memory

	// Function exports
	wasmSanitize         wasmer.NativeFunction
	wasmSanitizeURI      wasmer.NativeFunction
	wasmInterpolate      wasmer.NativeFunction
	wasmExtractAttribute wasmer.NativeFunction
	wasmGetResultBuffer  wasmer.NativeFunction
	wasmGetVersion       wasmer.NativeFunction
	wasmReset            wasmer.NativeFunction
}

// NewMappingEngineMinimal creates a new minimal mapping engine
func NewMappingEngineMinimal(wasmPath string) (*MappingEngineMinimal, error) {
	wasmBytes, err := ioutil.ReadFile(wasmPath)
	if err != nil {
		return nil, fmt.Errorf("failed to read WASM file: %w", err)
	}

	engine := wasmer.NewEngine()
	store := wasmer.NewStore(engine)

	module, err := wasmer.NewModule(store, wasmBytes)
	if err != nil {
		return nil, fmt.Errorf("failed to compile module: %w", err)
	}

	importObject := wasmer.NewImportObject()
	instance, err := wasmer.NewInstance(module, importObject)
	if err != nil {
		return nil, fmt.Errorf("failed to instantiate module: %w", err)
	}

	memory, err := instance.Exports.GetMemory("memory")
	if err != nil {
		return nil, fmt.Errorf("failed to get memory: %w", err)
	}

	// Get all function exports
	wasmSanitize, _ := instance.Exports.GetFunction("wasmSanitize")
	wasmSanitizeURI, _ := instance.Exports.GetFunction("wasmSanitizeURI")
	wasmInterpolate, _ := instance.Exports.GetFunction("wasmInterpolate")
	wasmExtractAttribute, _ := instance.Exports.GetFunction("wasmExtractAttribute")
	wasmGetResultBuffer, _ := instance.Exports.GetFunction("wasmGetResultBuffer")
	wasmGetVersion, _ := instance.Exports.GetFunction("wasmGetVersion")
	wasmReset, _ := instance.Exports.GetFunction("wasmReset")

	return &MappingEngineMinimal{
		instance:             instance,
		memory:              memory,
		wasmSanitize:        wasmSanitize,
		wasmSanitizeURI:     wasmSanitizeURI,
		wasmInterpolate:     wasmInterpolate,
		wasmExtractAttribute: wasmExtractAttribute,
		wasmGetResultBuffer: wasmGetResultBuffer,
		wasmGetVersion:      wasmGetVersion,
		wasmReset:           wasmReset,
	}, nil
}

// Helper to write string to WASM memory
func (m *MappingEngineMinimal) writeString(s string, offset int32) int32 {
	bytes := []byte(s)
	data := m.memory.Data()
	copy(data[offset:], bytes)
	return int32(len(bytes))
}

// Helper to read result from WASM memory
func (m *MappingEngineMinimal) readResult(length int32) string {
	result, _ := m.wasmGetResultBuffer()
	ptr := result.(int32)
	data := m.memory.Data()
	return string(data[ptr : ptr+length])
}

// GetVersion returns the WASM module version
func (m *MappingEngineMinimal) GetVersion() (string, error) {
	result, err := m.wasmGetVersion()
	if err != nil {
		return "", err
	}
	length := result.(int32)
	return m.readResult(length), nil
}

// Sanitize removes extra whitespace
func (m *MappingEngineMinimal) Sanitize(input string) (string, error) {
	inputLen := m.writeString(input, 0)
	
	result, err := m.wasmSanitize(int32(0), inputLen)
	if err != nil {
		return "", err
	}
	
	length := result.(int32)
	return m.readResult(length), nil
}

// SanitizeURI encodes special characters
func (m *MappingEngineMinimal) SanitizeURI(input string) (string, error) {
	inputLen := m.writeString(input, 0)
	
	result, err := m.wasmSanitizeURI(int32(0), inputLen)
	if err != nil {
		return "", err
	}
	
	length := result.(int32)
	return m.readResult(length), nil
}

// Interpolate performs simple template variable substitution
func (m *MappingEngineMinimal) Interpolate(template, key1, val1, key2, val2 string) (string, error) {
	// Write all strings to memory at different offsets
	offset := int32(0)
	templateLen := m.writeString(template, offset)
	offset += templateLen + 1
	
	key1Len := m.writeString(key1, offset)
	key1Offset := offset
	offset += key1Len + 1
	
	val1Len := m.writeString(val1, offset)
	val1Offset := offset
	offset += val1Len + 1
	
	key2Len := m.writeString(key2, offset)
	key2Offset := offset
	offset += key2Len + 1
	
	val2Len := m.writeString(val2, offset)
	val2Offset := offset
	
	result, err := m.wasmInterpolate(
		0, templateLen,
		key1Offset, key1Len,
		val1Offset, val1Len,
		key2Offset, key2Len,
		val2Offset, val2Len,
	)
	if err != nil {
		return "", err
	}
	
	length := result.(int32)
	return m.readResult(length), nil
}

// ExtractAttribute extracts an attribute value from XML
func (m *MappingEngineMinimal) ExtractAttribute(xml, attrName string) (string, error) {
	xmlLen := m.writeString(xml, 0)
	attrLen := m.writeString(attrName, xmlLen+1)
	
	result, err := m.wasmExtractAttribute(0, xmlLen, xmlLen+1, attrLen)
	if err != nil {
		return "", err
	}
	
	length := result.(int32)
	return m.readResult(length), nil
}

func main() {
	// Create mapping engine
	engine, err := NewMappingEngineMinimal("zig-out/wasm/mapping-engine.wasm")
	if err != nil {
		log.Fatal(err)
	}

	// Get version
	version, err := engine.GetVersion()
	if err != nil {
		log.Fatal(err)
	}
	fmt.Printf("WASM Engine Version: %s\n\n", version)

	// Test sanitize
	sanitized, err := engine.Sanitize("  Hello   \n\n  World  \t ")
	if err != nil {
		log.Fatal(err)
	}
	fmt.Printf("Sanitized: '%s'\n", sanitized)

	// Test URI encoding
	encoded, err := engine.SanitizeURI("hello [world] test")
	if err != nil {
		log.Fatal(err)
	}
	fmt.Printf("URI encoded: '%s'\n", encoded)

	// Test template interpolation
	interpolated, err := engine.Interpolate(
		"${baseUrl}/resource/${spec}",
		"baseUrl", "http://example.com",
		"spec", "test-collection",
	)
	if err != nil {
		log.Fatal(err)
	}
	fmt.Printf("Interpolated: '%s'\n", interpolated)

	// Test XML attribute extraction
	xml := `<input id="F474"><title>Starry Night</title></input>`
	id, err := engine.ExtractAttribute(xml, "id")
	if err != nil {
		log.Fatal(err)
	}
	fmt.Printf("Extracted ID: '%s'\n", id)

	// Simulate a mapping transformation
	fmt.Println("\n=== Simulated EDM Mapping ===")
	
	// Extract unique identifier
	uniqueID, _ := engine.ExtractAttribute(xml, "id")
	fmt.Printf("Unique ID: %s\n", uniqueID)
	
	// Build aggregation URI
	aggregationURI, _ := engine.Interpolate(
		"${baseUrl}/resource/aggregation/${spec}/F474",
		"baseUrl", "http://data.collectienederland.nl",
		"spec", "van-gogh-museum",
	)
	fmt.Printf("Aggregation URI: %s\n", aggregationURI)
	
	// Clean title
	messyTitle := "  Starry   Night  "
	cleanTitle, _ := engine.Sanitize(messyTitle)
	fmt.Printf("Clean title: '%s'\n", cleanTitle)
}