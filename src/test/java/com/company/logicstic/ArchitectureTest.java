package com.company.logicstic;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

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

  private static final String MODULES = "com.company.logicstic.modules.";

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

  /**
   * Cycles are checked across the <em>behavioural</em> packages only.
   *
   * <p>§2 makes an explicit carve-out: "Cross-feature JPA associations ({@code Load.customer},
   * {@code Trip.truck}) are allowed — the schema is one relational database and the associations
   * mirror it." A bidirectional association such as {@code Document.employee} / {@code
   * Employee.documents} is therefore permitted, and it necessarily forms an entity-package cycle.
   * Running {@code beFreeOfCycles()} over {@code modules.(*)..} reports those 650 allowed entity
   * edges as failures, so the rule would have to be deleted to go green — which §15.6 calls worse
   * than no rule.
   *
   * <p>Scoping to {@code service} keeps the rule enforcing what §2 actually forbids: a cycle in
   * behaviour, where two features call into each other. Reaching into another feature's data layer
   * is caught separately and more precisely by {@link
   * #FEATURES_MUST_NOT_USE_ANOTHER_FEATURES_REPOSITORY}.
   */
  @ArchTest
  static final ArchRule SERVICES_MUST_BE_FREE_OF_CYCLES =
      slices()
          .matching("com.company.logicstic.modules.(*).service..")
          .should()
          .beFreeOfCycles()
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

  /** Returns the feature name for a class inside {@code modules.<feature>}, else {@code null}. */
  private static String featureOf(String packageName) {
    if (!packageName.startsWith(MODULES)) {
      return null;
    }
    String rest = packageName.substring(MODULES.length());
    int dot = rest.indexOf('.');
    return dot < 0 ? rest : rest.substring(0, dot);
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
            .filter(target -> target.getPackageName().endsWith(".repository"))
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
