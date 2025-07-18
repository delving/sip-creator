# SIP-Creator Mapping and Record Definition Guide

This document explains how mapping and record definition (rec-def) files work together in SIP-Creator to transform XML metadata records.

## Table of Contents
- [Overview](#overview)
- [Record Definition Files](#record-definition-files)
- [Mapping Files](#mapping-files)
- [How They Work Together](#how-they-work-together)
- [Validation](#validation)
- [Advanced Features](#advanced-features)

## Overview

SIP-Creator uses two main file types to define metadata transformations:

1. **Record Definition Files** (`.rec-def` or `-record-definition.xml`) - Define the target output structure
2. **Mapping Files** (`.mapping` or `mapping_*.xml`) - Define how to transform source data to match the target

Together, these files enable flexible, schema-driven transformation of XML metadata with built-in validation.

## Record Definition Files

Record definition files describe the target metadata schema structure and include validation rules, documentation, and reusable functions.

### Basic Structure

```xml
<record-definition prefix="edm" version="5.2.6" flat="false">
    <!-- Namespace declarations -->
    <namespaces>
        <namespace prefix="dc" uri="http://purl.org/dc/elements/1.1/"/>
        <namespace prefix="edm" uri="http://www.europeana.eu/schemas/edm/"/>
        <namespace prefix="ore" uri="http://www.openarchives.org/ore/terms/"/>
    </namespaces>
    
    <!-- Reusable transformation functions -->
    <functions>
        <mapping-function name="convertToUTM">
            <groovy-code>
                // Groovy transformation code
            </groovy-code>
        </mapping-function>
    </functions>
    
    <!-- Target structure definition -->
    <root>
        <elem tag="edm:RDF">
            <elem tag="ore:Aggregation" attrs="rdf:about">
                <elem tag="edm:dataProvider"/>
                <elem tag="edm:object" fieldType="link"/>
                <!-- More elements... -->
            </elem>
        </elem>
    </root>
</record-definition>
```

### Key Components

#### 1. Namespaces
Define XML namespaces used in the output:
```xml
<namespace prefix="dc" uri="http://purl.org/dc/elements/1.1/" schema="dc.xsd"/>
```

#### 2. Elements and Attributes
Define the hierarchical structure:
```xml
<elem tag="dc:title" required="true">
    <doc>
        <string lang="en">Title of the resource</string>
    </doc>
</elem>

<!-- Element with attributes -->
<elem tag="edm:WebResource" attrs="rdf:about">
    <attr tag="rdf:about" required="true"/>
</elem>
```

#### 3. Field Types and Markers
Special field behaviors:
```xml
<!-- Link field - will be validated as URI -->
<elem tag="edm:object" fieldType="link"/>

<!-- Unique field marker -->
<elem tag="dc:identifier" fieldType="unique"/>

<!-- Indexed field for searching -->
<elem tag="dc:subject" fieldType="indexed"/>
```

#### 4. Validation Rules
Built-in validation:
```xml
<elem tag="dc:date">
    <check>
        <groovy-code>
            value ==~ /\d{4}(-\d{2}(-\d{2})?)?/
        </groovy-code>
    </check>
</elem>
```

#### 5. Documentation
Human-readable descriptions:
```xml
<elem tag="dc:creator">
    <doc>
        <string lang="en">Entity primarily responsible for making the resource</string>
        <string lang="nl">Entiteit primair verantwoordelijk voor het maken van de bron</string>
    </doc>
</elem>
```

#### 6. Option Lists
Controlled vocabularies:
```xml
<elem tag="dc:type">
    <opts>
        <opt>
            <string>IMAGE</string>
        </opt>
        <opt>
            <string>TEXT</string>
        </opt>
        <opt>
            <string>VIDEO</string>
        </opt>
    </opts>
</elem>
```

## Mapping Files

Mapping files define how source XML is transformed to match the record definition structure.

### Basic Structure

```xml
<rec-mapping prefix="edm" schemaVersion="5.2.6" locked="true">
    <!-- Mapping metadata -->
    <facts>
        <entry>
            <string>provider</string>
            <string>My Institution</string>
        </entry>
        <entry>
            <string>rights</string>
            <string>http://creativecommons.org/publicdomain/zero/1.0/</string>
        </entry>
    </facts>
    
    <!-- Additional functions -->
    <functions>
        <mapping-function name="formatDate">
            <groovy-code>
                // Custom date formatting logic
            </groovy-code>
        </mapping-function>
    </functions>
    
    <!-- Path-based transformations -->
    <node-mappings>
        <!-- Simple mapping -->
        <node-mapping inputPath="/input/title" 
                      outputPath="/edm:RDF/ore:Aggregation/dc:title"/>
        
        <!-- Mapping with transformation -->
        <node-mapping inputPath="/input/creator" 
                      outputPath="/edm:RDF/ore:Aggregation/dc:creator">
            <groovy-code>
                <string>it.toString().sanitize()</string>
            </groovy-code>
        </node-mapping>
    </node-mappings>
</rec-mapping>
```

### Key Components

#### 1. Facts
Mapping metadata used in transformations:
```xml
<facts>
    <entry>
        <string>provider</string>
        <string>National Museum</string>
    </entry>
    <entry>
        <string>baseUrl</string>
        <string>http://data.museum.org</string>
    </entry>
</facts>
```

#### 2. Node Mappings
Define transformations from input to output paths:

**Simple copy:**
```xml
<node-mapping inputPath="/input/description" 
              outputPath="/edm:RDF/ore:Aggregation/dc:description"/>
```

**With transformation:**
```xml
<node-mapping inputPath="/input/date" 
              outputPath="/edm:RDF/ore:Aggregation/dc:date">
    <groovy-code>
        <string>formatDate(it.toString())</string>
    </groovy-code>
</node-mapping>
```

**With operator (for multiple values):**
```xml
<node-mapping inputPath="/input/subject" 
              outputPath="/edm:RDF/ore:Aggregation/dc:subject"
              operator="ALL">
    <groovy-code>
        <string>it.toString().toUpperCase()</string>
    </groovy-code>
</node-mapping>
```

#### 3. Operators
Control how multiple input values are handled:
- **FIRST** - Use only the first value
- **ALL** - Process all values
- **JOIN** - Join values with separator
- **CUSTOM** - Custom handling in Groovy code

#### 4. Dictionary Mappings
For value lookups:
```xml
<node-mapping inputPath="/input/type" 
              outputPath="/edm:RDF/ore:Aggregation/dc:type">
    <dictionary>
        <entry>
            <string>foto</string>
            <string>IMAGE</string>
        </entry>
        <entry>
            <string>document</string>
            <string>TEXT</string>
        </entry>
    </dictionary>
</node-mapping>
```

## How They Work Together

### 1. Processing Flow

```
Source XML → Mapping File → Generated Groovy Code → Record Definition → Output XML/RDF
                ↓                                           ↓
            Validation                                  Validation
```

### 2. Path Resolution

- **Input paths** reference the source XML structure
- **Output paths** reference the record definition structure
- Paths use `/` separator and can include predicates

### 3. Generated Code

SIP-Creator generates executable Groovy code from mappings:

```groovy
use (MappingCategory) {
    WORLD.input * { _input ->
        _uniqueIdentifier = _input['identifier'][0].toString()
        
        RDF.'edm:RDF' {
            'ore:Aggregation'('rdf:about': "${baseUrl}/aggregation/${_uniqueIdentifier}") {
                'dc:title' {
                    yield _input['title'][0].toString()
                }
                // More mappings...
            }
        }
    }
}
```

### 4. Validation Chain

1. **Structure validation** - Output matches record definition
2. **XSD validation** - Against schema files
3. **Field validation** - Required fields, formats, unique values
4. **Custom assertions** - Groovy-based validation rules

## Validation

### XSD Validation

Separate XSD files validate output:
```xml
<!-- edm_5.2.6_validation.xsd -->
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <!-- Schema definition -->
</xs:schema>
```

### Assertion Tests

Custom validation in record definitions:
```xml
<assertion>
    <assert test="count(//dc:title) > 0">
        <string>At least one title is required</string>
    </assert>
</assertion>
```

### SHACL Validation

For RDF output, SHACL shapes can validate:
```turtle
ex:TitleShape a sh:PropertyShape ;
    sh:path dc:title ;
    sh:minCount 1 ;
    sh:datatype xsd:string .
```

## Advanced Features

### 1. Templates

Reusable element patterns:
```xml
<elem tag="edm:Agent" template="true">
    <elem tag="skos:prefLabel"/>
    <elem tag="skos:altLabel"/>
    <elem tag="rdaGr2:dateOfBirth"/>
</elem>
```

### 2. Conditional Mapping

Using Groovy conditions:
```xml
<node-mapping inputPath="/input/creator" 
              outputPath="/edm:RDF/ore:Aggregation/dc:creator">
    <groovy-code>
        <string>
            if (it.toString().contains("Anonymous")) {
                return null  // Skip anonymous creators
            }
            return it.toString().sanitize()
        </string>
    </groovy-code>
</node-mapping>
```

### 3. Multiple Output Nodes

Generate multiple output elements:
```xml
<node-mapping inputPath="/input/subject" 
              outputPath="/edm:RDF/ore:Aggregation">
    <groovy-code>
        <string>
            def subjects = it.toString().split(';')
            subjects.each { subject ->
                'dc:subject' {
                    yield subject.trim()
                }
                'edm:concept' {
                    yield "http://vocab.example.org/${subject.trim().sanitizeURI()}"
                }
            }
        </string>
    </groovy-code>
</node-mapping>
```

### 4. Global Variables

Access mapping facts and utilities:
```groovy
// In mapping code
"${provider}"  // From facts
"${baseUrl}/resource/${_uniqueIdentifier}"
_optLookup['type'][it.toString()]  // Option lookups
```

### 5. Function Libraries

Share code between mappings:
```xml
<mapping-function name="normalizeDate">
    <groovy-code>
        <string>
            // Complex date normalization logic
            // Available in all node mappings
        </string>
    </groovy-code>
</mapping-function>
```

## Best Practices

1. **Use record definitions from schema repository** when available
2. **Document your mappings** with clear descriptions
3. **Validate incrementally** during development
4. **Test with edge cases** - empty fields, special characters
5. **Reuse functions** for common transformations
6. **Use appropriate operators** for multiple values
7. **Handle missing data gracefully** with null checks
8. **Follow namespace conventions** consistently

## File Locations

In a typical SIP-Creator dataset:

```
dataset-name/
├── mapping_edm.xml           # Mapping file
├── edm-record-definition.xml # Record definition
├── edm-validation.xsd        # XSD schema
├── source/                   # Source XML files
├── mapped/                   # Mapped output
└── report/                   # Validation reports
```

## Troubleshooting

Common issues and solutions:

1. **"Path not found"** - Check input paths match source XML structure
2. **"Required field missing"** - Ensure mapping produces required elements
3. **"Invalid URI"** - Use `sanitizeURI()` for link fields
4. **"Namespace not declared"** - Add to record definition namespaces
5. **"Groovy compilation error"** - Check syntax and variable names

The combination of record definitions and mappings provides a powerful, flexible system for metadata transformation with comprehensive validation and documentation capabilities.