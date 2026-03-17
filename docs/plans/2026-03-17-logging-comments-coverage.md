# Logging Comments Coverage Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reduce redundant logging, simplify and complete JavaDoc comments, and raise JaCoCo line and branch coverage toward the configured 80% threshold.

**Architecture:** Focus on core lifecycle and utility classes where duplicated logs and comment drift are concentrated. Add small, behavior-oriented unit tests around connection lifecycle, event dispatch, and helper utilities so coverage moves on high-fanout code instead of slow integration paths.

**Tech Stack:** Java 21, Maven, JUnit 5, Mockito, JaCoCo, Lombok, Netty

---

### Task 1: Audit high-signal targets

**Files:**
- Modify: `src/main/java/com/example/xmpp/XmppTcpConnection.java`
- Modify: `src/main/java/com/example/xmpp/util/ConnectionUtils.java`
- Modify: `src/main/java/com/example/xmpp/net/XmppNettyHandler.java`
- Test: `src/test/java/com/example/xmpp/net/XmppConnectionLifecycleErrorTest.java`

**Step 1: Write the failing test**

Add or refine tests that assert the connection lifecycle still behaves correctly after log simplification and target-resolution cleanup.

**Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=XmppConnectionLifecycleErrorTest#testConnectAsyncUsesServiceDomainWhenHostIsBlank test`

Expected: fail if target resolution or lifecycle behavior is broken.

**Step 3: Write minimal implementation**

Remove duplicated log statements and keep one structured log per failure path. Keep connection target selection minimal: configured host/address first, service domain fallback second.

**Step 4: Run test to verify it passes**

Run the same focused Maven command and confirm success.

**Step 5: Commit**

Skip commit in this session unless explicitly requested.

### Task 2: Normalize JavaDoc

**Files:**
- Modify: `src/main/java/com/example/xmpp/**/*.java`

**Step 1: Write the failing test**

Use documentation review as the failing condition: identify public classes and methods with missing, stale, or overly verbose JavaDoc, especially missing `@param`, `@return`, and `@throws`.

**Step 2: Run test to verify it fails**

Run: `rg -n "@param|@return|@throws|/\\*\\*" src/main/java/com/example/xmpp`

Expected: find mismatched or incomplete JavaDoc in edited files.

**Step 3: Write minimal implementation**

Simplify descriptions, keep one-sentence intent, and fill only necessary tags. Avoid decorative prose and outdated behavior notes.

**Step 4: Run test to verify it passes**

Re-run the grep and manually inspect touched files for concise and complete JavaDoc.

**Step 5: Commit**

Skip commit in this session unless explicitly requested.

### Task 3: Raise coverage on core units

**Files:**
- Modify: `src/test/java/com/example/xmpp/util/ConnectionUtilsTest.java`
- Modify: `src/test/java/com/example/xmpp/net/XmppNettyHandlerTest.java`
- Modify: `src/test/java/com/example/xmpp/net/XmppConnectionLifecycleErrorTest.java`
- Modify: `src/test/java/com/example/xmpp/event/XmppEventBusTest.java`
- Modify: `src/test/java/com/example/xmpp/logic/PingManagerTest.java`
- Modify: `src/test/java/com/example/xmpp/logic/ReconnectionManagerTest.java`

**Step 1: Write the failing test**

Add focused tests for uncovered branches: stale-channel guards, lifecycle cleanup, dispatch failure handling, event bus unsubscribe paths, and utility error branches.

**Step 2: Run test to verify it fails**

Run each new test method directly with `mvn -q -Dtest=<ClassName>#<methodName> test`.

Expected: fail for the specific missing behavior before implementation.

**Step 3: Write minimal implementation**

Only adjust production code where tests reveal real gaps; otherwise add tests around existing behavior.

**Step 4: Run test to verify it passes**

Run the targeted tests plus the owning test class after each small batch.

**Step 5: Commit**

Skip commit in this session unless explicitly requested.

### Task 4: Measure bundle coverage

**Files:**
- Modify: `pom.xml` only if verification reveals configuration issues

**Step 1: Write the failing test**

Treat JaCoCo threshold failure as the failing condition.

**Step 2: Run test to verify it fails**

Run: `mvn verify`

Expected: either passes at 80% or reports the exact line/branch gap.

**Step 3: Write minimal implementation**

Add tests or small cleanup only in the modules named by the coverage report.

**Step 4: Run test to verify it passes**

Run `mvn verify` again and confirm both line and branch thresholds pass.

**Step 5: Commit**

Skip commit in this session unless explicitly requested.
