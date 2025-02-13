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

Tailormap API is written in **Java 17** using **GeoTools 32.x**, **Hibernate 6.6.x** and **Spring Boot 3.4.x** and
leverages **OpenApi 3.0.3**.
Dependencies for this project are managed using Maven in the file `pom.xml` in the root of the repository.
Code for this project is generated using Maven using the file `pom.xml` in the root of the repository.
Builds for this project are executed using Maven using the file `pom.xml` in the root of the repository.
REST controllers in the `org.tailormap.api.controller` package use the definitions in the
`src/main/resources/openapi/viewer-api.yaml` description.
Administrative REST controllers in the `org.tailormap.api.controller` package use the definitions in the
`src/main/resources/openapi/admin-schemas.yaml` description, any controllers in this package should preferably use
Swagger annotations such as `@Operation` and `@ApiResponse`.

# Best Practices

## General Guidelines

Do not suggest changes to the project structure, such as renaming files or directories, unless it is necessary for the
implementation of the feature or fix.
Do not suggest changes to the project's build system, such as switching to a different build tool.
Never suggest code using deprecated libraries or methods.
Use a 2-space indentation for Java code and try to follow the `palantir/palantir-java-format`.

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
 
