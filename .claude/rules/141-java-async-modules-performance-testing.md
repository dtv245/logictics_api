---
description: "Modern Java Architecture: CompletableFuture, JPMS modules, performance considerations, and testing strategies"
paths:
  - "**/*.java"
---

# Java Async, JPMS Modules, Performance & Testing

## 1. `CompletableFuture` Asynchronous Programming

- **MUST** chain asynchronous tasks using non-blocking methods (`thenApplyAsync()`, `thenComposeAsync()`, `thenAcceptAsync()`).
- **MUST** explicitly handle exceptions in the pipeline using `.exceptionally()` or `.handle()`.
- **MUST** pass a custom dedicated `Executor` for I/O-heavy async tasks instead of abusing `ForkJoinPool.commonPool()`.
- **MUST NOT** call blocking `.get()` or `.join()` on main application/request threads.

```java
// GOOD: Asynchronous chain with custom executor and exception handling
ExecutorService customExecutor = Executors.newFixedThreadPool(8);

CompletableFuture<Void> future = CompletableFuture
    .supplyAsync(() -> fetchData("query"), customExecutor)
    .thenApplyAsync(data -> processData(data), customExecutor)
    .thenAcceptAsync(result -> saveData(result), customExecutor)
    .exceptionally(ex -> {
        logger.error("Async execution failed", ex);
        return null;
    });
```

## 2. Java Platform Module System (JPMS)

- **MUST** specify explicit dependencies using `requires` and public packages using `exports` inside `module-info.java`.
- **MUST** encapsulate internal packages by NOT exporting them in `module-info.java`.
- **MUST** use `opens` only when reflection is required by frameworks (e.g., Spring, Jackson, Hibernate).

```java
// GOOD: Strongly encapsulated module-info.java
module com.example.service {
    requires java.base;
    requires java.logging;
    requires com.fasterxml.jackson.databind;

    exports com.example.service.api;
    opens com.example.service.dto to com.fasterxml.jackson.databind;
}
```

## 3. Performance Considerations with Modern Features

- **MUST** profile code before optimizing; avoid premature optimization.
- **MUST NOT** use parallel streams for small collections or I/O-bound operations due to thread splitting overhead.
- **MUST** use primitive streams (`IntStream`, `LongStream`, `DoubleStream`) to eliminate autoboxing overhead in arithmetic operations.
- **DO** implement lazy initialization safely using double-checked locking with `volatile` fields or `Supplier` memoization.

```java
// GOOD: Primitive stream avoiding boxing
long sum = LongStream.rangeClosed(1, 1_000_000)
    .filter(n -> n % 2 == 0)
    .sum();
```

## 4. Testing Modern Java Code

- **MUST** test edge cases for functional constructs (empty streams, null values, unmatched filters).
- **MUST** test both present and empty paths for methods returning `Optional` (leveraging AssertJ `isPresent()`, `isEmpty()`).
- **MUST** use non-blocking assertion tools like Awaitility when testing `CompletableFuture` async workflows.

```java
// GOOD: AssertJ testing with Optional & Streams
@Test
void testFindUser() {
    Optional<User> result = userService.findUserById("123");
    assertThat(result).isPresent().hasValueSatisfying(u -> {
        assertThat(u.getName()).isEqualTo("Alice");
    });
}
```
