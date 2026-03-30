# Evidence-Based Verification (详细)

## The Iron Law

```
NO CLAIMS WITHOUT EVIDENCE
No completion claims without running verification commands and confirming output
```

## Verification Gate (必须执行)

For ANY claim, you MUST:

| Claim | Required Evidence |
|-------|------------------|
| "Compilation succeeded" | Exit code 0 + "BUILD SUCCESS" in output |
| "Tests passed" | `mvn test` output shows `Failures: 0, Errors: 0` |
| "Coverage is 100%" | JaCoCo report or `mvn jacoco:check` output |
| "Test compiles" | Exit code 0 + no error messages |

## Verification Pattern (必须遵循)

```
1. IDENTIFY: What command proves this claim?
2. RUN: Execute the FULL command (fresh, complete)
3. READ: Full output, check exit code, count failures
4. VERIFY: Does output confirm the claim?
   - If NO: State actual status with evidence
   - If YES: State claim WITH evidence
5. ONLY THEN: Make the claim
```

## Wrong vs Right

| Wrong (不可做) | Right (必须做) |
|---------------|---------------|
| "Should compile now" | Run `mvn test-compile`, see exit code 0 |
| "Tests probably pass" | Run `mvn test`, see `Failures: 0, Errors: 0` |
| "Looks correct" | Show actual output with numbers |
| "Coverage seems good" | Run `mvn jacoco:check`, see 100% line/branch |
| "Build succeeded" | See "BUILD SUCCESS" in output |

## Common Verification Commands

```bash
# Verification 1: Compile
mvn compile
# Evidence: Exit code 0, "BUILD SUCCESS"

# Verification 2: Test Compile
mvn test-compile
# Evidence: Exit code 0, "BUILD SUCCESS"

# Verification 3: Run Tests
mvn test
# Evidence: "Tests run: X, Failures: 0, Errors: 0, Skipped: 0"

# Verification 4: Coverage Check
mvn jacoco:check
# Evidence: Exit code 0, "COVEREDRATIO" is 1.00
```

## Exit Criteria Examples

### Before claiming "Compilation succeeded"
```bash
$ mvn compile
[INFO] BUILD SUCCESS
[INFO] Total time: 5.234 s
```
✅ Claim: "mvn compile succeeded" (evidence: BUILD SUCCESS, exit 0)

### Before claiming "Tests passed"
```bash
$ mvn test
[INFO] Tests run: 42, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
✅ Claim: "All 42 tests passed" (evidence: Failures: 0, Errors: 0)

### Before claiming "Coverage is 100%"
```bash
$ mvn jacoco:check
[INFO] All coverage checks passed
```
✅ Claim: "100% coverage verified" (evidence: All coverage checks passed)
