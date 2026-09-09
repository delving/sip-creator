# Repository Guidelines

## Project Overview

SIP-Creator transforms XML metadata records (LIDO, ESE, EDM, MODS, etc.) into RDF
using Groovy-based mappings. It ships a Swing GUI, a picocli CLI, and a gRPC server.
Licensed under EUPL 1.2.

## Project Structure

- `schema-repo/` — Cached schema definitions for offline XSD/SHACL validation.
- `sip-core/` — Shared transformation engine: Groovy runtime, code generation,
  record definitions, metadata model. Tests in `sip-core/src/test/java`.
- `sip-app/` — GUI (Swing + FlatLaf), CLI (picocli), gRPC server. Protobuf
  definitions in `sip-app/src/main/proto`. Tests in `sip-app/src/test/java`.
- `sip-web/` — Planned JTE-based web module (not yet in the Maven build).
- `_scripts/` — Native packaging helpers (`prepare_build.sh`, `jpackage_*`).
- `_data/` — Golden-file test fixtures (input/output XML, mapping, Groovy).

## Build, Lint, and Test Commands

### Full build
```bash
mvn clean install                # compile + test all modules
mvn clean install -DskipTests    # compile only
```

### Run tests for a single module
```bash
mvn test -pl sip-core
mvn test -pl sip-app
```

### Run a single test class
```bash
mvn test -pl sip-core -Dtest=BulkMappingRunnerTest
mvn test -pl sip-app  -Dtest=FilenameExtractorTest
```

### Run a single test method
```bash
mvn test -pl sip-core -Dtest=BulkMappingRunnerTest#testManyInvocations
```

### Coverage
```bash
mvn clean install -Djacoco.skip=false   # enable JaCoCo (off by default)
mvn jacoco:report                       # reports in target/site/jacoco/
```

### Protobuf / gRPC
```bash
# Regenerated automatically by protobuf-maven-plugin during compile.
# For manual buf usage:
buf lint    # from repo root (buf.work.yaml points to sip-app/src/main/proto)
buf generate
```

### Running the applications
```bash
# GUI
mvn exec:java -pl sip-app -Dexec.mainClass="eu.delving.sip.Application"

# CLI / gRPC (need module opens for Groovy reflection)
export MAVEN_OPTS="--add-opens=java.base/java.util=ALL-UNNAMED \
  --add-opens=java.base/java.lang=ALL-UNNAMED \
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED"
mvn exec:java -pl sip-app -Dexec.mainClass="eu.delving.sip.cli.SIPCLI" -Dexec.args="[args]"
mvn exec:java -pl sip-app -Dexec.mainClass="eu.delving.sip.grpc.SIPGRPC"
```

### Native packaging
```bash
bash _scripts/prepare_build.sh
bash _scripts/jpackage_linux.sh      # .tar.xz
bash _scripts/jpackage_macos.sh      # .dmg
bash _scripts/jpackage_windows.sh    # .msi / .zip
```

## Environment Requirements

- **Java 21** (enforced by maven-enforcer-plugin)
- **Maven >= 3.9.0** (enforced by maven-enforcer-plugin)
- No code-style enforcement plugins (no Checkstyle, Spotless, PMD, or Error Prone).

## Code Style

### Formatting (`.editorconfig` is the source of truth)
- **Java**: 4-space indentation, max 120 characters per line, LF line endings.
- **XML**: 2-space indentation.
- **YAML**: 2-space indentation.
- UTF-8 everywhere; trim trailing whitespace (except Markdown).
- K&R brace style (opening brace on the same line).

### Naming
- Packages under `eu.delving.*` mirroring the directory tree.
- Classes: `PascalCase` — descriptive nouns (`MappingRunner`, `DataSetModel`).
- Methods: `camelCase` — prefer verbs for actions (`runMapping`, `compileScript`).
- Constants: `UPPER_SNAKE_CASE`.
- Logger field: `LOG` (sip-core convention) or `logger` (sip-app convention).

### Imports
- Prefer explicit single-class imports in new code; wildcard imports (`java.io.*`)
  exist in legacy files but should not be introduced.
- No enforced ordering; follow the prevailing order in the file you are editing.
- Static imports are common for assertion methods and utility helpers
  (`import static eu.delving.metadata.StringUtil.*`).

