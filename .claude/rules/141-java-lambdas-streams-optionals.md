---
description: "Modern Java: Lambdas, Functional Interfaces, Stream API, and Optional API"
paths:
  - "**/*.java"
---

# Java Lambdas, Streams & Optional Guidelines

## 1. Lambda Expressions & Functional Interfaces

- **MUST** prefer lambda expressions over anonymous inner classes for implementing single-abstract-method interfaces.
- **MUST** use method references (`Class::method`, `instance::method`) when they are cleaner and more direct than equivalent lambdas.
- **MUST** annotate custom functional interfaces with `@FunctionalInterface`.
- **MUST** keep lambdas short, side-effect-free, and focused on a single responsibility.
- **DO** leverage standard `java.util.function` interfaces (`Predicate`, `Function`, `Consumer`, `Supplier`) instead of custom ones.

```java
// GOOD: Method references and built-in functional interfaces
List<String> names = List.of("Alice", "Bob", "Charlie");

// Method reference
names.forEach(System.out::println);

// Predicate & Streams
List<String> filtered = names.stream()
    .filter(name -> name.length() > 3)
    .collect(Collectors.toList());

// Custom annotated functional interface
@FunctionalInterface
interface Transformer<T, R> {
    R transform(T input);
}
```

## 2. Stream API Best Practices

- **MUST** use streams for declarative sequence processing; prefer pipelines (`filter`, `map`, `distinct`, `sorted`, `collect`) over manual `for` loop mutations.
- **MUST NOT** mutate external state inside stream lambda operations (avoid side-effects in `peek` or `forEach`).
- **MUST** use `parallelStream()` ONLY for CPU-intensive operations on large data sets after profiling proves performance gains.
- **DO** use appropriate collectors (`Collectors.toList()`, `Collectors.toSet()`, `Collectors.groupingBy()`).

```java
// GOOD: Declarative stream pipeline
List<String> cleanedData = rawInput.stream()
    .filter(Objects::nonNull)
    .map(String::trim)
    .filter(s -> !s.isEmpty())
    .map(String::toLowerCase)
    .distinct()
    .sorted()
    .collect(Collectors.toList());

// BAD: Mutating external collection inside stream forEach
List<String> result = new ArrayList<>();
rawInput.stream().forEach(s -> result.add(s.toUpperCase())); // Anti-pattern!
```

## 3. Optional API Usage

- **MUST** use `Optional<T>` as a method return type for values that may be absent.
- **MUST NOT** call `Optional.get()` directly without `isPresent()`; prefer fluent methods (`orElse()`, `orElseGet()`, `orElseThrow()`, `map()`, `flatMap()`).
- **MUST NOT** use `Optional` for class fields, constructor parameters, or method arguments.
- **DO** use `orElseGet(Supplier)` when creating the fallback value is computationally expensive.

```java
// GOOD: Functional chain with Optional
public String getUserCountryCode(User user) {
    return Optional.ofNullable(user)
        .flatMap(User::getAddress)
        .flatMap(Address::getCountry)
        .map(Country::getCode)
        .orElse("UNKNOWN");
}

// BAD: Dangerous Optional.get() call
public String getUnsafe(Optional<String> opt) {
    return opt.get(); // Throws NoSuchElementException if empty!
}
```
