# EDM SHACL Shapes Summary

## 1. Root RDF Shape (edm:RDFShape)
* Enforces core EDM structure
* Requirements:
  - Exactly one ProvidedCHO
  - Exactly one Aggregation

## 2. ProvidedCHO Shape (edm:ProvidedCHOShape)
* Core descriptive metadata 
* Links to:
  - Agents (via dc:creator, dc:contributor)
  - Places (via dcterms:spatial)
  - TimeSpans (via dcterms:temporal)
  - Concepts (via dc:subject, dc:type)
* Validation rules:
  - Required either subject, type, temporal or spatial
  - Required title or description
  - Language required for TEXT type

## 3. Aggregation Shape (edm:AggregationShape)
* Core aggregation of digital representations
* Links to:
  - ProvidedCHO (via edm:aggregatedCHO)
  - WebResources (via edm:hasView, isShownBy, isShownAt)
  - Organizations (via edm:dataProvider, provider)
* Validation rules:
  - Required rights statement
  - Either isShownAt or isShownBy required
  - Valid rights URI patterns

## 4. WebResource Shape (edm:WebResourceShape)
* Technical metadata for digital objects
* Links to:
  - Other WebResources (via dcterms:hasPart)
  - Rights statements
* Validation rules:
  - Technical metadata constraints (dimensions, format)
  - Strict data typing for measurements
  - Color space enumeration

## 5. Place Shape (edm:PlaceShape)
* Geographic location information
* Links to:
  - Other Places (via dcterms:hasPart/isPartOf)
  - Alternative identifiers (via owl:sameAs)
* Validation rules:
  - Coordinate range checks
  - Required label
  - Hierarchy consistency

## 6. Agent Shape (edm:AgentShape)
* Person or organization information
* Links to:
  - Places (birthplace, deathplace)
  - TimeSpans (dates)
  - Other Agents (via edm:hasMet)
* Validation rules:
  - Date consistency
  - Required identification
  - Type-specific constraints

## 7. Concept Shape (skos:ConceptShape)
* Controlled vocabulary terms
* Links to:
  - Other Concepts (via semantic relations)
  - ConceptSchemes (via inScheme)
* Validation rules:
  - Label requirements
  - Relationship consistency
  - Hierarchy validation

## Key Cross-Resource Relationships

### One-to-One Relationships
* ProvidedCHO ↔ Aggregation

### One-to-Many Relationships
* Aggregation ↔ WebResource

### Many-to-Many Relationships
* ProvidedCHO ↔ Contextual resources (Place, Agent, Concept)
* Place ↔ Place (hierarchical)
* Concept ↔ Concept (semantic and mapping)

## Important Validation Features

### Required Properties
* ProvidedCHO: title/description, subject/type/temporal/spatial
* Aggregation: rights, provider, dataProvider
* WebResource: technical metadata when applicable
* Place: label, coordinates for non-historical places
* Agent: identifier
* Concept: preferred label

### Type Validations
* Strict data types for measurements and dates
* URI patterns for rights statements
* Geographic coordinate ranges
* Language tags where appropriate

### Relationship Validations
* Hierarchy consistency checks
* Reciprocal relationship validation
* Cross-scheme concept mappings
* Date sequence validation for Agents
