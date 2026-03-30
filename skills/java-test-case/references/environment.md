# Environment & Detection (详细)

## Step 1: Environment Variable Acquisition

### 1.1 Read Project Version Requirements from pom.xml

```bash
# Read Java version from pom.xml
grep -E "maven.compiler.source|maven.compiler.target|java.version" pom.xml

# Read Maven version if specified
grep -E "maven.version|build.plugins.plugin.version" pom.xml | grep -i maven

# Check parent pom for version inheritance
grep -E "<parent>" pom.xml
```

### 1.2 Find Available Java/Maven Installations

```bash
# Windows: List all Java versions in common locations
dir "C:\Program Files\Java" /b
dir "D:\Tools\Java" /b 2>nul
dir "D:\Java" /b 2>nul

# Windows: List all Maven versions in common locations
dir "D:\Tools" /b 2>nul | findstr "maven"
dir "C:\Program Files" /b /s 2>nul | findstr "maven"

# Find specific JDK paths (Windows)
for /d %d in ("C:\Program Files\Java\jdk-*") do @echo %d
for /d %d in ("D:\Tools\Java\jdk-*") do @echo %d
```

### 1.3 Match Version and Set Environment Variables

| Project Requires | Recommended JDK | Recommended Maven |
|------------------|-----------------|-------------------|
| Java 8 | JDK 1.8.x | Maven 3.3.x - 3.8.x |
| Java 11 | JDK 11.x | Maven 3.6.x - 3.9.x |
| Java 17 | JDK 17.x | Maven 3.8.x+ |
| Java 21 | JDK 21.x | Maven 3.9.x+ |

```bash
# Check if installed version matches requirement
java -version

# Set to match project version (Windows session-level)
set JAVA_HOME=D:\Tools\Java\jdk-17
set MAVEN_HOME=D:\Tools\apache-maven-3.9.6
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%

# Set to match project version (Linux/Mac)
export JAVA_HOME=/usr/lib/jvm/java-17
export MAVEN_HOME=/opt/maven-3.9.6
export PATH=$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH
```

### 1.4 Verify Matched Environment (必须执行)

```bash
# Confirm versions match project requirements
java -version
mvn -version

# Double-check against pom.xml
grep -E "maven.compiler.source|maven.compiler.target" pom.xml
```

---

## Step 2: Detect Test Framework and Coverage Framework via Maven

```bash
# Check pom.xml for test framework dependencies
grep -E "<groupId>org.junit.jupiter|<groupId>org.junit.vintage" -A 3 pom.xml
grep -A 5 "testng" pom.xml       # TestNG
grep -A 5 "spock" pom.xml        # Spock

# Check for coverage framework in build plugins
grep -E "<artifactId>.*-plugin</artifactId>" pom.xml | grep -iE "jacoco|cobertura|clover|codecover|pmd|coverage"
```

### Test Frameworks

| Framework | Dependency | Version |
|-----------|------------|---------|
| JUnit 5 | junit-jupiter | 5.x |
| JUnit 4 | junit-vintage | 4.x |
| TestNG | testng | 7.x |
| Spock | spock-core | 2.x |

### Coverage Frameworks

| Framework | Plugin | Version |
|-----------|--------|---------|
| JaCoCo | jacoco-maven-plugin | 0.8.x |
| Cobertura | cobertura-maven-plugin | 2.x |

```bash
# List all test dependencies
mvn dependency:list -DincludeScope=test 2>/dev/null | grep -iE "junit|testng|spock"

# Extract coverage plugin version and configuration
grep -A 10 "jacoco-maven-plugin" pom.xml
grep -A 10 "cobertura-maven-plugin" pom.xml
```

---

## Step 3: Collect Maven Build Parameters

### 3.1 Core Properties (including CUSTOM properties)

```bash
# Collect ALL properties (including custom-named ones)
grep -E "<properties>" -A 100 pom.xml | head -120

# ⚠️ IMPORTANT: Custom properties use various naming patterns:
# - surefire.excludedGroups (custom surefire config)
# - jacoco.skip (custom JaCoCo control)
# - log4j.configuration (custom logging config)
# - Any project-specific: myapp.property, custom.setting, etc.
```

**⚠️ CRITICAL: Custom Property Naming Patterns**

| Custom Pattern | Standard Equivalent | Note |
|----------------|-------------------|------|
| `surefire.excludedGroups` | `excludedGroups` | Same meaning, different name |
| `surefire.groups` | `groups` | Same meaning |
| `jacoco.skip` | - | JaCoCo skip flag |
| `lombok.skip` | - | Lombok skip flag |
| Any `${something}` | - | May be referenced in pom.xml |

