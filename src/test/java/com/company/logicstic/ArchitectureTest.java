package com.company.logicstic;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;

/**
 * Executable form of the docs/docs/development/engineering-conventions.md §2 dependency rules.
 *
 * <p>Without this class every rule in §2 is review-only — which is how the cross-feature repository
 * dependencies and the package cycles got in. Checkstyle cannot replace it: it sees one file at a
 * time and can neither resolve a type to its owning feature nor detect a cycle.
 *
 * <p>MapStruct writes {@code *MapperImpl} classes into the same packages as the interfaces they
 * implement. They are excluded below because they are generated, not reviewed, source.
 */
@AnalyzeClasses(
    packages = "com.company.logicstic",
    importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class ArchitectureTest {

  private static final String BASE = "com.company.logicstic.";
  private static final java.util.Set<String> NON_FEATURE_PACKAGES =
      java.util.Set.of("shared", "tenant", "security", "cache", "config", "devtools");

  // ---------------------------------------------------------------------------------------------
  // §2 — allowed direction: controller -> service -> repository -> entity
  // ---------------------------------------------------------------------------------------------

  @ArchTest
  static final ArchRule CONTROLLERS_MUST_NOT_USE_REPOSITORIES =
      noClasses()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .should()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("Repository")
          .because(
              "docs/docs/development/engineering-conventions.md §2: a controller talks to a service, never to the data layer");

  @ArchTest
  static final ArchRule SERVICES_MUST_NOT_DEPEND_ON_CONTROLLERS =
      noClasses()
          .that()
          .haveSimpleNameEndingWith("Service")
          .or()
          .haveSimpleNameEndingWith("ServiceImpl")
          .should()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("Controller")
          .because(
              "docs/docs/development/engineering-conventions.md §2: dependencies point inward, never back at the web layer");

  @ArchTest
  static final ArchRule REPOSITORIES_MUST_NOT_DEPEND_ON_SERVICES =
      noClasses()
          .that()
          .haveSimpleNameEndingWith("Repository")
          .should()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("Service")
          .because(
              "docs/docs/development/engineering-conventions.md §2: the repository is the innermost layer");

  /**
   * The rule that matters most: feature A must never reach into feature B's data layer. Cross-
   * feature reads go through B's public service interface, which owns B's "not found" error, its
   * tenant scoping and its fetch strategy.
   */
  @ArchTest
  static final ArchRule FEATURES_MUST_NOT_USE_ANOTHER_FEATURES_REPOSITORY =
      classes()
          .that(new DescribedPredicateOfGeneratedCode())
          .should(neverDependOnAnotherFeaturesRepository())
          .because(
              "docs/docs/development/engineering-conventions.md §2: cross-feature access goes through the owning feature's public"
                  + " service interface, not its repository");

  /** Cycles are checked across the <em>behavioural</em> service layers only. */
  @ArchTest
  static final ArchRule SERVICES_MUST_BE_FREE_OF_CYCLES =
      classes()
          .that()
          .haveSimpleNameEndingWith("Service")
          .or()
          .haveSimpleNameEndingWith("ServiceImpl")
          .should(neverHaveCyclicServiceDependencies())
          .because(
              "docs/docs/development/engineering-conventions.md §2: two features must never call into each other");

  // ---------------------------------------------------------------------------------------------
  // §6 / §7 — injection and transaction placement
  // ---------------------------------------------------------------------------------------------

  @ArchTest
  static final ArchRule NO_FIELD_INJECTION =
      GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION.because(
          "docs/docs/development/engineering-conventions.md §6: constructor injection only");

  @ArchTest
  static final ArchRule REPOSITORIES_MUST_NOT_DECLARE_TRANSACTIONS =
      noClasses()
          .that()
          .haveSimpleNameEndingWith("Repository")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("org.springframework.transaction.annotation.Transactional")
          .because(
              "docs/docs/development/engineering-conventions.md §7: the transaction boundary is the service implementation");

  // ---------------------------------------------------------------------------------------------
  // §13 — logging
  // ---------------------------------------------------------------------------------------------

  @ArchTest
  static final ArchRule NO_STANDARD_STREAMS =
      GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.because(
          "docs/docs/development/engineering-conventions.md §13: log through SLF4J");

  // ---------------------------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------------------------

  /**
   * Returns the feature name for a class inside {@code com.company.logicstic.<feature>}, else
   * {@code null}.
   */
  private static String featureOf(String packageName) {
    if (!packageName.startsWith(BASE)) {
      return null;
    }
    String rest = packageName.substring(BASE.length());
    int dot = rest.indexOf('.');
    String feature = dot < 0 ? rest : rest.substring(0, dot);
    if (NON_FEATURE_PACKAGES.contains(feature) || feature.isEmpty()) {
      return null;
    }
    return feature;
  }

  private static ArchCondition<JavaClass> neverHaveCyclicServiceDependencies() {
    return new ArchCondition<>("never have cyclic service dependencies between features") {
      private final java.util.Map<String, java.util.Set<String>> featureGraph =
          new java.util.concurrent.ConcurrentHashMap<>();

      @Override
      public void check(JavaClass origin, ConditionEvents events) {
        String ownFeature = featureOf(origin.getPackageName());
        if (ownFeature == null) {
          return;
        }
        origin.getDirectDependenciesFromSelf().stream()
            .map(dependency -> dependency.getTargetClass())
            .filter(
                target ->
                    target.getSimpleName().endsWith("Service")
                        || target.getSimpleName().endsWith("ServiceImpl"))
            .forEach(
                target -> {
                  String targetFeature = featureOf(target.getPackageName());
                  if (targetFeature != null && !targetFeature.equals(ownFeature)) {
                    featureGraph
                        .computeIfAbsent(
                            ownFeature,
                            k -> java.util.Collections.synchronizedSet(new java.util.HashSet<>()))
                        .add(targetFeature);
                  }
                });
      }

      @Override
      public void finish(ConditionEvents events) {
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Set<String> recStack = new java.util.HashSet<>();
        for (String node : featureGraph.keySet()) {
          if (detectCycle(node, visited, recStack, new java.util.ArrayList<>(), events)) {
            break;
          }
        }
      }

      private boolean detectCycle(
          String current,
          java.util.Set<String> visited,
          java.util.Set<String> recStack,
          java.util.List<String> path,
          ConditionEvents events) {
        if (recStack.contains(current)) {
          path.add(current);
          events.add(
              SimpleConditionEvent.violated(
                  current, "Cycle detected in service dependencies: " + String.join(" -> ", path)));
          return true;
        }
        if (visited.contains(current)) {
          return false;
        }
        visited.add(current);
        recStack.add(current);
        path.add(current);

        for (String neighbor : featureGraph.getOrDefault(current, java.util.Set.of())) {
          if (detectCycle(neighbor, visited, recStack, new java.util.ArrayList<>(path), events)) {
            return true;
          }
        }
        recStack.remove(current);
        return false;
      }
    };
  }

  private static ArchCondition<JavaClass> neverDependOnAnotherFeaturesRepository() {
    return new ArchCondition<>("never depend on another feature's repository") {
      @Override
      public void check(JavaClass origin, ConditionEvents events) {
        String ownFeature = featureOf(origin.getPackageName());
        if (ownFeature == null) {
          return;
        }
        origin.getDirectDependenciesFromSelf().stream()
            .map(dependency -> dependency.getTargetClass())
            .filter(target -> target.getSimpleName().endsWith("Repository"))
            .forEach(
                target -> {
                  String targetFeature = featureOf(target.getPackageName());
                  if (targetFeature != null && !targetFeature.equals(ownFeature)) {
                    events.add(
                        SimpleConditionEvent.violated(
                            origin,
                            String.format(
                                "%s (feature '%s') depends on %s (feature '%s')",
                                origin.getName(), ownFeature, target.getName(), targetFeature)));
                  }
                });
      }
    };
  }

  /** Excludes MapStruct's generated {@code *MapperImpl} classes from the feature rules. */
  private static final class DescribedPredicateOfGeneratedCode
      extends com.tngtech.archunit.base.DescribedPredicate<JavaClass> {

    private DescribedPredicateOfGeneratedCode() {
      super("are not generated");
    }

    @Override
    public boolean test(JavaClass javaClass) {
      return !javaClass.getSimpleName().endsWith("MapperImpl");
    }
  }
}
