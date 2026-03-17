package main

import (
	"fmt"
	"io/ioutil"
	"log"

	"github.com/wasmerio/wasmer-go/wasmer"
)

// MappingEngine wraps the WASM mapping engine
type MappingEngine struct {
	instance *wasmer.Instance
	memory   *wasmer.Memory
	
	// Function exports
	allocateMemory    wasmer.NativeFunction
	freeMemory        wasmer.NativeFunction
	sanitizeString    wasmer.NativeFunction
	sanitizeURI       wasmer.NativeFunction
	getStringLength   wasmer.NativeFunction
	executeExpression wasmer.NativeFunction
}

// NewMappingEngine creates a new mapping engine from WASM bytes
func NewMappingEngine(wasmPath string) (*MappingEngine, error) {
	wasmBytes, err := ioutil.ReadFile(wasmPath)
	if err != nil {
		return nil, fmt.Errorf("failed to read WASM file: %w", err)
	}

	engine := wasmer.NewEngine()
	store := wasmer.NewStore(engine)
	
	// Compile the module
	module, err := wasmer.NewModule(store, wasmBytes)
	if err != nil {
		return nil, fmt.Errorf("failed to compile module: %w", err)
	}

	// Create import object (empty for now)
	importObject := wasmer.NewImportObject()
	
	// Instantiate the module
	instance, err := wasmer.NewInstance(module, importObject)
	if err != nil {
		return nil, fmt.Errorf("failed to instantiate module: %w", err)
	}

	// Get memory export
	memory, err := instance.Exports.GetMemory("memory")
	if err != nil {
		return nil, fmt.Errorf("failed to get memory: %w", err)
	}

	// Get function exports
	allocateMemory, err := instance.Exports.GetFunction("allocateMemory")
	if err != nil {
		return nil, fmt.Errorf("failed to get allocateMemory: %w", err)
	}

	freeMemory, err := instance.Exports.GetFunction("freeMemory")
	if err != nil {
		return nil, fmt.Errorf("failed to get freeMemory: %w", err)
	}

	sanitizeString, err := instance.Exports.GetFunction("sanitizeString")
	if err != nil {
		return nil, fmt.Errorf("failed to get sanitizeString: %w", err)
	}

	sanitizeURI, err := instance.Exports.GetFunction("sanitizeURI")
	if err != nil {
		return nil, fmt.Errorf("failed to get sanitizeURI: %w", err)
	}

	getStringLength, err := instance.Exports.GetFunction("getStringLength")
	if err != nil {
		return nil, fmt.Errorf("failed to get getStringLength: %w", err)
	}

	executeExpression, err := instance.Exports.GetFunction("executeExpression")
	if err != nil {
		return nil, fmt.Errorf("failed to get executeExpression: %w", err)
	}

	return &MappingEngine{
		instance:          instance,
		memory:           memory,
		allocateMemory:   allocateMemory,
		freeMemory:       freeMemory,
		sanitizeString:   sanitizeString,
		sanitizeURI:      sanitizeURI,
		getStringLength:  getStringLength,
		executeExpression: executeExpression,
	}, nil
}

// Helper to write string to WASM memory
func (m *MappingEngine) writeString(s string) (int32, int32, error) {
	bytes := []byte(s)
	
	// Allocate memory in WASM
	result, err := m.allocateMemory(len(bytes) + 1)
	if err != nil {
		return 0, 0, err
	}
	
	ptr := result.(int32)
	
	// Write bytes to memory
	data := m.memory.Data()
	copy(data[ptr:], bytes)
	data[ptr+int32(len(bytes))] = 0 // null terminator
	
	return ptr, int32(len(bytes)), nil
}

// Helper to read string from WASM memory
func (m *MappingEngine) readString(ptr int32) (string, error) {
	// Get string length
	result, err := m.getStringLength(ptr)
	if err != nil {
		return "", err
	}
	
	length := result.(int32)
	data := m.memory.Data()
	bytes := data[ptr : ptr+length]
	
	// Free the memory
	_, _ = m.freeMemory(ptr)
	
	return string(bytes), nil
}

// Sanitize removes extra whitespace and newlines
func (m *MappingEngine) Sanitize(input string) (string, error) {
	ptr, len, err := m.writeString(input)
	if err != nil {
		return "", err
	}
	defer m.freeMemory(ptr)
	
	result, err := m.sanitizeString(ptr, len)
	if err != nil {
		return "", err
	}
	
	return m.readString(result.(int32))
}

// SanitizeURI encodes special characters for URIs
func (m *MappingEngine) SanitizeURI(input string) (string, error) {
	ptr, len, err := m.writeString(input)
	if err != nil {
		return "", err
	}
	defer m.freeMemory(ptr)
	
	result, err := m.sanitizeURI(ptr, len)
	if err != nil {
		return "", err
	}
	
	return m.readString(result.(int32))
}

// InterpolateTemplate performs string interpolation
func (m *MappingEngine) InterpolateTemplate(template, uniqueID, baseURL, spec string) (string, error) {
	// Write all strings to WASM memory
	templatePtr, templateLen, err := m.writeString(template)
	if err != nil {
		return "", err
	}
	defer m.freeMemory(templatePtr)
	
	uniqueIDPtr, uniqueIDLen, err := m.writeString(uniqueID)
	if err != nil {
		return "", err
	}
	defer m.freeMemory(uniqueIDPtr)
	
	baseURLPtr, baseURLLen, err := m.writeString(baseURL)
	if err != nil {
		return "", err
	}
	defer m.freeMemory(baseURLPtr)
	
	specPtr, specLen, err := m.writeString(spec)
	if err != nil {
		return "", err
	}
	defer m.freeMemory(specPtr)
	
	// Execute expression
	result, err := m.executeExpression(
		templatePtr, templateLen,
		uniqueIDPtr, uniqueIDLen,
		baseURLPtr, baseURLLen,
		specPtr, specLen,
	)
	if err != nil {
		return "", err
	}
	
	return m.readString(result.(int32))
}

func main() {
	// Create mapping engine
	engine, err := NewMappingEngine("wasm/mapping-engine.wasm")
	if err != nil {
		log.Fatal(err)
	}

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
	interpolated, err := engine.InterpolateTemplate(
		"${baseUrl}/resource/${spec}/${_uniqueIdentifier}",
		"12345",
		"http://example.com",
		"test-collection",
	)
	if err != nil {
		log.Fatal(err)
	}
	fmt.Printf("Interpolated: '%s'\n", interpolated)
}