# Groovy Features Analysis Report

This report analyzes all Groovy code found in mapping files to determine implementation requirements.

## Phase 1: Core features needed for basic functionality

### Property Access
Examples found:
- `www.mauritshuis.nl`
- `_id.sanitizeURI`
- `_rdfDescription.edmhasView`

Sample code:
```groovy
5.2.6
```

### Operators
Examples found:
- `arithmetic/comparison`

### String Literals
Examples found:
- `basic strings`

Sample code:
```groovy
"https://sws.geonames.org/${it}/"
```

### String Interpolation
Examples found:
- `createEDMAgentUri(_enbdcorganisator.enbdcpersonuuid_)`
- `_externaldocument.replaceAll(".*\\\\", "")`
- `_Reproduction.reproductionoriginalfilename_`

Sample code:
```groovy
"https://sws.geonames.org/${it}/"
```

### Method Calls
Examples found:
- `getBytes`
- `split`
- `capitalize`

Sample code:
```groovy
itt = it.toString()
```

## Phase 2: Commonly used patterns in mappings

### Array Access
Examples found:
- `"http://vocab.getty.edu/aat/300033973", "tekeningen"`
- `|`
- `2`

Sample code:
```groovy
year = ~/([0-9]{4})/
```

### It Variable
Examples found:
- `it usage`

Sample code:
```groovy
itt = it.toString()
```

### Common Methods
Examples found:
- `split`
- `size`
- `endsWith`

### Loop Operators
Examples found:
- `* (map)`
- `>> (flatten)`

Sample code:
```groovy
_image.thumbnaillarge * { _thumbnaillarge ->
```

### Closures
Examples found:
- `closure usage`

Sample code:
```groovy
"${_input.record[0].about[0].mmmmemorix[0].image.collect{thumb -> return thumb.thumbnailsmall[0]}.in
```

### Elvis Operator
Examples found:
- `?:`

Sample code:
```groovy
"https://dams.antwerpen.be/asset/${_id.replaceAll(".*?:", "").replaceAll("oai-dams-", "")}"
```

## Phase 3: Advanced features for full compatibility

### Regex
Examples found:
- `.*2013`
- `([0-9]{4})-[0-9]{2}-[0-9]{2}`
- `.*2011`

Sample code:
```groovy
year = ~/([0-9]{4})/
```

### Conditionals
Examples found:
- `if/ternary`

Sample code:
```groovy
if (year.matcher(itt).matches()) {
```

### Collection Methods
Examples found:
- `find`
- `collect`
- `findAll`

### Loop Operators
Examples found:
- `| (zip/tuple)`

Sample code:
```groovy
_image.thumbnaillarge * { _thumbnaillarge ->
```

### Type Casting
Examples found:
- `as operator`

Sample code:
```groovy
// The city "Amsterfoort" is used as reference "WGS84" coordinate.
```

## Phase 4: Rarely used features - implement as needed

### Range Operator
Examples found:
- `..`