**Check ALL properties, not just standard ones:**
```bash
# List every property (including custom)
mvn help:evaluate -DforceStdout -q 2>/dev/null | grep -E "^[a-zA-Z]" | head -30

# Or get effective POM
mvn help:effective-pom 2>/dev/null | grep -E "<properties>|<[a-z]+[.][a-zA-Z]+>" | head -30
```

### 3.2 Test Filtering (Surefire) - Check BOTH Standard AND Custom

```bash
# Collect surefire plugin configuration
grep -A 20 "maven-surefire-plugin" pom.xml

# ⚠️ ALSO check custom property names for surefire:
grep -E "surefire\.|surefire\.excluded|surefire\.groups|surefire\.skip" pom.xml
```

| Standard Parameter | Custom Property | Purpose | Override |
|-----------|---------|---------|---------|
| `excludedGroups` | `surefire.excludedGroups` | Groups to exclude | `-DexcludedGroups=` |
| `includedGroups` | `surefire.groups` | Groups to include | `-Dgroups=` |
| `test` | - | Specific test | `-Dtest=TestClass#method` |
| `argLine` | - | JVM args | `-DargLine="-Xmx512m"` |
| `skipTests` | `surefire.skipTests` | Skip tests | `-DskipTests=false` |
| `runOrder` | - | Test order | `-DrunOrder=random` |

**⚠️ IMPORTANT - argLine for JaCoCo:**
```bash
# If JaCoCo is configured, argLine MUST include:
-Djacoco-agent.destfile=...
# Example:
<argLine>-Xmx512m -XX:MaxPermSize=256m @{jacoco.agent}</argLine>
```

**⚠️ IMPORTANT - Custom properties must be APPLIED:**
```bash
# pom.xml may define: <surefire.excludedGroups>real-server</surefire.excludedGroups>
# But Maven uses: ${surefire.excludedGroups} in plugin config

# To override custom properties:
mvn test -Dsurefire.excludedGroups=
mvn test -Dsurefire.groups=
```

### 3.3 Profiles

```bash
# List all defined profiles
grep -E "<profile>" -A 30 pom.xml | grep -E "<id>"

# Show active profiles
mvn help:active-profiles 2>/dev/null

# Show all profiles (including inactive)
mvn help:all-profiles 2>/dev/null
```

| Profile Element | Purpose |
|----------------|---------|
| `<id>` | Profile identifier |
| `<activation>` | Auto-activation conditions |
| `<properties>` | Profile-specific properties |
| `<build>` | Profile-specific build config |
| `<repositories>` | Profile-specific repositories |
| `<pluginRepositories>` | Profile-specific plugin repos |

**Common Profile Patterns:**
```xml
<!-- Skip real-server integration tests -->
<profile>
    <id>ci</id>
    <properties>
        <excludedGroups>real-server</excludedGroups>
    </properties>
</profile>

<!-- Coverage profile -->
<profile>
    <id>coverage</id>
    <properties>
        <argLine>-Xmx512m @{jacoco.agent}</argLine>
    </properties>
</profile>
```

### 3.4 Build Directory Structure

```bash
# Check for custom source directories
grep -E "<sourceDirectory>|<testSourceDirectory>|<resources>|<testResources>" pom.xml
```

| Element | Default | Purpose |
|---------|---------|---------|
| `<sourceDirectory>` | `src/main/java` | Main source root |
| `<testSourceDirectory>` | `src/test/java` | Test source root |
| `<resources>` | `src/main/resources` | Main resources |
| `<testResources>` | `src/test/resources` | Test resources |

### 3.5 Dependency Management

```bash
# Check dependencyManagement section
grep -E "<dependencyManagement>" -A 50 pom.xml | head -60

# List effective dependency versions
mvn dependency:tree -Dverbose 2>/dev/null | head -50
```

**Important for tests:**
- Managed versions in `<dependencyManagement>`
- `<scope>test</scope>` dependencies
- `<optional>true</optional>` dependencies (not transitive)

### 3.6 Plugin Management

```bash
# Check pluginManagement section
grep -E "<pluginManagement>" -A 50 pom.xml | head -60

# List all plugins
mvn help:all-profiles 2>/dev/null | grep "BUILD"
```

| Plugin | Purpose |
|--------|---------|
| `maven-compiler-plugin` | Java compilation |
| `maven-surefire-plugin` | Test execution |
| `maven-surefire-report-plugin` | Test reporting |
| `jacoco-maven-plugin` | Code coverage |
| `maven-jar-plugin` | JAR packaging |
| `maven-shade-plugin` | Uber JAR creation |

### 3.7 Repositories (Important for Offline/CI)

