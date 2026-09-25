---
description: "Java SOLID principles, DRY, YAGNI, and OOP core pillars (Encapsulation, Inheritance, Polymorphism)"
paths:
  - "**/*.java"
---

# Java SOLID Principles & Core OOP Pillars

## 1. Single Responsibility Principle (SRP)

- **MUST** give every class a single, well-defined responsibility and one primary reason to change.
- **DO NOT** mix persistence, formatting, network logic, or user notifications inside domain entity classes.

```java
// GOOD: Separated responsibilities
class UserData {
    private final String name;
    private final String email;
    public UserData(String name, String email) { this.name = name; this.email = email; }
    public String getName() { return name; }
    public String getEmail() { return email; }
}

class UserPersistence {
    public void saveUser(UserData user) { /* Database saving logic */ }
}

class UserEmailer {
    public void sendWelcomeEmail(UserData user) { /* Email sending logic */ }
}

// BAD: Single class handling state, persistence, and email
class User {
    private String name;
    private String email;
    public void saveToDatabase() { /* DB logic */ }
    public void sendWelcomeEmail() { /* Email logic */ }
}
```

## 2. Open/Closed Principle (OCP)

- **MUST** design software entities to be open for extension but closed for modification.
- **DO** use interfaces, abstract classes, and polymorphism to allow adding new behavior without altering existing, tested code.

```java
// GOOD: Open for extension via Shape interface
interface Shape {
    double calculateArea();
}

class Rectangle implements Shape {
    private final double width, height;
    public Rectangle(double w, double h) { width = w; height = h; }
    @Override public double calculateArea() { return width * height; }
}

class Circle implements Shape {
    private final double radius;
    public Circle(double r) { radius = r; }
    @Override public double calculateArea() { return Math.PI * radius * radius; }
}

class AreaCalculator {
    public double getTotalArea(List<Shape> shapes) {
        return shapes.stream().mapToDouble(Shape::calculateArea).sum();
    }
}

// BAD: Requires modification every time a new shape type is added
class AreaCalculatorBad {
    public double calculateRectangleArea(Rectangle rect) { return rect.width * rect.height; }
    public double calculateCircleArea(Circle circ) { return Math.PI * circ.radius * circ.radius; }
}
```

## 3. Liskov Substitution Principle (LSP)

- **MUST** ensure subclasses are completely substitutable for their supertypes without altering program correctness or throwing unexpected exceptions.
- **DO NOT** override parent methods to throw `UnsupportedOperationException` or render them no-ops.

```java
// GOOD: Proper hierarchy reflecting capabilities
interface Bird {
    void move();
}

class FlyingBird implements Bird {
    public void fly() { System.out.println("Flying"); }
    @Override public void move() { fly(); }
}

class Ostrich implements Bird {
    public void runFast() { System.out.println("Running"); }
    @Override public void move() { runFast(); }
}

// BAD: Subclass breaks superclass contract
class BirdBad {
    public void fly() { System.out.println("Flying"); }
}

class PenguinBad extends BirdBad {
    @Override
    public void fly() {
        throw new UnsupportedOperationException("Penguins cannot fly");
    }
}
```

## 4. Interface Segregation Principle (ISP)

- **MUST** keep interfaces small, focused, and role-specific.
- **DO NOT** force clients to depend on interface methods they do not consume.

```java
// GOOD: Segregated role interfaces
interface Worker { void work(); }
interface Eater { void eat(); }

class HumanWorker implements Worker, Eater {
    @Override public void work() { /* ... */ }
    @Override public void eat() { /* ... */ }
}

class RobotWorker implements Worker {
    @Override public void work() { /* ... */ }
}

// BAD: Fat interface forcing unnecessary implementations
interface IWorkerAndEater {
    void work();
    void eat();
}
class RobotBad implements IWorkerAndEater {
    @Override public void work() { /* ... */ }
    @Override public void eat() { throw new UnsupportedOperationException(); }
}
```

## 5. Dependency Inversion Principle (DIP)

- **MUST** make high-level modules depend on abstractions (interfaces), not concrete implementations.
- **DO** inject dependencies via constructors instead of hardwiring `new` instances inside dependent classes.

