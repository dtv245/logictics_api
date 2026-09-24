---
description: "Java Object Creation, Method Design, and Exception Handling guidelines"
paths:
  - "**/*.java"
---

# Java Object Creation, Method Design & Exception Handling

## 1. Static Factory Methods Instead of Constructors

- **MUST** consider static factory methods (`of()`, `valueOf()`, `getInstance()`) to provide descriptive creation names and return cached instances.
- **DO** keep constructors `private` when static factory methods are the primary creation mechanism.

```java
// GOOD: Static factory method with caching
public class BigInteger {
    private final long val;
    private static final BigInteger ZERO = new BigInteger(0);

    private BigInteger(long val) { this.val = val; }

    public static BigInteger valueOf(long val) {
        if (val == 0) return ZERO; // Cached instance
        return new BigInteger(val);
    }
}
```

## 2. Builder Pattern for Many Constructor Parameters

- **MUST** use the Builder pattern when faced with classes requiring 4+ constructor parameters or many optional parameters.

```java
// GOOD: Builder pattern
public class NutritionFacts {
    private final int servingSize;
    private final int servings;
    private final int calories;
    private final int fat;

    public static class Builder {
        private final int servingSize, servings;
        private int calories = 0, fat = 0;

        public Builder(int servingSize, int servings) {
            this.servingSize = servingSize;
            this.servings = servings;
        }
        public Builder calories(int val) { calories = val; return this; }
        public Builder fat(int val) { fat = val; return this; }
        public NutritionFacts build() { return new NutritionFacts(this); }
    }

    private NutritionFacts(Builder b) {
        this.servingSize = b.servingSize;
        this.servings = b.servings;
        this.calories = b.calories;
        this.fat = b.fat;
    }
}
// Usage: new NutritionFacts.Builder(240, 8).calories(100).build();
```

## 3. Singletons: Use Enum Types

- **MUST** use single-element enums as the preferred approach for singletons to guarantee serialization safety and prevent reflection attacks.

```java
// GOOD: Enum singleton
public enum DatabaseConnection {
    INSTANCE;
    public void executeQuery(String sql) { /* ... */ }
}

// ALTERNATIVE: Static holder with private constructor
public class Logger {
    private static final Logger INSTANCE = new Logger();
    private Logger() {}
    public static Logger getInstance() { return INSTANCE; }
}
```

## 4. Prefer Dependency Injection to Hardwiring Resources

- **MUST** pass resources/dependencies into constructors rather than creating them hardcoded inside classes.

```java
// GOOD: Dependency injected via constructor
public class SpellChecker {
    private final Lexicon dictionary;

    public SpellChecker(Lexicon dictionary) {
        this.dictionary = Objects.requireNonNull(dictionary);
    }
    public boolean isValid(String word) { return dictionary.contains(word); }
}

// BAD: Hardcoded dependency
public class SpellCheckerBad {
    private static final Lexicon dictionary = new EnglishLexicon();
}
```

## 5. Avoid Creating Unnecessary Objects

- **MUST** reuse expensive, immutable objects (e.g., `DateTimeFormatter`, regex `Pattern`) as `static final` constants.
- **DO** use primitive types instead of boxed primitives inside loops to avoid autoboxing overhead.

```java
// GOOD: Reusing DateTimeFormatter constant
public class DateUtils {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public String formatDate(LocalDate date) {
        return FORMATTER.format(date);
    }
}
```

## 6. Check Parameters for Validity

- **MUST** validate method arguments immediately at entry using `Objects.requireNonNull()` or explicit parameter checks (fail-fast principle).

```java
// GOOD: Fail-fast parameter validation
public BigInteger mod(BigInteger m) {
    if (m.signum() <= 0) {
        throw new ArithmeticException("Modulus <= 0: " + m);
    }
    return this.remainder(m);
}

public static int indexOf(String haystack, String needle) {
    Objects.requireNonNull(haystack, "haystack must not be null");
    Objects.requireNonNull(needle, "needle must not be null");
    return haystack.indexOf(needle);
}
```

## 7. Make Defensive Copies When Needed

- **MUST** create defensive copies of mutable objects (such as `Date` or `byte[]`) received as parameters or returned from accessor methods.

```java
// GOOD: Defensive copy in constructor and getter
public final class Period {
    private final Date start;
    private final Date end;

    public Period(Date start, Date end) {
        this.start = new Date(start.getTime()); // Defensive copy in
        this.end = new Date(end.getTime());     // Defensive copy in
        if (this.start.after(this.end)) throw new IllegalArgumentException("start after end");
    }

    public Date start() { return new Date(start.getTime()); } // Defensive copy out
    public Date end() { return new Date(end.getTime()); }     // Defensive copy out
}
```

## 8. Design Method Signatures Carefully

- **MUST** choose clear, self-documenting method names and keep parameter counts low (3 or fewer). Group related parameters into DTOs/records if necessary.

```java
// GOOD: Parameter object grouping related values
public record ProfileUpdate(String firstName, String lastName, Address address) {}

public class UserService {
    public void updateUserProfile(User user, ProfileUpdate update) { /* ... */ }
}
```

## 9. Return Empty Collections or Arrays, Not Nulls

- **MUST NEVER** return `null` for methods returning arrays or collections; return `List.of()`, `Collections.emptyList()`, or empty array `new String[0]`.

```java
// GOOD: Return empty collection
public List<Item> getItems() {
    return items.isEmpty() ? Collections.emptyList() : new ArrayList<>(items);
}

// BAD: Returning null forces caller to write null checks
public List<Item> getItemsBad() {
    return items.isEmpty() ? null : items;
}
```

## 10. Return Optionals Judiciously

- **MUST** use `Optional<T>` as a return type for methods that might legitimately fail to return a value.
- **DO NOT** use `Optional` for fields, method parameters, or collection returns.

```java
// GOOD: Optional return type
public Optional<User> findByUsername(String username) {
    return Optional.ofNullable(users.get(username));
}

// BAD: Optional field or parameter
class UserBad {
    private Optional<String> middleName; // BAD: Use nullable field String instead
}
```

## 11. Exception Handling Rules

- **MUST** use exceptions ONLY for exceptional conditions, never for normal control flow loops.
- **MUST** use checked exceptions for recoverable conditions and runtime exceptions (`IllegalArgumentException`, `IllegalStateException`) for programming errors.
- **MUST** use standard Java exceptions (`NullPointerException`, `IllegalArgumentException`, `IndexOutOfBoundsException`) whenever appropriate instead of custom exceptions.
- **MUST** include failure-capture context information (IDs, key inputs, current state) in exception detail messages.
- **MUST NEVER** silently catch and ignore exceptions (`catch (Exception e) {}`). Always log or wrap and rethrow.

```java
// GOOD: Descriptive exception detail message
public void withdraw(String accountId, double amount, double currentBalance) {
    if (amount > currentBalance) {
        throw new InsufficientFundsException(
            String.format("Insufficient funds. Account: %s, Balance: %.2f, Requested: %.2f",
                accountId, currentBalance, amount));
    }
}
```