```bash
# Check repositories
grep -E "<repositories>|<repository>" -A 5 pom.xml

# Check plugin repositories
grep -E "<pluginRepositories>|<pluginRepository>" -A 5 pom.xml
```

| Element | Purpose | Note |
|---------|---------|------|
| `<repositories>` | Dependency repos | May need auth |
| `<pluginRepositories>` | Plugin repos | May need auth |
| `<id>` | Repository identifier | Used in settings.xml |
| `<url>` | Repository URL | |
| `<releases>/<snapshots>` | Release/Snapshot policies | |

### 3.8 Environment Variables for Tests

```bash
# Check for environment variables passed to tests
grep -E "<environmentVariables>|<systemPropertyVariables>" pom.xml
```

| Element | Purpose |
|---------|---------|
| `<environmentVariables>` | Env vars for forked JVM |
| `<systemPropertyVariables>` | System props passed to tests |
| `<workingDirectory>` | Working directory for tests |

---

## Step 4: Apply Parameters in Commands (必须!)

**⚠️ CRITICAL: Parameters from pom.xml are MEANT TO BE USED.**

Checking is not enough - you must APPLY them in your commands:

### Apply Profiles

```bash
# Run with specific profile
mvn test -P<profile-id>

# Run with multiple profiles
mvn test -Pprofile1,profile2

# Skip a profile
mvn test -P!skip-this-profile

# Show which profiles are active and why
mvn help:active-profiles -X 2>&1 | grep -E "Profile|ACTIV"
```

### Apply Test Parameters (BOTH Standard AND Custom)

```bash
# Override standard excludedGroups
mvn test -DexcludedGroups=

# Override CUSTOM surefire.excludedGroups
mvn test -Dsurefire.excludedGroups=

# Override standard skipTests
mvn test -DskipTests=false

# Override CUSTOM surefire.skipTests
mvn test -Dsurefire.skipTests=false

# Run specific test class
mvn test -Dtest=ClassNameTest

# Run specific test method
mvn test -Dtest=ClassNameTest#methodName

# Provide JaCoCo agent manually if argLine missing
mvn test -DargLine="-Xmx512m @{jacoco.agent}"
```

### Verify Profile Properties Applied

```bash
# Verify profile properties are applied
mvn help:evaluate -Dexpression=some.property 2>/dev/null | grep -v "INFO"

# Show effective POM with all profiles merged (ALL properties visible)
mvn help:effective-pom > effective-pom.xml

# Find custom properties in effective POM
grep -E "surefire\.|jacoco\.|lombok\.|myapp\.|custom\." effective-pom.xml
```

### Find ALL Custom Properties

```bash
# Method 1: Get all properties from effective POM
mvn help:effective-pom 2>/dev/null | grep -E "<[a-z]+[.][a-zA-Z]+>" | head -30

# Method 2: List all project properties
mvn help:evaluate -DforceStdout 2>/dev/null | grep -v "INFO"

# Method 3: Search for custom naming patterns
grep -E "surefire\.|jacoco\." pom.xml
```

---

## Step 5: Full Maven Help

```bash
# Get complete effective POM with all profiles merged
mvn help:effective-pom > effective-pom.xml

# Get effective settings
mvn help:effective-settings

# List all build properties
mvn help:evaluate -DforceStdout 2>/dev/null | grep -v "INFO"

# Show which profiles are being used
mvn diagnose -X 2>&1 | grep -E "profile|Profile"
```

---

## Parameter Override Priority

Parameters override each other (high wins):

```
1. Command line:    -Dproperty=value
2. Profile:        <profile><properties>
3. pom.xml:        <properties>
4. Parent POM:     inherited
```

**Example:**
```xml
<!-- pom.xml has: skipTests=false -->
<!-- Profile has: skipTests=true -->
<!-- Command line: (none) -->

<!-- Result: skipTests=true (profile wins) -->

<!-- To override: mvn test -DskipTests=false -->

<!-- Command line ALWAYS wins -->
```

---

## Common Issues & Fixes

| Issue | Check | Fix |
|-------|-------|-----|
| Tests skipped | `grep skipTests pom.xml` | `mvn test -DskipTests=false` |
| Wrong groups | `grep excludedGroups pom.xml` | `mvn test -DexcludedGroups=` |
| No coverage | Check argLine for JaCoCo | `mvn test -DargLine="@{jacoco.agent}"` |
| Profile not active | `mvn help:active-profiles` | `mvn test -P<id>` |
| Custom src dir | `grep sourceDirectory pom.xml` | Ensure correct path |
| Wrong encoding | `grep sourceEncoding pom.xml` | `mvn compile -Dproject.build.sourceEncoding=UTF-8` |
