# Complete Mapping Workflow Demo

This demo shows the entire SIP-Creator transformation workflow from source XML to target EDM format using the WASM-powered string functions.

## How to Run

1. Make sure the server is running:
   ```bash
   python3 -m http.server 8081
   ```

2. Open the demo in your browser:
   http://localhost:8081/wasm_complete_mapping_demo.html

## What the Demo Shows

### 1. Source Record (LIDO XML)
- Shows a Van Gogh Museum record in LIDO format
- Highlights the key values that will be extracted

### 2. Mapping Rules
The demo has three tabs showing different aspects:

#### Simple Mappings Tab
- Direct field-to-field mappings (e.g., title → dc:title)

#### Groovy Code Tab
- Complex transformations using string functions:
  - `sanitizeURI()` - URL-safe encoding
  - String interpolation (`${variable}`)
  - `replace()` function

#### Facts/Variables Tab
- Global variables available to all mappings
- Organization-specific configuration

### 3. Transformation Process
When you click "Run WASM Transformation":
1. Values are extracted from the source XML
2. WASM string functions process the data:
   - `sanitizeURI` converts IDs to URL-safe format
   - String interpolation builds URIs
   - `replace` function transforms rights statements
3. Output XML is generated in EDM format

### 4. Output Views
- **Formatted Output**: Pretty-printed EDM XML
- **Raw XML**: The actual XML that would be sent to Europeana
- **Debug Log**: Step-by-step transformation process

### 5. Record Definition
Shows the EDM schema requirements and validation rules

## Key Features Demonstrated

1. **String Functions** (implemented in WASM):
   - `sanitizeURI()` - Custom SIP-Creator function
   - `replace()` - Used 12,628 times in real mappings
   - String interpolation - Used 26,916 times

2. **Real-World Example**:
   - Based on actual Van Gogh Museum data
   - Shows typical LIDO to EDM transformation
   - Demonstrates common patterns from 521 analyzed mapping files

3. **Performance**:
   - All string operations run in WASM (6.8KB module)
   - Native performance for critical operations
   - Maintains compatibility with 15 years of mappings

## Technical Implementation

The demo uses:
- **WASM Module**: `mapping_engine.wasm` (6.8KB)
- **String Functions**: All implemented in Zig
- **Memory Management**: Fixed buffers with proper offset handling
- **UTF-8 Support**: Full Unicode compatibility

## Next Steps

The current implementation covers string operations (46,000+ uses). 
Still needed:
1. XML parsing in WASM
2. Collection operations (`*` operator - 3,047 uses)
3. Pattern matching (`matches`/`matcher` - 3,560 uses)
4. Full mapping engine integration