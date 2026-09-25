---
description: "Modern Java language features: java.time API, default methods, var, collection factories, text blocks"
paths:
  - "**/*.java"
---

# Modern Java Language Features

## 1. Modern Date/Time API (`java.time`)

- **MUST** replace legacy `java.util.Date`, `Calendar`, and `SimpleDateFormat` with immutable `java.time` types.
- **MUST** select appropriate types:
  - `Instant`: Machine timestamp in UTC.
  - `LocalDate`: Date only (year-month-day).
  - `LocalTime`: Time of day without timezone.
  - `LocalDateTime`: Local date and time without timezone offset.
  - `ZonedDateTime`: Full date-time with explicit timezone handling DST.
  - `Duration` / `Period`: Time-based vs Date-based elapsed spans.
- **MUST** use `DateTimeFormatter` (thread-safe) for parsing and formatting.

```java
// GOOD: Modern java.time usage
LocalDate today = LocalDate.now();
LocalDate tomorrow = today.plusDays(1);
ZonedDateTime utcNow = ZonedDateTime.now(ZoneId.of("UTC"));

DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
String formatted = LocalDateTime.now().format(formatter);
```

## 2. Default Methods in Interfaces

- **MUST** use default methods primarily to add new, non-breaking methods to existing interfaces without breaking implementers.
- **MUST** keep default method logic minimal, delegating to abstract interface methods or stateless utilities.
- **MUST NOT** use default methods to simulate state or complex base class inheritance.

```java
// GOOD: Non-breaking additive default method
public interface DataProcessor {
    String process(String data);

    default String processWithLogging(String data) {
        System.out.println("Processing: " + data);
        return process(data);
    }
}
```

## 3. Local Variable Type Inference (`var`)

- **MUST** use `var` ONLY for local variables when the concrete type is obvious from the right-hand assignment.
- **MUST NOT** use `var` for method return types, method parameters, or class fields.
- **MUST NOT** use `var` when it obscures variable type or intent (e.g. with generic return calls).

```java
// GOOD: Type obvious from initializer
var users = new ArrayList<User>();
var name = "Alice";
try (var is = new FileInputStream("file.txt")) { /* ... */ }

// BAD: Type obscured
var result = someService.execute(); // What type is result?
```

## 4. Collection Factory Methods

- **MUST** use `List.of()`, `Set.of()`, `Map.of()`, and `Map.ofEntries()` to create concise, unmodifiable collections.
- **MUST NOT** attempt to pass `null` elements/keys/values to factory methods (throws `NullPointerException`).
- **MUST NOT** attempt to mutate unmodifiable collections (`add()`, `remove()` throw `UnsupportedOperationException`).

```java
// GOOD: Unmodifiable collection factory methods
List<String> immutableList = List.of("A", "B", "C");
Set<Integer> immutableSet = Set.of(1, 2, 3);
Map<String, Integer> immutableMap = Map.of("one", 1, "two", 2);

// Mutable copy when mutation is needed
List<String> mutableList = new ArrayList<>(List.of("A", "B"));
mutableList.add("C");
```

## 5. Text Blocks for Multi-Line Strings

- **MUST** use Java text blocks (`"""..."""`) for formatted multi-line strings (JSON, SQL, HTML, XML).
- **DO** align the closing `"""` delimiter to control stripped leading whitespace properly.

```java
// GOOD: Text block for clean multi-line SQL and JSON
String sql = """
    SELECT id, name, email
    FROM users
    WHERE active = true
    ORDER BY name;
    """;

String json = """
    {
        "status": "SUCCESS",
        "code": 200
    }
    """;
```
