# Coverage Requirements (详细)

## Coverage Goals

| Type | Target | Command Line |
|---------------|--------|--------------|
| Line Coverage | 100% | JaCoCo line coverage |
| Branch Coverage | 100% | JaCoCo branch coverage |

---

## Coverage Verification Commands

```bash
# Run tests with coverage report
mvn clean test

# Generate JaCoCo report
mvn jacoco:report

# Check coverage report at: target/site/jacoco/index.html
# Or use command line to check:
mvn jacoco:check
```

---

## Coverage Report Location

```
target/site/jacoco/index.html          # Main coverage report
target/site/jacoco/{package}/index.html # Per-package coverage
```

---

## Adding New Tests Workflow

### Step 1: Identify Code to Test
- Find methods/branches with no test coverage
- Review JaCoCo report for uncovered lines

### Step 2: Add Test Cases to Achieve 100% Coverage
- Write tests for each uncovered line
- Write tests for each branch (if/else, switch, etc.)

### Step 3: Run Tests
```bash
mvn test
```

### Step 4: Check Coverage
```bash
mvn jacoco:check
```

### Step 5: If Coverage < 100%, Add More Tests
- Repeat until coverage report shows 100% line AND branch

### Step 6: Final Verification
- All tests pass
- JaCoCo report shows 100% line coverage
- JaCoCo report shows 100% branch coverage

---

## Wrong vs Right

### WRONG: Add tests without checking coverage
```bash
# Just ensure tests pass
mvn test
# "Coverage is 85%, close enough"
return  # DO NOT STOP
```

### CORRECT: Add tests and verify 100% coverage
```bash
mvn test
# Coverage is 85%, need more tests
# Add more test cases
mvn test
# Coverage is 92%, still need more
# Add more test cases
mvn test
# Coverage is 100%, pass rate 100%
# ONLY NOW the task is complete
```

---

## Common Coverage Gaps

| Gap Type | Description | Example |
|----------|-------------|---------|
| Uncovered lines | Code never executed | `if (condition)` with false condition |
| Uncovered branches | If/else paths not tested | Both true and false branches |
| Exception paths | Catch blocks not tested | Exception handling code |
| Edge cases | Boundary conditions | Empty, null, max values |

---

## Branch Coverage Example

```java
// Source code
public int absoluteValue(int n) {
    if (n >= 0) {      // Branch: true
        return n;       // Line 2 covered when n >= 0
    } else {            // Branch: false
        return -n;      // Line 4 covered when n < 0
    }
}

// Test for 100% branch coverage
@Test
public void testAbsoluteValue_Positive() {
    assertEquals(5, absoluteValue(5));   // Covers n >= 0
}

@Test
public void testAbsoluteValue_Negative() {
    assertEquals(5, absoluteValue(-5));  // Covers n < 0
}
```

---

## JaCoCo Configuration

If coverage checks fail, verify pom.xml configuration:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>1.00</minimum>
                            </limit>
                            <limit>
                                <counter>BRANCH</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>1.00</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

---

## Exit Criteria Summary

| Requirement | Target | Verification |
|-------------|--------|--------------|
| Line coverage | 100% | `mvn jacoco:check` |
| Branch coverage | 100% | `mvn jacoco:check` |
| All tests pass | 100% | `mvn test` |
