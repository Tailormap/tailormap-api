/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.compile;

import com.github.javaparser.*;
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
import java.nio.file.*;
import java.nio.file.Paths;

/**
 * Injects JsonSetter annotation with "Nulls.SKIP" to fields that don't have a null initializer. This is needed because
 * Jackson 3 changed behavior by setting missing fields to null instead of leaving it at the initial value, this causes
 * issues with code that did not expect a null value. Changing the global setting is not desirable because this would
 * affect other parts of the application, so this is a one-off script to fix the issue in generated sources for
 * persisted JSON entities.
 */
public class JacksonNullSkipInjector {

  public static void main(String[] args) throws IOException {
    Path sourceDir = Paths.get(args[0]);

    try (var paths = Files.walk(sourceDir)) {
      paths.filter(p -> p.toString().endsWith(".java")).forEach(JacksonNullSkipInjector::processFile);
    }
  }

  private static void processFile(Path filePath) {
    try {
      System.out.println("Processing " + filePath);
      CompilationUnit cu = StaticJavaParser.parse(filePath);

      cu.findAll(FieldDeclaration.class).forEach(field -> {
        if (!field.isStatic() && hasInitializer(field)) {
          String fieldName = field.getVariable(0).getNameAsString();
          System.out.println("Adding JsonSetter annotation to " + fieldName);
          //          NormalAnnotationExpr a = new NormalAnnotationExpr(
          //              new Name("JsonSetter"),
          //              NodeList.nodeList(
          //                  new MemberValuePair("nulls", new FieldAccessExpr(new NameExpr("Nulls"),
          // "SKIP")),
          //                  new MemberValuePair("contentNulls", new FieldAccessExpr(new NameExpr("Nulls"),
          // "SKIP")),
          //                  new MemberValuePair("value", new StringLiteralExpr(fieldName))));
          //          field.addAnnotation(a);

          // Modify @JsonProperty on setter method
          String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
          cu.findAll(MethodDeclaration.class).stream()
              .filter(method -> method.getNameAsString().equals(setterName))
              .forEach(method -> processSetterMethod(method, fieldName));
        }
      });
      cu.addImport("com.fasterxml.jackson.annotation.JsonSetter");
      cu.addImport("com.fasterxml.jackson.annotation.Nulls");
      cu.addImport("com.fasterxml.jackson.annotation.JsonProperty");

      Files.writeString(filePath, cu.toString());

    } catch (Exception e) {
      throw new RuntimeException("Failed processing " + filePath, e);
    }
  }

  private static boolean hasInitializer(FieldDeclaration field) {
    return field.getVariables().stream()
        .anyMatch(variable -> variable.getInitializer().isPresent()
            && !variable.getInitializer().get().toString().equals("null"));
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
