package com.company.logicstic.modules.messaging.repository;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.employee.repository.EmployeeRepository;
import com.company.logicstic.modules.messaging.entity.Conversation;
import com.company.logicstic.modules.messaging.entity.Message;
import jakarta.persistence.Entity;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.service.ServiceRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

class MessagingRepositoryQueryTest {

  @Test
  void repositoryQueriesUseValidEntityPaths() throws Exception {
    ServiceRegistry registry =
        new StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
            .build();

    try {
      MetadataSources metadata = new MetadataSources(registry);
      scanModuleEntities().forEach(metadata::addAnnotatedClass);

      try (SessionFactory sessionFactory = metadata.buildMetadata().buildSessionFactory()) {
        var translator =
            sessionFactory
                .unwrap(SessionFactoryImplementor.class)
                .getQueryEngine()
                .getHqlTranslator();

        String conversations =
            queryValue(
                ConversationRepository.class, "findByParticipant", UUID.class, Pageable.class);
        String conversationForParticipant =
            queryValue(
                ConversationRepository.class, "findByIdAndParticipant", UUID.class, UUID.class);
        String unread = queryValue(MessageRepository.class, "countUnread", UUID.class);
        String unreadMessages =
            queryValue(MessageRepository.class, "findUnread", UUID.class, UUID.class);
        String drivers =
            queryValue(
                EmployeeRepository.class,
                "searchDrivers",
                String.class,
                String.class,
                Pageable.class);
        String driverById = queryValue(EmployeeRepository.class, "findDriverById", UUID.class);

        assertThatCode(() -> translator.translate(conversations, Conversation.class))
            .doesNotThrowAnyException();
        assertThatCode(() -> translator.translate(conversationForParticipant, Conversation.class))
            .doesNotThrowAnyException();
        assertThatCode(() -> translator.translate(unread, Long.class)).doesNotThrowAnyException();
        assertThatCode(() -> translator.translate(unreadMessages, Message.class))
            .doesNotThrowAnyException();
        assertThatCode(() -> translator.translate(drivers, Employee.class))
            .doesNotThrowAnyException();
        assertThatCode(() -> translator.translate(driverById, Employee.class))
            .doesNotThrowAnyException();
      }
    } finally {
      StandardServiceRegistryBuilder.destroy(registry);
    }
  }

  private Set<Class<?>> scanModuleEntities() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
    return scanner.findCandidateComponents("com.company.logicstic.modules").stream()
        .map(definition -> loadClass(definition.getBeanClassName()))
        .collect(Collectors.toSet());
  }

  private Class<?> loadClass(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException exception) {
      throw new AssertionError("Cannot load entity " + className, exception);
    }
  }

  private String queryValue(Class<?> repository, String methodName, Class<?>... parameterTypes)
      throws NoSuchMethodException {
    Method method = repository.getMethod(methodName, parameterTypes);
    return method.getAnnotation(Query.class).value();
  }
}
