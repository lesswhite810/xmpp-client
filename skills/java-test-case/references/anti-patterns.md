# Test Quality & Anti-Patterns (详细)

## The Iron Law

```
Test what the code does, not what the mocks do.
Tests that verify mock behavior provide false confidence while catching zero real bugs.
```

---

## The Five Anti-Patterns

### Anti-Pattern 1: Testing Mock Behavior ❌

**The Problem:** Verifying that mocks exist and were called, rather than testing actual component output.

```java
// ❌ BAD: Testing the mock, not the behavior
@Test
public void testShouldCallTheApi() {
    when(mockApi.getData()).thenReturn("test");
    service.process();
    verify(mockApi).getData(); // Testing mock, not result!
}

// ✅ GOOD: Testing actual behavior
@Test
public void testShouldReturnProcessedData() {
    when(mockApi.getData()).thenReturn("test");
    String result = service.process();
    assertEquals("processed: test", result); // Testing actual output
}
```

**Solution:** Test the genuine component output. If you can only verify mock calls, reconsider whether the test adds value.

---

### Anti-Pattern 2: Test-Only Methods in Production ❌

**The Problem:** Adding methods to production classes solely for test setup or cleanup.

```java
// ❌ BAD: Production code polluted with test concerns
public class UserCache {
    private Map<Integer, User> cache = new HashMap<>();
    
    public User getUser(Integer id) {
        return cache.get(id);
    }
    
    // This method exists ONLY for tests - DON'T DO THIS
    public void _resetForTesting() {
        cache.clear();
    }
}

// ✅ GOOD: Test utilities separate from production
public class UserCache {
    private Map<Integer, User> cache = new HashMap<>();
    
    public User getUser(Integer id) {
        return cache.get(id);
    }
}

// In test: Use fresh instance per test
@BeforeEach
public void setUp() {
    cache = new UserCache(); // Fresh instance
}
```

**Solution:** Relocate cleanup logic to test utility functions. Use fresh instances per test instead of reset methods.

---

### Anti-Pattern 3: Mocking Without Understanding ❌

**The Problem:** Over-mocking without grasping side effects, leading to tests that pass but hide real issues.

```java
// ❌ BAD: Mocking everything without understanding
@Test
public void testProcessOrder() {
    when(inventoryService.checkStock(any())).thenReturn(true);
    when(paymentService.charge(any())).thenReturn(true);
    when(shippingService.ship(any())).thenReturn(true);
    when(notificationService.notify(any())).thenReturn(true);
    
    result = orderProcessor.process(order);
    assertTrue(result.isSuccess()); // What did we actually test?
}

// ✅ GOOD: Strategic mocking with real components where possible
@Test
public void testProcessOrderWithRealInventory() {
    // Real inventory service against test database
    InventoryService realInventory = new InventoryService(testDb);
    
    // Mock only external services
    PaymentService mockPayment = mock(PaymentService.class);
    when(mockPayment.charge(any())).thenReturn(true);
    
    OrderProcessor processor = new OrderProcessor(realInventory, mockPayment);
    Result result = processor.process(order);
    
    assertTrue(result.isSuccess());
    assertEquals(originalStock - 1, realInventory.getStock(order.getItemId()));
}
```

**Solution:** Run tests with real implementations first to understand behavior. Then mock at the appropriate level - external services, not internal logic.

---

### Anti-Pattern 4: Incomplete Mocks ❌

**The Problem:** Partial mock responses missing downstream fields that production code expects.

```java
// ❌ BAD: Incomplete mock response
@Test
public void testGetUser() {
    when(userApi.getUser(1)).thenReturn(
        new User() {{ setId(1); setName("Test"); }}
        // Missing: email, createdAt, permissions, settings...
    );
    
    User result = service.getUser(1);
    result.getEmail(); // NPE! Production crashes
}

// ✅ GOOD: Complete mock matching real API response
@Test
public void testGetUser() {
    User mockUser = User.builder()
        .id(1)
        .name("Test")
        .email("test@example.com")
        .createdAt(LocalDateTime.now())
        .permissions(List.of("read", "write"))
        .settings(UserSettings.builder().theme("light").build())
        .build();
    
    when(userApi.getUser(1)).thenReturn(mockUser);
    User result = service.getUser(1);
    assertEquals("test@example.com", result.getEmail()); // Works
}
```

**Solution:** Mirror complete real API response structure. Use factories to generate complete mock objects with sensible defaults.

---

### Anti-Pattern 5: Integration Tests as Afterthought ❌

**The Problem:** Treating testing as optional follow-up work rather than integral to development.

```
Day 1: Write 500 lines of code
Day 2: Write 500 more lines
Day 3: "We need to ship, tests can wait"
Day 30: Catastrophic bug in production
Day 31: "Why didn't we have tests?"
```

**Solution:** Follow TDD - testing is implementation, not documentation. No feature is "done" without tests.

```java
// ✅ GOOD: Tests are part of implementation
@Test
public void testShouldRejectDuplicateUsernames() {
    // Write failing test first
    assertThrows(UsernameExistsException.class, 
        () -> userService.createUser("alice"));
    
    userService.createUser("alice");
    
    // Then implement
    assertThrows(UsernameExistsException.class, 
        () -> userService.createUser("alice"));
}
```

---

## Anti-Pattern Detection Checklist

Review your tests for these warning signs:

| Warning Sign | Anti-Pattern |
|-------------|--------------|
| `verify(mock).toHaveBeenCalled()` without testing output | Testing mock behavior |
| Methods starting with `_` or `ForTesting` in production | Test-only methods |
| Every dependency is mocked | Mocking without understanding |
| Mocks return only `{ success: true }` only | Incomplete mocks |
| Test files added weeks after feature ships | Tests as afterthought |

---

## Quick Reference

| Anti-Pattern | Symptom | Fix |
|-------------|---------|-----|
| Testing mocks | Only mock assertions, no behavior tests | Assert on actual output |
| Test-only methods | `_reset()`, `_setForTest()` in prod | Use fresh instances |
| Over-mocking | 10+ mocks per test | Test with real deps first |
| Incomplete mocks | Minimal stub responses | Use factories, match reality |
| Tests as afterthought | Features ship untested | TDD from the start |
