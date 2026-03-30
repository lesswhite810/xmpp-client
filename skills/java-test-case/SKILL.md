---
name: java-test-case
description: Use when creating Java test cases with Maven - handles environment setup, framework detection, parameter collection, naming conventions, and 100% pass rate requirements
---

# Java Test Case Skill

## Overview

Automates Java test case creation with Maven. Ensures environment match, detects frameworks, enforces naming conventions, and requires 100% pass rate with evidence.

## When to Use

- Creating new Java test cases
- Setting up Maven-based Java testing environment
- Running `mvn test` or `mvn test-compile`
- Debugging test failures
- Reviewing test quality
- Checking coverage with JaCoCo

---

## Core Workflow

### Step 1: Environment Setup (@environment.md)

**1.1 Read pom.xml version requirements**
```bash
grep -E "maven.compiler.source|maven.compiler.target" pom.xml
```

**1.2 Verify Java/Maven match**
```bash
java -version
mvn -version
```

**1.3 Check active profiles (IMPORTANT!)**
```bash
mvn help:active-profiles 2>/dev/null
```

**1.4 Collect ALL Maven parameters (including CUSTOM properties)**
```bash
# Standard parameters
grep -A 20 "maven-surefire-plugin" pom.xml
grep -A 10 "maven-compiler-plugin" pom.xml

# ⚠️ CUSTOM property patterns (MUST check):
grep -E "surefire\.|jacoco\.|lombok\.|myapp\." pom.xml

# All properties including custom
mvn help:effective-pom 2>/dev/null | grep -E "<[a-z]+[.][a-zA-Z]+>" | head -20
```

### Step 2: Framework Detection

```bash
# Detect test framework
grep -E "junit|testng|spock" pom.xml

# Detect coverage framework
grep -E "jacoco|cobertura" pom.xml
```

**Supported:**
- JUnit 5, JUnit 4, TestNG, Spock
- JaCoCo, Cobertura

### Step 3: Apply Parameters in Compilation (必须应用)

**⚠️ CRITICAL: Parameters are MEANT TO BE USED, not just checked.**

After collecting parameters, apply them in your commands:

```bash
# Template: mvn <goal> [options]

# Example with profile:
mvn test -P<profile-id>

# Example with JaCoCo (if argLine configured):
mvn test -Pcoverage    # Profile activates argLine with JaCoCo

# Example to OVERRIDE pom.xml settings:
mvn test -DexcludedGroups=      # Override to empty = run ALL groups
mvn test -DskipTests=false     # Override skipTests
mvn test -DargLine="..."       # Provide JaCoCo agent manually

# Example for specific test:
mvn test -Dtest=ClassNameTest#methodName

# Example with custom JVM args:
mvn test -DargLine="-Xmx512m -Dsomething=true"
```

**Parameter Override Priority (high to low):**
```
1. Command line: -Dproperty=value
2. Profile: <profile><properties>
3. pom.xml: <properties>
```

### Step 4: Execute Compilation & Tests

```bash
# Compile with active profiles applied
mvn compile

# Compile tests with all parameters
mvn test-compile

# Run tests (uses pom.xml settings, apply -P for profiles)
mvn test

# Coverage check (requires JaCoCo agent in argLine)
mvn test
mvn jacoco:check
```

---

## Parameters Checklist (must apply)

### Standard Parameters

| Parameter | Check | Apply | Override if Needed |
|-----------|-------|-------|-------------------|
| `active-profiles` | `mvn help:active-profiles` | `-P<id>` | `-P!skip` |
| `excludedGroups` | surefire plugin | auto (from pom) | `-DexcludedGroups=` |
| `includedGroups` | surefire plugin | auto (from pom) | `-Dgroups=` |
| `argLine` | surefire plugin | auto (from pom) | `-DargLine="..."` |
| `skipTests` | surefire plugin | auto (from pom) | `-DskipTests=false` |
| `test` | surefire plugin | auto (from pom) | `-Dtest=Class#method` |

### Custom Properties (MUST CHECK)

| Custom Property | Example | Check | Apply |
|----------------|---------|-------|-------|
| `surefire.excludedGroups` | `<surefire.excludedGroups>real-server</surefire.excludedGroups>` | `grep surefire.` | `-Dsurefire.excludedGroups=` |
| `surefire.groups` | `<surefire.groups></surefire.groups>` | `grep surefire.` | `-Dsurefire.groups=` |
| `surefire.skipTests` | May skip tests | `grep surefire.` | `-Dsurefire.skipTests=false` |
| `jacoco.skip` | Skip coverage | `grep jacoco.` | `-Djacoco.skip=false` |
| Any `${myapp.*}` | Custom app props | Check all props | `-Dmyapp.property=value` |

**⚠️ Find all custom properties:**
```bash
mvn help:effective-pom 2>/dev/null | grep -E "<[a-z]+[.][a-zA-Z]+>" | head -20
```

---

## Part C: Evidence-Based Verification (@verification.md)

