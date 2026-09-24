---
description: "Refactoring Java OOP code smells: God Class, Feature Envy, Inappropriate Intimacy, Refused Bequest, Shotgun Surgery, Data Clumps"
paths:
  - "**/*.java"
---

# Java OOP Code Smells & Refactoring

## 1. Large Class / God Class

- **MUST** break down classes that perform multiple unrelated responsibilities (e.g. parsing, DB access, UI rendering, email notifications) into small, cohesive component classes.
- **REFACTOR BY**: Extract Class, Extract Interface.

```java
// REFACTORED: Separated concerns
class OrderProcessor {
    private final OrderRepository repository;
    private final PaymentGateway paymentGateway;
    private final EmailService emailService;

    public OrderProcessor(OrderRepository repo, PaymentGateway payment, EmailService email) {
        this.repository = repo;
        this.paymentGateway = payment;
        this.emailService = email;
    }
    public void process(Order order) { /* ... */ }
}
```

## 2. Feature Envy

- **MUST** move methods that access data from another class more than their own class into the class containing that data.
- **REFACTOR BY**: Move Method.

```java
// GOOD: Method placed inside Customer where the data resides
class Customer {
    private String name;
    private Address address;

    public String getShippingLabel() {
        return name + "\n" + address.getStreet() + "\n" + address.getCity() + ", " + address.getZipCode();
    }
}

// BAD: Order envious of Customer's address fields
class OrderBad {
    private Customer customer;
    public String getCustomerShippingLabel() {
        Address addr = customer.getAddress();
        return customer.getName() + "\n" + addr.getStreet() + "\n" + addr.getCity();
    }
}
```

## 3. Inappropriate Intimacy

- **MUST** eliminate direct access to another class's private or package-private state.
- **REFACTOR BY**: Move Field, Encapsulate Field, or delegate via public interface methods.

```java
// GOOD: Decoupled via method call
class ServiceA {
    private int counter = 0;
    public void resetCounter() { this.counter = 0; }
}

class ServiceB {
    public void reset(ServiceA serviceA) {
        serviceA.resetCounter();
    }
}

// BAD: Direct mutation of another class's field
class ServiceBBad {
    public void reset(ServiceABad serviceA) {
        serviceA.internalCounter = 0; // Direct field access
    }
}
```

## 4. Refused Bequest

- **MUST** avoid inheriting from classes where superclass methods are overridden to throw `UnsupportedOperationException` or render no-ops.
- **REFACTOR BY**: Replace Inheritance with Composition, or extract a shared interface.

```java
// GOOD: Replacing improper inheritance with composition
class CarDoor {
    private final Window window = new Window();
    public void openDoor() { /* ... */ }
    public void rollDownWindow() { window.open(); }
}
```

## 5. Shotgun Surgery

- **MUST** consolidate single concepts spread thin across multiple classes into a single authoritative class so changes require editing only one place.
- **REFACTOR BY**: Move Method, Move Field, Inline Class.

```java
// GOOD: Centralized discount rule engine
class DiscountPolicy {
    public double applyDiscount(Order order) { /* Single source of truth for discounts */ return 0.0; }
}
```

## 6. Data Clumps

- **MUST** group primitive fields that always travel together (e.g. `startDate`, `endDate`, or `street`, `city`, `zipCode`) into a dedicated Java `record` or class.
- **REFACTOR BY**: Extract Class, Introduce Parameter Object.

```java
// GOOD: Data clump extracted into DateRange record
public record DateRange(LocalDate start, LocalDate end) {
    public DateRange {
        if (start.isAfter(end)) throw new IllegalArgumentException("Start must be before end");
    }
}

class EventScheduler {
    public void scheduleEvent(String name, DateRange range) {
        System.out.println("Scheduling " + name + " for " + range);
    }
}

// BAD: Data clump of multiple primitive parameters
class EventSchedulerBad {
    public void scheduleEvent(String name, int startDay, int startMonth, int startYear, int endDay, int endMonth, int endYear) {}
}
```
