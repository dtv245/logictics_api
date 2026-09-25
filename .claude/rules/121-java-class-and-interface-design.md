---
description: "Java Class and Interface Design: composition over inheritance, immutability, access control, enums, annotations"
paths:
  - "**/*.java"
---

# Java Class & Interface Design Best Practices

## 1. Favor Composition Over Inheritance

- **MUST** use composition (has-a) instead of inheritance (is-a) when reusing implementation logic from existing classes.
- **DO** wrap component classes and delegate calls to avoid fragile subclass dependencies.

```java
// GOOD: Composition wrapper (Decorator pattern)
public class InstrumentedSet<E> {
    private final Set<E> set;
    private int addCount = 0;

    public InstrumentedSet(Set<E> set) { this.set = set; }

    public boolean add(E e) {
        addCount++;
        return set.add(e);
    }

    public boolean addAll(Collection<? extends E> c) {
        addCount += c.size();
        return set.addAll(c);
    }

    public int getAddCount() { return addCount; }
}

// BAD: Subclassing HashSet breaks addAll count due to internal super class self-use
public class InstrumentedHashSetBad<E> extends HashSet<E> {
    private int addCount = 0;
    @Override public boolean add(E e) { addCount++; return super.add(e); }
    @Override public boolean addAll(Collection<? extends E> c) {
        addCount += c.size();
        return super.addAll(c); // Internally calls add(), double-counting!
    }
}
```

## 2. Design for Immutability

- **MUST** make classes immutable wherever possible by making classes `final`, fields `private final`, and eliminating mutator methods.
- **DO** return new instances from operations rather than modifying existing state.

```java
// GOOD: Immutable class design
public final class Complex {
    private final double real;
    private final double imaginary;

    public Complex(double real, double imaginary) {
        this.real = real;
        this.imaginary = imaginary;
    }

    public Complex plus(Complex c) {
        return new Complex(real + c.real, imaginary + c.imaginary);
    }

    public double realPart() { return real; }
    public double imaginaryPart() { return imaginary; }
}

// BAD: Mutable state requires synchronization and introduces race conditions
public class ComplexBad {
    private double real;
    private double imaginary;
    public void plus(ComplexBad c) { this.real += c.real; this.imaginary += c.imaginary; }
}
```

## 3. Minimize the Accessibility of Classes and Members

- **MUST** make each class or member as inaccessible as possible using proper visibility modifiers (`private`, package-private, `protected`, `public`).
- **DO NOT** expose internal helper methods or non-final constants in the public API.

```java
// GOOD: Strictly scoped visibility
public class BankAccount {
    private final String accountNumber;
    private double balance;
    static final double MINIMUM_BALANCE = 0.0; // Package-private for package internal access

    public BankAccount(String accountNumber, double balance) {
        this.accountNumber = accountNumber;
        this.balance = balance;
    }

    private void validate(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
    }
}
```

## 4. In Public Classes, Use Accessor Methods, Not Public Fields

- **MUST** use getters and setters instead of public mutable fields in public classes to preserve flexibility for future validation or logging.

```java
// GOOD: Accessor methods encapsulate state
public class Point {
    private double x;
    private double y;

    public Point(double x, double y) { this.x = x; this.y = y; }
    public double getX() { return x; }
    public void setX(double x) {
        if (Double.isNaN(x)) throw new IllegalArgumentException("x cannot be NaN");
        this.x = x;
    }
}

// BAD: Public mutable fields prevent adding validation later without breaking API
public class PointBad {
    public double x;
    public double y;
}
```

## 5. Design for Inheritance or Else Prohibit It

- **MUST** mark classes `final` or make constructors `private` if they are not specifically designed and documented for subclassing.
- **DO** document all self-use of overridable methods if subclassing is allowed.

```java
// GOOD: Prohibiting instantiation and inheritance on utility class
public final class StringUtils {
    private StringUtils() { throw new AssertionError("No instances allowed"); }
    public static boolean isEmpty(String s) { return s == null || s.isEmpty(); }
}
```

## 6. Use Enums Instead of Int Constants

- **MUST** use Java `enum` types instead of `public static final int` constants to gain compile-time type safety and namespace protection.

```java
// GOOD: Type-safe enum with properties and behavior
public enum Planet {
    EARTH(5.975e+24, 6.378e6),
    MARS(6.419e+23, 3.393e6);

    private final double mass;
    private final double radius;

    Planet(double mass, double radius) {
        this.mass = mass;
        this.radius = radius;
    }

    public double surfaceGravity() {
        return 6.67300E-11 * mass / (radius * radius);
    }
}

// BAD: Int constant pattern lacking type safety
public class PlanetConstantsBad {
    public static final int PLANET_EARTH = 0;
    public static final int PLANET_MARS = 1;
}
```

## 7. Use Instance Fields Instead of Ordinals

- **MUST** store values in dedicated instance fields rather than deriving business logic from `ordinal()`.

```java
// GOOD: Explicit instance field
public enum Ensemble {
    SOLO(1), DUET(2), TRIO(3), QUARTET(4);

    private final int numberOfMusicians;
    Ensemble(int size) { this.numberOfMusicians = size; }
    public int getNumberOfMusicians() { return numberOfMusicians; }
}

// BAD: Fragile dependence on enum ordering
public enum EnsembleBad {
    SOLO, DUET, TRIO, QUARTET;
    public int numberOfMusicians() { return ordinal() + 1; }
}
```

## 8. Use EnumSet Instead of Bit Fields

- **MUST** use `EnumSet` for sets of enum flags instead of bitwise integer operations (`1 << 0`).

```java
// GOOD: Type-safe EnumSet
public class TextStyle {
    public enum Style { BOLD, ITALIC, UNDERLINE }
    public void applyStyles(Set<Style> styles) { /* Apply styles */ }
}
// Usage: textStyle.applyStyles(EnumSet.of(TextStyle.Style.BOLD, TextStyle.Style.ITALIC));

// BAD: Bitmask integer flags
public class TextStyleBad {
    public static final int STYLE_BOLD = 1 << 0;
    public static final int STYLE_ITALIC = 1 << 1;
    public void applyStyles(int styleFlags) { /* ... */ }
}
```

## 9. Use EnumMap Instead of Ordinal Indexing

- **MUST** use `EnumMap` when mapping enum keys to values instead of indexing into arrays with `enum.ordinal()`.

```java
// GOOD: EnumMap keying
Map<Phase, Map<Phase, Transition>> map = new EnumMap<>(Phase.class);

// BAD: Array indexed by ordinal
Transition[][] transitions = new Transition[Phase.values().length][Phase.values().length];
```

## 10. Consistently Use the @Override Annotation

- **MUST** place `@Override` on every method that overrides an abstract method, interface method, or superclass method to catch signature typos at compile time.

```java
// GOOD: Explicit @Override catches errors immediately
public class Bigram {
    private final char first, second;
    public Bigram(char first, char second) { this.first = first; this.second = second; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Bigram)) return false;
        Bigram b = (Bigram) o;
        return b.first == first && b.second == second;
    }

    @Override
    public int hashCode() { return 31 * first + second; }
}
```
