# SIP-Creator Mapping Hints and Functions Reference

This document provides a comprehensive reference for mapping functions and utilities available when creating mappings in SIP-Creator.

## Table of Contents
- [GroovyNode Navigation](#groovynode-navigation)
- [Built-in Functions](#built-in-functions)
- [MappingCategory DSL Functions](#mappingcategory-dsl-functions)
- [List Operations](#list-operations)
- [Mapping Variables](#mapping-variables)
- [Common Mapping Patterns](#common-mapping-patterns)

## GroovyNode Navigation

When working with XML nodes in mappings, you use `GroovyNode` objects. These provide flexible navigation through nested structures:

### The `get()` method

```groovy
node.get(String key)
```

This method supports four search patterns:

1. **Attribute lookup** - Use `@` prefix
   ```groovy
   _input['@id']  // returns the value of the "id" attribute
   ```

2. **Get all children** - Use `*`
   ```groovy
   _input['*']  // returns all child nodes
   ```

3. **First matching element** - Use `_` suffix
   ```groovy
   _input['title_']  // returns first "title" element with non-empty text
   ```

4. **All matching elements** - Use name without suffix
   ```groovy
   _input['title']  // returns all "title" child elements as a list
   ```

### Other navigation methods

- **`findFirstMatch(String name)`** - Recursively searches for first node with given name and non-empty text
- **`getValueNodes(String name)`** - Recursively collects ALL nodes with given name that have values
- **`getByName(String name)`** - Returns immediate children matching the name (non-recursive)

## Built-in Functions

### Date Functions

```groovy
// Calculate age between two dates
calculateAge(String birthDate, String deathDate, 
            boolean automaticDateReordering = false, 
            boolean ignoreErrors = false)

// Get age range like "20 – 29"
calculateAgeRange(String birthDate, String deathDate,
                 boolean automaticDateReordering = false,
                 boolean ignoreErrors = false)
```

### Record Control Functions

```groovy
// Discard current record with reason
discard("Reason for discarding")

// Conditionally discard
discardIf(condition, "Reason")
discardIfNot(condition, "Reason")
```

## MappingCategory DSL Functions

These functions are available within the `use (MappingCategory)` block:

### String Manipulation

```groovy
// Remove newlines and multiple spaces
string.sanitize()

// Encode special characters for URIs
// Spaces → %20, [ ] → %5B %5D, \ → %5C
string.sanitizeURI()

// Pattern-based replacement with caching
string.replaceAll(regex, replacement)

// Split string
string.split(regex)
string.split(regex, limit)

// Check pattern match
string.matches(regex)
```

### Node Text Operations

```groovy
// These work on GroovyNode objects
node.indexOf(string)           // Find index of string in node text
node.substring(from)           // Get substring from index
node.substring(from, to)       // Get substring range
node.contains(string)          // Check if text contains string
node.startsWith(string)        // Check if text starts with string
node.endsWith(string)          // Check if text ends with string
node.replaceAll(from, to)      // Replace pattern in text
```

## List Operations

SIP-Creator provides special operators for working with lists of nodes:

```groovy
// Concatenate lists
list1 + list2

// Create map from two lists (tuple operation)
keys | values

// Apply closure to each element
list * { element ->
    element.sanitize()
}

// Split nodes by delimiter
nodes * ','

// Apply closure to first element only
list ** { firstElement ->
    firstElement.toUpperCase()
}

// Apply closure once with all elements
list >> { allElements ->
    allElements.join(", ")
}
```

## Mapping Variables

These variables are available in mapping context:

- **`_facts`** - Access to mapping facts/configuration
- **`_optLookup`** - Access to option lookups/vocabularies
- **`_uniqueIdentifier`** - The unique identifier of the current record
- **`_absent_`** - Boolean flag for conditional mapping
- **`_input`** - The source record being mapped

## Common Mapping Patterns

### Basic Mapping Structure

```groovy
use (MappingCategory) {
    WORLD.input * { _input ->
        // Set unique identifier
        _uniqueIdentifier = _input['@id'][0].toString()
        
        // Map to output structure
        RDF.'rdf:RDF' {
            // Your mapping code here
        }
    }
}
```

### Handling Multiple Values

```groovy
// Process all creator and contributor nodes
def allCreators = (_input.creator + _input.contributor) * { person ->
    person.name.sanitize()
}

// Get first non-empty value
def title = _input['title_'].toString() ?: _input['alternativeTitle_'].toString()
```

### Conditional Mapping

```groovy
// Only map if field exists
if (_input.description) {
    'dc:description' {
        yield _input.description.sanitize()
    }
}

// Discard records based on status
discardIf(_input.status == "deleted", "Record has been deleted")
```

### URI Construction

```groovy
// Build clean URIs
def resourceURI = "http://example.com/resource/" + _input.identifier.sanitizeURI()

// Handle special characters in URIs
def subject = "http://vocab.example.com/subject/" + _input.subject.sanitizeURI()
```

### Working with Attributes

```groovy
// Access attributes
def lang = _input.title['@xml:lang'][0]
def id = _input['@id'][0]

// Check attribute existence
if (_input.title['@lang']) {
    // Use language attribute
}
```

### Date Handling

```groovy
// Calculate and use age
if (_input.birthDate && _input.deathDate) {
    def age = calculateAge(_input.birthDate[0].toString(), 
                          _input.deathDate[0].toString(), 
                          true)  // automatic date reordering
    'bio:age' {
        yield age
    }
}
```

### Complex Text Processing

```groovy
// Clean and format text
def description = _input.description
    .sanitize()                    // Remove extra whitespace
    .replaceAll('&', '&amp;')      // Escape ampersands
    .replaceAll('<br>', '\n')      // Convert breaks to newlines
```

### Vocabulary Lookups

```groovy
// Use option lookups for controlled vocabularies
def mappedType = _optLookup.materialType[_input.type[0].toString()]
if (mappedType) {
    'dc:type' {
        yield mappedType
    }
}
```

## Tips and Best Practices

1. **Always check for node existence** before accessing to avoid null pointer exceptions
2. **Use `sanitize()` on text fields** to clean up whitespace and formatting
3. **Use `sanitizeURI()` when building URIs** to properly encode special characters
4. **Remember that node access returns lists** - use `[0]` or `_` suffix for single values
5. **Test with edge cases** - empty fields, special characters, multiple values
6. **Use meaningful discard reasons** to help diagnose mapping issues
7. **Leverage list operators** for efficient batch processing of multiple values

## Custom Functions

You can define your own functions in:
- The mapping file itself
- The record definition
- These are loaded after standard functions and can override them

Example:
```groovy
def formatName(node) {
    if (!node) return ""
    def parts = node.toString().split(',')
    if (parts.length == 2) {
        return parts[1].trim() + " " + parts[0].trim()
    }
    return node.toString()
}
```