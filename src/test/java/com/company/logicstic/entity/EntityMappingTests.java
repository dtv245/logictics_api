package com.company.logicstic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.service.ServiceRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityMappingTests {

    private static final String ENTITY_PACKAGE = "com.company.logicstic.entity";

    @Test
    void shouldDiscoverAndBuildMetadataForAllEntities() {
        Set<Class<?>> entities = scanEntities();
        assertEquals(50, entities.size(), "sql.md phải tạo đúng 50 entity nghiệp vụ");

        entities.forEach(this::verifyEntityFields);

        ServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .build();
        try {
            MetadataSources metadataSources = new MetadataSources(registry);
            entities.forEach(metadataSources::addAnnotatedClass);
            assertNotNull(metadataSources.buildMetadata());
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private Set<Class<?>> scanEntities() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        return scanner.findCandidateComponents(ENTITY_PACKAGE).stream()
                .map(definition -> loadClass(definition.getBeanClassName()))
                .collect(Collectors.toSet());
    }

    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Không tải được entity " + className, exception);
        }
    }

    private void verifyEntityFields(Class<?> entityClass) {
        Field idField = Set.of(entityClass.getDeclaredFields()).stream()
                .filter(field -> field.isAnnotationPresent(Id.class))
                .findFirst()
                .orElseThrow(() -> new AssertionError(entityClass.getSimpleName() + " thiếu @Id"));

        assertNotNull(idField.getAnnotation(GeneratedValue.class),
                entityClass.getSimpleName() + " UUID ID thiếu @GeneratedValue");
        assertNotNull(idField.getAnnotation(UuidGenerator.class),
                entityClass.getSimpleName() + " UUID ID thiếu @UuidGenerator");

        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            boolean mapped = field.isAnnotationPresent(Column.class)
                    || field.isAnnotationPresent(JoinColumn.class)
                    || field.isAnnotationPresent(OneToMany.class);
            assertTrue(mapped, () -> entityClass.getSimpleName() + "." + field.getName()
                    + " thiếu annotation mapping JPA");
        }
    }
}
