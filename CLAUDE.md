# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SIP-Creator is a Java application for transforming XML metadata records into various output formats, particularly RDF. It provides three interfaces:
- GUI application (Swing-based) for interactive mapping
- CLI for batch processing and automation
- gRPC server for distributed processing

The core technology uses Groovy for dynamic mapping code that transforms source XML into target formats through visual mapping and code refinement.

## Common Development Commands

### Build and Test
```bash
# Standard build
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Run specific module tests
mvn test -pl sip-core
mvn test -pl sip-app

# Generate coverage report
mvn jacoco:report
```

### Running Applications
```bash
# GUI Application
mvn exec:java -pl sip-app -Dexec.mainClass="eu.delving.sip.Application"

# CLI (requires MAVEN_OPTS for module access)
export MAVEN_OPTS="--add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED"
mvn exec:java -pl sip-app -Dexec.mainClass="eu.delving.sip.cli.SIPCLI" -Dexec.args="[arguments]"

# gRPC Server
mvn exec:java -pl sip-app -Dexec.mainClass="eu.delving.sip.grpc.SIPGRPC"
```

### Release Process
```bash
# Prepare release (updates versions, creates tag)
mvn release:prepare

# Perform release (builds and deploys)
mvn release:perform

# Clean failed release
mvn release:clean
```

### Native Packaging
```bash
# Prepare build first
bash _scripts/prepare_build.sh

# Then create platform packages
bash _scripts/jpackage_linux.sh    # .tar.xz
bash _scripts/jpackage_macos.sh    # .dmg
bash _scripts/jpackage_windows.sh  # .msi, .zip
```

## Architecture and Code Structure

### Module Organization
- **schema-repo**: Schema management, interfaces with http://schemas.delving.eu
- **sip-core**: Core transformation logic shared between client and server
- **sip-app**: GUI, CLI, and gRPC implementations

### Key Architectural Components

#### Processing Pipeline
1. **Storage Layer**: `Storage.java`, `DataSet.java` - Handle file I/O, compression (gzip/zstd)
2. **Parsing**: `MetadataParser.java` - Streaming XML parsing for large datasets
3. **Mapping Engine**: `MappingRunner.java`, `MappingExecutor.java` - Execute Groovy transformations
4. **Validation**: XSD, SHACL, and custom assertions
5. **Output**: RDF serialization in multiple formats

#### Model Architecture (MVC)
- `SipModel.java`: Central model aggregating all sub-models
- Specialized models: `MappingModel`, `DataSetModel`, `StatsModel`
- Observer pattern throughout for reactive updates
- `WorkModel` for async operations with progress tracking

#### Multi-threading Strategy
- `FileProcessor.java` manages thread pool (1.1x CPU cores)
- Producer-consumer pattern with `BlockingQueue`
- Thread-safe output writing and reporting

#### Groovy Integration
- Dynamic compilation via `GroovyCodeResource`
- Live coding in GUI with immediate feedback
- Code generation from visual mappings via `CodeGenerator`

### Data Flow
1. Compressed XML input (`.xml.gz` or `.xml.zst`)
2. Stream parsing into `MetadataRecord` objects
3. Groovy mapping execution per record
4. DOM construction and validation
5. RDF serialization to output format

### State Management
DataSetState progression:
```
ABSENT → SOURCED → ANALYZED_SOURCE → MAPPING → PROCESSED
```

## Development Guidelines

### When Working with Mappings
- Mappings are stored in `DataSet` directories under `~/DelvingSIPCreator`
- Always preserve user's Groovy code refinements when regenerating
- Test with both small and large datasets for performance

### Adding New Features
- GUI features go in `sip-app/src/main/java/eu/delving/sip/frames/`
- New validators implement appropriate interfaces in `sip-core`
- RDF output formats extend serialization classes

### Testing Considerations
- Unit tests for transformation logic in `sip-core`
- Integration tests use test data in `src/test/resources/`
- Test various metadata formats: ESE, EDM, LIDO, MODS, etc.

### Performance Tips
- Use streaming parsers for XML processing
- Leverage multi-threading via `FileProcessor`
- Monitor memory usage with large datasets
- Profile with production-sized datasets

## Key Entry Points for Understanding

1. **Main Applications**: 
   - `sip-app/.../Application.java` - GUI entry
   - `sip-app/.../cli/SIPCLI.java` - CLI commands
   - `sip-app/.../grpc/SIPGRPC.java` - gRPC service

2. **Core Processing**:
   - `sip-core/.../MappingRunner.java` - Transformation interface
   - `sip-core/.../GroovyCodeResource.java` - Dynamic code execution
   - `sip-app/.../FileProcessor.java` - Multi-threaded processing

3. **Data Management**:
   - `sip-app/.../Storage.java` - Dataset persistence
   - `sip-app/.../model/SipModel.java` - Central model

## Environment Requirements
- Java 21+
- Maven 3.9.0+
- For CLI: Set MAVEN_OPTS for module access
- For releases: Proper Maven settings for deployment