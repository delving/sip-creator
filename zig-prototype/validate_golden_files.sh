#!/bin/bash

# Script to validate golden files and test transformation accuracy

echo "=== SIP-Creator Zig Prototype Golden File Validation ==="
echo

# Check if golden files exist
if [ ! -f "../_data/input_vgm.xml" ]; then
    echo "ERROR: input_vgm.xml not found in _data directory"
    exit 1
fi

if [ ! -f "../_data/output_vgm.xml" ]; then
    echo "ERROR: output_vgm.xml not found in _data directory"
    exit 1
fi

echo "✓ Golden files found"
echo

# Validate XML syntax
echo "Validating XML syntax..."
if command -v xmllint &> /dev/null; then
    xmllint --noout ../_data/input_vgm.xml 2>&1
    if [ $? -eq 0 ]; then
        echo "✓ input_vgm.xml is valid XML"
    else
        echo "✗ input_vgm.xml has XML errors"
    fi
    
    xmllint --noout ../_data/output_vgm.xml 2>&1
    if [ $? -eq 0 ]; then
        echo "✓ output_vgm.xml is valid XML"
    else
        echo "✗ output_vgm.xml has XML errors"
    fi
else
    echo "⚠ xmllint not found, skipping XML validation"
fi
echo

# Build and run tests
echo "Building Zig prototype..."
zig build
if [ $? -ne 0 ]; then
    echo "✗ Build failed"
    exit 1
fi
echo "✓ Build successful"
echo

echo "Running tests..."
zig build test
if [ $? -ne 0 ]; then
    echo "✗ Tests failed"
    exit 1
fi
echo "✓ All tests passed"
echo

# Run the main program to demonstrate functionality
echo "Running mapping engine demo..."
./zig-out/bin/mapping-engine
echo

echo "=== Summary ==="
echo "The golden files provide a baseline for testing the mapping transformation:"
echo "- input_vgm.xml: Sample LIDO input from Van Gogh Museum"
echo "- output_vgm.xml: Expected EDM output after transformation"
echo
echo "The tests verify:"
echo "1. String manipulation functions (sanitize, sanitizeURI)"
echo "2. Template interpolation with variables"
echo "3. XML navigation patterns"
echo "4. Mapping execution logic"
echo
echo "Next steps:"
echo "1. Implement full XML parsing"
echo "2. Add Groovy expression parser"
echo "3. Complete DOM builder for output generation"
echo "4. Compare generated output with golden files"