```java
// GOOD: High-level service depends on MessageSender abstraction
interface MessageSender {
    void sendMessage(String message);
}

class EmailSender implements MessageSender {
    @Override public void sendMessage(String message) { /* Send email */ }
}

class NotificationService {
    private final MessageSender sender;
    public NotificationService(MessageSender sender) { this.sender = sender; }
    public void notify(String msg) { sender.sendMessage(msg); }
}

// BAD: High-level service directly instantiates concrete low-level class
class NotificationServiceBad {
    private EmailSender emailer = new EmailSender();
    public void notify(String msg) { emailer.sendMessage(msg); }
}
```

## 6. DRY (Don't Repeat Yourself)

- **MUST** consolidate duplicated code into reusable helper methods, utilities, or shared abstractions.
- **DO NOT** copy-paste validation, calculation, or formatting logic across multiple methods or classes.

```java
// GOOD: Centralized validation logic
class ValidationUtils {
    public static void validatePositive(double val, String fieldName) {
        if (val <= 0) throw new IllegalArgumentException(fieldName + " must be positive.");
    }
}

class Calculator {
    public double calculateArea(double w, double h) {
        ValidationUtils.validatePositive(w, "Width");
        ValidationUtils.validatePositive(h, "Height");
        return w * h;
    }
}
```

## 7. YAGNI (You Ain't Gonna Need It)

- **MUST** implement only the requirements needed today.
- **DO NOT** add speculative features, unused abstraction layers, or extra export formats until explicitly required.

```java
// GOOD: Simple implementation meeting current requirements
class SimpleReportGenerator {
    public String generateReport(List<String> data) {
        return String.join(", ", data);
    }
}

// BAD: Speculative over-engineering with unused features
class OverkillReportGenerator {
    public String generateHtml(List<String> d) { return ""; }
    public byte[] generatePdf(List<String> d) { return new byte[0]; } // Not required!
    public byte[] generateExcel(List<String> d) { return new byte[0]; } // Not required!
}
```

## 8. Encapsulation

- **MUST** protect class internal state by declaring fields `private` and controlling access via well-defined methods.
- **DO** validate incoming parameters inside mutating methods to enforce object invariants.

```java
// GOOD: Encapsulated balance with invariants enforced
class BankAccount {
    private double balance;

    public BankAccount(double initialBalance) {
        if (initialBalance < 0) throw new IllegalArgumentException("Balance cannot be negative");
        this.balance = initialBalance;
    }

    public void deposit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        this.balance += amount;
    }

    public double getBalance() { return balance; }
}

// BAD: Public mutable fields breaking encapsulation
class UnsafeBankAccount {
    public double balance; // Can be modified directly to invalid state
}
```

## 9. Inheritance Guidelines

- **MUST** use inheritance only for genuine "is-a" relationships.
- **DO** prefer composition when sharing functionality across unrelated types to avoid fragile base class issues.

```java
// GOOD: Abstract base class representing true shared domain identity
abstract class Animal {
    private final String name;
    public Animal(String name) { this.name = name; }
    public String getName() { return name; }
    public abstract void makeSound();
}

class Dog extends Animal {
    public Dog(String name) { super(name); }
    @Override public void makeSound() { System.out.println(getName() + ": Woof!"); }
}
```

## 10. Polymorphism

- **MUST** leverage polymorphism to decouple callers from concrete target types.
- **DO NOT** use long `if-else` or `switch` statements with `instanceof` casts when method overriding can handle execution dynamically.

```java
// GOOD: Polymorphic execution
interface Drawable {
    void draw();
}

class CircleDrawer implements Drawable { @Override public void draw() { /* Draw circle */ } }
class SquareDrawer implements Drawable { @Override public void draw() { /* Draw square */ } }

class Canvas {
    public void render(List<Drawable> items) {
        items.forEach(Drawable::draw);
    }
}

// BAD: Explicit type checking instead of polymorphism
class CanvasBad {
    public void render(Object shape) {
        if (shape instanceof CircleDrawer) ((CircleDrawer) shape).draw();
        else if (shape instanceof SquareDrawer) ((SquareDrawer) shape).draw();
    }
}
```
