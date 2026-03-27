/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.tools;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Injects JsonSetter annotation with "Nulls.SKIP" to fields that don't have a null initializer. This is needed because
 * Jackson 3 changed behavior by setting missing fields to null instead of leaving it at the initial value, this causes
 * issues with code that did not expect a null value. Changing the global setting is not desirable because this would
 * affect other parts of the application, so this is a one-off script to fix the issue in generated sources for
 * persisted JSON entities.
 */
public class JacksonNullSkipInjector {

  static void main(String[] args) throws IOException {
    Path sourceDir = Path.of(args[0]);
    try (Stream<Path> paths = Files.walk(sourceDir)) {
      paths.filter(p -> p.toString().endsWith(".java")).forEach(JacksonNullSkipInjector::processFile);
    }
  }

  @SuppressWarnings("PMD.SystemPrintln")
  private static void processFile(Path filePath) {
    try {
      CompilationUnit cu = StaticJavaParser.parse(filePath);
      boolean modified = false;

      List<FieldDeclaration> fields = cu.findAll(FieldDeclaration.class);
      for (FieldDeclaration field : fields) {
        if (!field.isStatic() && hasInitializer(field)) {
          String fieldName = field.getVariable(0).getNameAsString();
          String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
          for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            if (method.getNameAsString().equals(setterName) && !hasJsonSetter(method)) {
              processSetterMethod(method, fieldName);
              modified = true;
            }
          }
        }
      }

      if (modified) {
        System.out.println("Adding @JsonSetter(Nulls.SKIP) annotations to " + filePath);
        cu.addImport("com.fasterxml.jackson.annotation.JsonSetter");
        cu.addImport("com.fasterxml.jackson.annotation.Nulls");
        Files.writeString(filePath, cu.toString());
      }

    } catch (Exception e) {
      throw new RuntimeException("Failed processing " + filePath, e);
    }
  }

  private static boolean hasInitializer(FieldDeclaration field) {
    return field.getVariables().stream()
        .anyMatch(variable -> variable.getInitializer().isPresent()
            && !variable.getInitializer().orElseThrow().toString().equals("null"));
  }

  private static boolean hasJsonSetter(MethodDeclaration method) {
    return method.getAnnotations().stream()
        .anyMatch(a -> a.getNameAsString().equals("JsonSetter"));
  }

  private static void processSetterMethod(MethodDeclaration method, String property) {
    NormalAnnotationExpr a = new NormalAnnotationExpr(
        new Name("JsonSetter"),
        NodeList.nodeList(
            new MemberValuePair("nulls", new FieldAccessExpr(new NameExpr("Nulls"), "SKIP")),
            new MemberValuePair("contentNulls", new FieldAccessExpr(new NameExpr("Nulls"), "SKIP")),
            new MemberValuePair("value", new StringLiteralExpr(property))));
    method.addAnnotation(a);
  }
}
