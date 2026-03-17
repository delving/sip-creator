# Framed JSON-LD Output Design

## Problem
SIP-Creator currently outputs flat/unframed JSON-LD, which is a simple list of triples. Users want human-readable, nested JSON-LD that shows the RDF structure in a more friendly way.

## Solution
Add a new "JSONLD FRAMED" output format that uses Jena's JSON-LD framing to produce nested, hierarchical output.

## Architecture

### 1. JenaHelper.java
- Add overloaded `convertRDF()` method that accepts a `Map<String, Object>` frame parameter
- When frame is provided, use `JsonLDWriteContext.setFrame()` with `RDFFormat.JSONLD_FRAME_PRETTY`
- Return formatted JSON similar to compact output

### 2. OutputFrame.java
- Add "JSONLD,FRAMED" to the output format dropdown
- Handle selection in the action listener, mapping to new format handling

### 3. SipModel.java
- Add "JSONLD_FRAMED" case in `getRDFFormat()` method
- Return appropriate RDFFormat or handle via special case

### 4. MappingCompileModel.java
- Pass frame parameter to `JenaHelper.convertRDF()` when format is framed

## Default Frame
Simple type-based frame:
```json
{
  "@type": "Thing"
}
```
This groups resources by their RDF type, producing readable nested output.

## Testing
- Create `JenaHelperTest.java` with tests for:
  - Framed output produces nested JSON structure
  - Framed output differs from compact (flat) output
  - Edge cases: empty model, model with no types
