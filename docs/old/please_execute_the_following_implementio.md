# Implementation Plan: Splitting p2p-common Module

Based on the analysis of the project structure and dependencies, I'll provide a detailed implementation plan for splitting the `p2p-common` module into separate modules as recommended.

## Current Structure

The current project has the following modules:
- `p2p-common`: Contains shared code used by all other modules
- `p2p-tracker`: Depends on p2p-common
- `p2p-indexserver`: Depends on p2p-common and p2p-tracker
- `p2p-peer`: Depends on p2p-common and p2p-tracker
- `p2p-client`: Depends on p2p-common, p2p-tracker, p2p-indexserver, and p2p-peer

The `p2p-common` module contains these packages:
- `bootstrap`: Bootstrap and lifecycle management
- `common`: Core interfaces and classes
- `config`: Configuration management
- `discovery`: Service discovery
- `health`: Health check and monitoring
- `storage`: Storage-related functionality
- `util`: Common utilities

## Implementation Steps

### 1. Create New Module Structure

First, create the new modules in the project:

```bash
# Create directories for new modules
mkdir p2p-common-api
mkdir p2p-config
mkdir p2p-bootstrap
mkdir p2p-health
mkdir p2p-discovery
mkdir p2p-storage
mkdir p2p-util
```

### 2. Create POM Files for New Modules

Create a POM file for each new module. Here's a template for each:

#### p2p-common-api/pom.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>dev.mars</groupId>
        <artifactId>p2p-java</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>
    <artifactId>p2p-common-api</artifactId>
    <version>1.0-SNAPSHOT</version>
    <properties>
        <maven.compiler.source>24</maven.compiler.source>
        <maven.compiler.target>24</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <version>5.11.4</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

Similar POM files should be created for the other modules (`p2p-config`, `p2p-bootstrap`, `p2p-health`, `p2p-discovery`, `p2p-storage`, `p2p-util`), adjusting the `artifactId` accordingly.

### 3. Update Root POM

Update the root `pom.xml` to include the new modules:

```xml
<modules>
    <module>p2p-common-api</module>
    <module>p2p-config</module>
    <module>p2p-bootstrap</module>
    <module>p2p-health</module>
    <module>p2p-discovery</module>
    <module>p2p-storage</module>
    <module>p2p-util</module>
    <module>p2p-common</module>
    <module>p2p-peer</module>
    <module>p2p-tracker</module>
    <module>p2p-client</module>
    <module>p2p-indexserver</module>
</modules>
```

### 4. Analyze Dependencies Between Packages

Before moving code, analyze the dependencies between packages to understand what dependencies each new module will need:

#### Dependency Analysis
- `p2p-common-api`: No dependencies on other packages
- `p2p-config`: May depend on p2p-common-api
- `p2p-util`: May depend on p2p-common-api
- `p2p-health`: Depends on p2p-config, p2p-util, p2p-common-api
- `p2p-bootstrap`: Depends on p2p-config, p2p-health, p2p-util, p2p-common-api
- `p2p-discovery`: Depends on p2p-config, p2p-util, p2p-common-api
- `p2p-storage`: Depends on p2p-config, p2p-util, p2p-common-api

### 5. Update Module Dependencies

Update each module's POM file to include the necessary dependencies. For example:

#### p2p-bootstrap/pom.xml
```xml
<dependencies>
    <dependency>
        <groupId>dev.mars</groupId>
        <artifactId>p2p-common-api</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>dev.mars</groupId>
        <artifactId>p2p-config</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>dev.mars</groupId>
        <artifactId>p2p-health</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>dev.mars</groupId>
        <artifactId>p2p-util</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter-api</artifactId>
        <version>5.11.4</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### 6. Move Code to New Modules

Move the code from `p2p-common` to the new modules:

```bash
# Create directory structure for each module
mkdir -p p2p-common-api/src/main/java/dev/mars/p2pjava/common
mkdir -p p2p-common-api/src/test/java/dev/mars/p2pjava/common

mkdir -p p2p-config/src/main/java/dev/mars/p2pjava/config
mkdir -p p2p-config/src/test/java/dev/mars/p2pjava/config
mkdir -p p2p-config/src/main/resources/config

mkdir -p p2p-bootstrap/src/main/java/dev/mars/p2pjava/bootstrap
mkdir -p p2p-bootstrap/src/test/java/dev/mars/p2pjava/bootstrap