### Iron Law: NO CLAIMS WITHOUT EVIDENCE

| Claim | Evidence |
|-------|----------|
| "Tests pass" | `mvn test` output: `Failures: 0, Errors: 0` |
| "Compiles" | Exit code 0, "BUILD SUCCESS" |
| "Coverage 100%" | `mvn jacoco:check` ratio = 1.00 |

### Verification Pattern
```
1. RUN command → 2. READ output → 3. VERIFY → 4. Claim with evidence
```

---

## Part E: Test Failure Debugging (@debugging.md)

### Iron Law: NO FIXES WITHOUT ROOT CAUSE FIRST

**4-Phase Process:**
```
Phase 1: Root Cause → Phase 2: Pattern → Phase 3: Hypothesis → Phase 4: Fix
```

**Phase 1 (MUST COMPLETE FIRST):**
- Read error messages completely
- Reproduce consistently
- Check recent changes (`git diff`)
- Gather evidence at component boundaries

**Phase 4: After root cause found:**
```bash
# Create regression test FIRST
mvn test  # Verify test fails

# Then fix
mvn test  # Verify test passes
```

---

## Part F: Test Quality (@anti-patterns.md)

### Iron Law: Test behavior, not mocks

**5 Anti-Patterns to Avoid:**

| Anti-Pattern | Bad | Good |
|--------------|-----|------|
| Mock behavior | `verify(mock).methodCall()` | `assertEquals(expected, result)` |
| Test-only methods | `_resetForTesting()` in prod | Fresh instance per test |
| Over-mocking | Mock every dependency | Mock only external services |
| Incomplete mocks | `{ success: true }` | Full response structure |
| Tests as afterthought | Add tests later | Feature + tests together |

---

## Part G: TDD Principles (@tdd.md)

### Iron Law: NO PRODUCTION CODE WITHOUT FAILING TEST FIRST

**Red-Green-Refactor Cycle:**
```
RED    → Write failing test    → mvn test (fail)
GREEN  → Minimal code         → mvn test (pass)
REFACTOR → Clean up           → mvn test (still pass)
```

---

## Part H: Coverage (@coverage.md)

| Type | Target | Command |
|------|--------|---------|
| Line | 100% | `mvn jacoco:check` |
| Branch | 100% | `mvn jacoco:check` |

---

## Quick Reference

### Compilation with Parameters

```bash
# Basic (uses pom.xml settings)
mvn compile
mvn test-compile
mvn test

# With profile
mvn test -P<profile-id>

# Override specific parameter
mvn test -DexcludedGroups= -DskipTests=false
```

### Common Overrides

| Override | Command | When |
|---------|---------|------|
| Run all groups | `-DexcludedGroups=` | Want to skip exclusions |
| Force tests on | `-DskipTests=false` | Tests skipped globally |
| Specific test | `-Dtest=Class#method` | Run single test |
| JaCoCo agent | `-DargLine="@{jacoco.agent}"` | Coverage not working |

### Evidence

| Task | Evidence |
|------|----------|
| Compile | Exit 0, BUILD SUCCESS |
| Test compile | Exit 0, BUILD SUCCESS |
| Run tests | Failures: 0, Errors: 0 |
| Coverage | Ratio 1.00 |
| Debug failure | See @debugging.md |

---

## Naming Conventions

| Element | Pattern | Example |
|---------|---------|---------|
| Test class | `{ClassName}Test` | `XmppConnectionTest` |
| Test method | `test{Method}_{scenario}_{result}` | `testSendStanza_whenValid_shouldSucceed` |

---

## Exit Criteria

### Environment Setup
- [ ] Java/Maven version matches pom.xml requirements
- [ ] Active profiles identified

### Parameter Collection (must apply in commands)
- [ ] `active-profiles` → Use `-P<id>` when running
- [ ] `excludedGroups` → No unintended test skips
- [ ] `argLine` → JaCoCo agent included (for coverage)
- [ ] `skipTests` → Tests actually run (not skipped)

### Compilation (with parameters applied)
- [ ] `mvn compile` → Exit 0, BUILD SUCCESS
- [ ] `mvn test-compile` → Exit 0, BUILD SUCCESS

### Test Execution
- [ ] `mvn test` → Failures: 0, Errors: 0
- [ ] `mvn jacoco:check` → 100% line & branch
- [ ] Tests follow naming convention
- [ ] Every test has assertions
- [ ] No anti-patterns (see @anti-patterns.md)

---

## References (按需加载)

| Reference | 何时加载 |
|-----------|---------|
| `@environment.md` | 环境检测详细步骤 |
| `@verification.md` | 证据验证完整流程 |
| `@debugging.md` | 测试失败调试4阶段 |
| `@anti-patterns.md` | 5种反模式详解 |
| `@tdd.md` | TDD红-绿-重构循环 |
| `@coverage.md` | 覆盖率检查详解 |

**加载方式：** 当需要详细信息时，读取对应 reference 文件。
