# SIP-Creator Release Process

This document describes the process for creating releases of the SIP-Creator project, including Release Candidates (RC) and final releases.

## Prerequisites

Before starting the release process, ensure you have:

1. Maven 3.2.x or higher installed
2. Git configured with your credentials:
   ```bash
   git config --global user.name "Your Name"
   git config --global user.email "your.email@example.com"
   ```
3. Access to the GitHub repository with write permissions
4. GitHub Packages access token configured in your `~/.m2/settings.xml`:
   ```xml
   <settings>
     <servers>
       <server>
         <id>github</id>
         <username>YOUR_GITHUB_USERNAME</username>
         <password>YOUR_GITHUB_TOKEN</password>
       </server>
     </servers>
   </settings>
   ```

## Version Numbering Convention

- Development versions: `X.Y.Z-SNAPSHOT` (e.g., `1.3.1-SNAPSHOT`)
- Release Candidates: `X.Y.Z-RCn` (e.g., `1.3.1-RC1`, `1.3.1-RC2`)
- Final releases: `X.Y.Z` (e.g., `1.3.1`)

## Creating a Release Candidate

1. **Ensure your local repository is up to date**:
   ```bash
   git checkout main
   git pull origin main
   ```

2. **Generate Protobuf Sources and Verify the Build**:
   ```bash
   # The protobuf sources are generated automatically during the generate-sources phase
   # You can explicitly generate them with:
   mvn generate-sources

   # Then verify the full build:
   mvn clean verify
   ```

   Note: The protobuf generation includes:
   - Buf tool generating protobuf files (configured in maven-antrun-plugin)
   - Protobuf/gRPC Java code generation (via protobuf-maven-plugin)
   - Sources will be generated in `target/generated-sources/protobuf/`

3. **Dry run the release preparation**:
   ```bash
   mvn release:prepare -DdryRun=true \
     -DreleaseVersion=1.3.1-RC1 \
     -DdevelopmentVersion=1.3.1-SNAPSHOT
   ```

4. **If the dry run is successful, prepare the actual release**:
   ```bash
   mvn release:prepare \
     -DreleaseVersion=1.3.1-RC1 \
     -DdevelopmentVersion=1.3.1-SNAPSHOT
   ```

5. **Perform the release**:
   ```bash
   mvn release:perform
   ```

This process will:
- Update version numbers in all POMs
- Create a Git tag for the release
- Build and test the project
- Deploy artifacts to GitHub Packages
- Update POMs to the next development version

## Creating the Final Release

After the Release Candidate has been tested and approved:

1. **Prepare the final release**:
   ```bash
   mvn release:prepare \
     -DreleaseVersion=1.3.1 \
     -DdevelopmentVersion=1.3.2-SNAPSHOT
   ```

2. **Perform the release**:
   ```bash
   mvn release:perform
   ```

## Post-Release Tasks

After a successful release:

1. **Verify the artifacts in GitHub Packages**:
   - Navigate to the GitHub repository
   - Go to the Packages section
   - Verify all artifacts are present with correct versions

2. **Create a GitHub Release**:
   - Go to the repository's Releases page
   - Create a new release using the tag created by Maven
   - Include release notes detailing changes, improvements, and bug fixes

3. **Update documentation**:
   - Update changelog
   - Update version numbers in documentation if necessary
   - Update installation instructions if necessary

## Troubleshooting

### Protobuf/gRPC Generation

The project uses both Buf and the standard protobuf-maven-plugin for generating protobuf/gRPC code:

1. **Buf Generation**:
   - Configured in `sip-app/pom.xml` using maven-antrun-plugin
   - Downloads and executes the buf tool
   - Runs during `generate-sources` phase
   - Configuration in `buf.yaml` and `buf.gen.yaml`

2. **Protobuf/gRPC Plugin**:
   - Uses `protobuf-maven-plugin`
   - Generates Java classes from .proto files
   - Also runs during `generate-sources` phase
   - Configured for both standard protobuf and gRPC

### Common Issues

1. **Release preparation fails**:
   ```bash
   mvn release:rollback
   git reset --hard HEAD^
   ```

2. **Release tag already exists**:
   ```bash
   git tag -d v1.3.1-RC1
   git push origin :refs/tags/v1.3.1-RC1
   ```

3. **Protobuf Generation Issues**:
   - Check that buf is properly downloaded and executable
   - Verify proto files are in the correct location (`src/main/proto`)
   - Ensure buf.yaml and buf.gen.yaml are properly configured
   - Check protoc version compatibility

4. **GitHub Packages authentication failure**:
   - Verify your GitHub token has the required permissions
   - Check your `settings.xml` configuration
   - Ensure the token hasn't expired

### Release Plugin Goals

- `release:prepare`: Updates versions, creates commits and tags
- `release:perform`: Checks out the tag and deploys the released version
- `release:rollback`: Reverts changes made by prepare
- `release:clean`: Removes backup files created by the release plugin

## Branch Strategy

The project follows these branching conventions:

- `main`: Primary development branch
- `release/X.Y.Z`: Release branches (created for each release)
- Tags: `vX.Y.Z-RCn` for Release Candidates, `vX.Y.Z` for final releases

## Notes

- Always perform a dry run before actual release
- Ensure all tests pass before releasing
- Keep release notes up to date
- Follow semantic versioning principles
- Consider creating release branches for major versions 