### Types and language features
- Target **Java 21**. No Java records or `var` usage in the codebase — keep
  consistency by using traditional classes and explicit types.
- `Optional` is rarely used (one occurrence); prefer direct null checks.
- Use `private final` for injected dependencies and immutable state.
- Do not add `final` to method parameters or local variables.

### Null handling
- Direct null checks (`if (x == null)`); no `@Nullable`/`@NonNull` annotations,
  no `Objects.requireNonNull()`.
- Return empty collections rather than null where possible.

### Error handling
- Domain-specific checked exceptions: `MappingException`, `StorageException`,
  `MetadataException`.
- `MappingException` uses a typed `ErrorType` enum (COMPILATION, EXECUTION,
  VALIDATION, STRUCTURE, CONTENT, RDF) — follow this pattern for new error types.
- Wrap lower-level exceptions in domain exceptions with cause chaining.
- `DiscardRecordException` (unchecked) is used as control flow to skip records.
- Prefer try-with-resources in new code; legacy code uses `IOUtils.closeQuietly()`.

### Logging
- SLF4J API (`org.slf4j.Logger` / `LoggerFactory`) with Logback runtime.
- Declare as: `private static final Logger LOG = LoggerFactory.getLogger(MyClass.class);`

### License header
Every Java file must carry the EUPL 1.2 header:
```java
/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 */
```

## Testing Guidelines

- **JUnit 5** for all new tests; legacy JUnit 4 tests still exist in sip-core
  and sip-app (do not convert without reason).
- Test naming: `<ClassName>Test` (new convention). Legacy prefix `Test<ClassName>`
  exists but should not be used for new tests.
- Method naming: descriptive camelCase, optionally prefixed with `should`
  (e.g., `shouldExtractBaseNameFromVariousPaths`).
- JUnit 5 tests use package-private visibility (no `public` on class or methods).
- Use `@ParameterizedTest` with `@MethodSource` or `@NullAndEmptySource` for
  edge-case coverage.
- Assertions: `org.junit.jupiter.api.Assertions.*` (static import); XMLUnit
  (`xmlunit-core`) is available for XML comparison.
- Mocking: **Mockito** (`mockito-core`). For JUnit 5, use `@ExtendWith(MockitoExtension.class)`.
- Store test data in `src/test/resources/`.
- For regression tests, follow `GOLDEN_FILES_TESTING.md` to manage golden files
  stored in `_data/`.
- Run `mvn clean install` before pushing to ensure all modules build and pass.

## Protobuf / gRPC Conventions

- Definitions live in `sip-app/src/main/proto/delving/mapping/v1/mapping.proto`.
- Use proto3 syntax with versioned packages (`mapping.v1`).
- Java options: `java_multiple_files = true`, package `eu.delving.sip.grpc`.
- Generated code is built by `protobuf-maven-plugin` during `mvn compile` —
  do not commit generated sources.
- Run `buf lint` when editing `.proto` files.

## Commit and PR Guidelines

- **Conventional Commits**: `feat(sip-web): …`, `fix(sip-core): …`, `refactor: …`.
- Each commit must build independently.
- PR body: summarize problem, solution, and test evidence; link issues.
- Flag schema, protobuf, or API changes for downstream consumers.

## Important Paths

- Runtime workspace: `~/DelvingSIPCreator` — never check in generated datasets.
- GUI frames: `sip-app/src/main/java/eu/delving/sip/frames/`
- Core engine: `sip-core/src/main/java/eu/delving/groovy/`
- Mapping model: `sip-core/src/main/java/eu/delving/metadata/`
- CLI entry: `sip-app/src/main/java/eu/delving/sip/cli/SIPCLI.java`
- gRPC entry: `sip-app/src/main/java/eu/delving/sip/grpc/SIPGRPC.java`
- Mapping guides: `MAPPING_AND_RECDEF_GUIDE.md`, `MAPPING_HINTS.md`

<!-- openwolf:begin -->
# OpenWolf

This project uses OpenWolf for context management. Read and follow .wolf/OPENWOLF.md at session start. Check .wolf/cerebrum.md before generating code. Grep .wolf/anatomy.md for a file's path before reading it (never read the whole index).
<!-- openwolf:end -->
