# Project Overview

This project is the Tailormap API, it provides the webserver and backend API for the Tailormap frontend.
The Tailormap frontend (tailormap-viewer) is written in Angular and developed in a separate repository that can be found
at: https://github.com/Tailormap/tailormap-viewer.

# Introduction

This guide helps GitHub Copilot generate code that is easy to read, use, and maintain. By following these rules, the
code will be simple, functional, and easy to modify in the future, ensuring long-term quality and understanding across
the team.

# Why This Guide?

The goal of this guide is to ensure that generated code is both human-readable and optimized for machine performance. It
facilitates collaboration, makes coding simpler, and ensures that best practices are consistently followed.

# Development Environment

Tailormap API is written in **Java 21** using **GeoTools 34.x**, **Hibernate 6.6.x** and **Spring Boot 3.5.x** and
leverages **OpenApi 3.0.3**.
Dependencies for this project are managed using Maven in the file `pom.xml` in the root of the repository.
Code for this project is generated using Maven using the file `pom.xml` in the root of the repository.
Builds for this project are executed using Maven using the file `pom.xml` in the root of the repository.
REST controllers in the `org.tailormap.api.controller` package use the definitions in the
`src/main/resources/openapi/viewer-api.yaml` description.
Administrative REST controllers in the `org.tailormap.api.controller` package use the definitions in the
`src/main/resources/openapi/admin-schemas.yaml` description, any controllers in this package should preferably use
Swagger annotations such as `@Operation` and `@ApiResponse`.

## Code Standards
- **Java Version**: Use Java 21 features and syntax.
- **GeoTools**: Use GeoTools 34.x for geospatial data handling.
- **Hibernate**: Use Hibernate 6.6.x for ORM and database interactions.
- **Spring Boot**: Use Spring Boot 3.5.x for building RESTful APIs.
- **Spring Data JPA**: Use Spring Data JPA for database access and repository management.
- **Spring Security**: Use Spring Security for authentication and authorization.
- **Spring Boot Actuator**: Use Spring Boot Actuator for monitoring and management of the application.
- **JUnit Jupiter**: Use JUnit 5 (Jupiter) for unit testing.
- **OpenAPI**: Use OpenAPI 3.0.3 for API documentation and definitions.
- **Maven**: Use Maven for dependency management and project builds.

### Required Before Each Commit
- **Code Formatting**: Ensure code is formatted according to the project's style guide using `mvn spotless:apply`.
- **Code Linting**: Run `mvn install` to ensure code adheres to the project's coding standards.

# Best Practices

## General Guidelines

Do not suggest changes to the project structure, such as renaming files or directories, unless it is necessary for the
implementation of the feature or fix.
Do not suggest changes to the project's build system, such as switching to a different build tool.
Never suggest code using deprecated libraries or methods.
Use a 2-space indentation for Java code and try to follow the `palantir/palantir-java-format`.
Use meaningful names for variables, methods, and classes that accurately describe their purpose.
Use snake case method names in test classes, e.g., `should_return_true_when_input_is_valid()`.

## Modularity

- **Single Responsibility Principle**: Write classes and methods that have a single responsibility, unless these are
  defined as utility class.
- **Code Reusability**: Encourage code reuse by creating modular components that can be used in multiple places.
- **Dependency Injection**: Use dependency injection to manage dependencies and improve code maintainability.

## Documentation

- **Commenting**: Include comments in code suggestions to explain complex logic or decisions.
- **JavaDoc**: For Java code, use JavaDoc comments to document public classes and methods.

## Error Handling

- **Robustness**: Suggest error handling practices, such as using try-catch blocks and validating inputs.
- **Logging**: Recommend logging important events and errors for easier debugging.
- **Error Messages**: Provide clear and informative error messages to help users understand what went wrong.

## Testing

- **Unit Tests**: Encourage the inclusion of unit tests for all new features and functions.
- **Integration Tests**: Suggest integration tests to ensure that components work together as expected.
- **Test-Driven Development**: Promote writing tests before implementing features to ensure requirements are met.

### Running tests
- Use `mvn test` to run all unit tests.
- Use `mvn -B -fae -e -DskipQA=true -Pqa-skip -Dspotless.apply.skip -Ddocker.skip=true -Ppostgresql verify -Dspring-boot.run.profiles=dev,populate-testdata,postgresql -Dspring-boot.run.arguments=--spatial.dbs.connect=true` to run all integration tests`

## Performance Considerations

- **Efficiency**: Suggest efficient algorithms and data structures to optimize performance.
- **Resource Management**: Close resources properly to avoid memory leaks and improve performance, this can be achieved
  using `try-with-resources` constructs.
- **Scalability**: Consider scalability when designing features to ensure the system can handle increased load.
- **Caching**: Recommend caching strategies to reduce latency and improve performance.

## Security Best Practices

- **Input Validation**: Always validate user inputs to prevent security vulnerabilities.
- **Secure Coding**: Follow secure coding practices to protect against common threats like SQL injection and cross-site
  scripting (XSS).

# Code Review Guidelines

- **Naming Conventions**: Follow naming conventions for classes, methods, variables, and packages.
- **Code Readability**: Write code that is easy to read and understand, using meaningful variable names and comments.
- **Code Consistency**: Ensure that code is consistent across the project, following the same style and conventions.
- **Code Duplication**: Avoid code duplication by reusing existing components or creating utility classes.
- **Code Complexity**: Keep code complexity low by breaking down complex logic into smaller, more manageable parts.
- **Code Performance**: Optimize code for performance by using efficient algorithms and data structures.
- **Code Security**: Follow secure coding practices to prevent common security vulnerabilities.
- **Code Testing**: Include unit tests and integration tests for all new features and functions.
- **Code Documentation**: Document code using comments and JavaDoc to explain complex logic and decisions.
 