mkdir -p p2p-health/src/main/java/dev/mars/p2pjava/health
mkdir -p p2p-health/src/test/java/dev/mars/p2pjava/health

mkdir -p p2p-discovery/src/main/java/dev/mars/p2pjava/discovery
mkdir -p p2p-discovery/src/test/java/dev/mars/p2pjava/discovery

mkdir -p p2p-storage/src/main/java/dev/mars/p2pjava/storage
mkdir -p p2p-storage/src/test/java/dev/mars/p2pjava/storage

mkdir -p p2p-util/src/main/java/dev/mars/p2pjava/util
mkdir -p p2p-util/src/test/java/dev/mars/p2pjava/util

# Move code from p2p-common to new modules
cp -r p2p-common/src/main/java/dev/mars/p2pjava/common/* p2p-common-api/src/main/java/dev/mars/p2pjava/common/
cp -r p2p-common/src/test/java/dev/mars/p2pjava/common/* p2p-common-api/src/test/java/dev/mars/p2pjava/common/

cp -r p2p-common/src/main/java/dev/mars/p2pjava/config/* p2p-config/src/main/java/dev/mars/p2pjava/config/
cp -r p2p-common/src/test/java/dev/mars/p2pjava/config/* p2p-config/src/test/java/dev/mars/p2pjava/config/
cp -r p2p-common/src/main/resources/config/* p2p-config/src/main/resources/config/

# Continue for other modules...
```

### 7. Update Existing Modules' Dependencies

Update the dependencies in `p2p-tracker`, `p2p-indexserver`, `p2p-peer`, and `p2p-client` to use the new modules instead of `p2p-common`.

#### p2p-tracker/pom.xml
```xml
<dependencies>
    <dependency>
        <groupId>dev.mars</groupId>
        <artifactId>p2p-common-api</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>dev.mars</groupId>
        <artifactId>p2p-config</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>dev.mars</groupId>
        <artifactId>p2p-bootstrap</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>dev.mars</groupId>
        <artifactId>p2p-health</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>dev.mars</groupId>
        <artifactId>p2p-util</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    <!-- Other dependencies -->
</dependencies>
```

### 8. Incremental Testing

After moving each package, run tests to ensure everything still works:

```bash
# Run tests for the new module
cd p2p-config
mvn test

# Run tests for modules that depend on the new module
cd ../p2p-tracker
mvn test
```

### 9. Deprecate p2p-common

Once all code has been moved and all tests pass, update `p2p-common` to be a transitional module that depends on all the new modules:

#### p2p-common/pom.xml
```xml
<dependencies>
    <dependency>
        <groupId>dev.mars</groupId>
        <artifactId>p2p-common-api</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>dev.mars</groupId>
        <artifactId>p2p-config</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>dev.mars</groupId>
        <artifactId>p2p-bootstrap</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>dev.mars</groupId>
        <artifactId>p2p-health</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>dev.mars</groupId>
        <artifactId>p2p-discovery</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>dev.mars</groupId>
        <artifactId>p2p-storage</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>dev.mars</groupId>
        <artifactId>p2p-util</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

Add a deprecation notice to the README or JavaDoc of `p2p-common`.

### 10. Final Testing

Run a full build and test of the entire project:

```bash
mvn clean install
```

## Phased Implementation

To minimize risk, implement this plan in phases:

### Phase 1: Create Core Modules
1. Create `p2p-common-api` and move core interfaces
2. Create `p2p-util` and move utility classes
3. Update dependencies and test

### Phase 2: Create Configuration Module
1. Create `p2p-config` and move configuration classes
2. Update dependencies and test

### Phase 3: Create Service Modules
1. Create `p2p-health`, `p2p-bootstrap`, `p2p-discovery`, and `p2p-storage`
2. Move corresponding code
3. Update dependencies and test

### Phase 4: Deprecate p2p-common
1. Update `p2p-common` to be a transitional module
2. Add deprecation notices
3. Final testing

## Conclusion

This implementation plan provides a structured approach to splitting the `p2p-common` module into more focused modules. By following this plan, the project will benefit from:

1. Clearer separation of concerns
2. More flexible dependency management
3. Improved maintainability
4. Better reusability of components

The phased approach minimizes risk by allowing for incremental testing and validation at each step.