# Test Failure Debugging (详细)

## The Iron Law

```
NO FIXES WITHOUT ROOT CAUSE INVESTIGATION FIRST
If you haven't completed Phase 1, you cannot propose fixes.
```

When `mvn test` fails, follow this 4-phase process before attempting any fix.

---

## Phase 1: Root Cause Investigation (MUST COMPLETE FIRST)

### 1. Read Error Messages Carefully

- Don't skip past errors or warnings
- Read stack traces completely
- Note: line numbers, file paths, error codes

```bash
# Run tests and capture full output
mvn test > test-output.txt 2>&1

# Look for error patterns
grep -A 10 "ERROR" test-output.txt
grep -B 5 "FAILURE" test-output.txt
```

### 2. Reproduce Consistently

- Can you trigger it reliably?
- What are the exact steps?
- Does it happen every time?
- If not reproducible → gather more data, don't guess

```bash
# Run single test to reproduce
mvn test -Dtest=FailedTestName

# Run with verbose output
mvn test -Dtest=FailedTestName -X
```

### 3. Check Recent Changes

```bash
# What changed that could cause this?
git diff HEAD~1 --stat

# Check specific files
git diff HEAD~1 src/test/java/...

# Look for related commits
git log --oneline -5 -- src/test/java/...
```

### 4. Gather Evidence in Multi-Component Systems

If system has multiple layers:

```
For EACH component boundary:
  - Log what data enters component
  - Log what data exits component
  - Verify environment/config propagation
  - Check state at each layer
```

---

## Phase 2: Pattern Analysis

**Find the pattern before fixing:**

### 1. Find Working Examples
- Locate similar working tests in same codebase
- What works that's similar to what's broken?

### 2. Identify Differences
- What's different between working and broken?
- List every difference, however small
- Don't assume "that can't matter"

```bash
# Compare with working test
grep -A 20 "testThatWorks" src/test/java/.../SomeTest.java
grep -A 20 "testThatFails" src/test/java/.../SomeTest.java
```

---

## Phase 3: Hypothesis and Testing

**Scientific method:**

### 1. Form Single Hypothesis
```
"I think X is the root cause because Y"
```
Write it down.

### 2. Test Minimally
- Make the SMALLEST possible change to test hypothesis
- One variable at a time
- Don't fix multiple things at once

### 3. Verify Before Continuing
- Did it work? Yes → Phase 4
- Didn't work? Form NEW hypothesis
- DON'T add more fixes on top

---

## Phase 4: Implementation

### 1. Create Failing Test Case (REGRESSION TEST)

```java
// Before fixing, write a test that reproduces the bug
@Test
public void testBugReproduction() {
    // This test should FAIL before the fix
    // and PASS after the fix
    assertEquals("expected", buggyMethod());
}
```

**Important:** This test proves:
- The bug existed
- The fix actually works
- Future changes won't reintroduce the bug

### 2. Implement Single Fix

- Address the root cause identified
- ONE change at a time
- No "while I'm here" improvements
- No bundled refactoring

### 3. Verify Fix

```bash
mvn test
# Test passes now?
# No other tests broken?
```

### 4. If 3+ Fixes Failed: Question Architecture

**Pattern indicating architectural problem:**
- Each fix reveals new shared state/coupling/problem in different place
- Fixes require "massive refactoring" to implement
- Each fix creates new symptoms elsewhere

**STOP and question fundamentals:**
- Is this pattern fundamentally sound?
- Are we "sticking with it through sheer inertia"?
- Should we refactor architecture vs. continue fixing symptoms?

---

## Debugging Red Flags - STOP

If you catch yourself thinking:
- "Quick fix for now, investigate later"
- "Just try changing X and see if it works"
- "Add multiple changes, run tests"
- "Skip the test, I'll manually verify"
- "It's probably X, let me fix that"

**ALL of these mean: STOP. Return to Phase 1.**

---

## Quick Reference

| Phase | Key Activities | Success Criteria |
|-------|---------------|------------------|
| **1. Root Cause** | Read errors, reproduce, check changes, gather evidence | Understand WHAT and WHY |
| **2. Pattern** | Find working examples, compare | Identify differences |
| **3. Hypothesis** | Form theory, test minimally | Confirmed or new hypothesis |
| **4. Implementation** | Create test, fix, verify | Bug resolved, tests pass |

---

## When Process Reveals "No Root Cause"

If systematic investigation reveals issue is truly environmental, timing-dependent, or external:

1. You've completed the process
2. Document what you investigated
3. Implement appropriate handling (retry, timeout, error message)
4. Add monitoring/logging for future investigation

**But:** 95% of "no root cause" cases are incomplete investigation.
