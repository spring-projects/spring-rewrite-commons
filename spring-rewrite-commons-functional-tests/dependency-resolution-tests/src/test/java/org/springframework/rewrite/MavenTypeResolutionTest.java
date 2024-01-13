/*
 * Copyright 2021 - 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.rewrite;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.ExecutionContext;
import org.openrewrite.RecipeRun;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.springframework.rewrite.parsers.RewriteExecutionContext;
import org.springframework.rewrite.parsers.RewriteProjectParsingResult;
import org.springframework.rewrite.parsers.SpringRewriteProperties;
import org.springframework.rewrite.parsers.maven.ClasspathDependencies;
import org.springframework.rewrite.support.openrewrite.GenericOpenRewriteRecipe;
import org.springframework.rewrite.test.util.ParserExecutionHelper;
import org.springframework.rewrite.test.util.TestProjectHelper;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MavenTypeResolutionTest {

	@Nested
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	class WithSingleModuleMavenProject {

		private static RewriteProjectParsingResult parsingResult;

		/**
		 * Given: A Project with pom file containing dependency to @SpringBootApplication
		 * and a simple Java class When: The project is parsed Then: The classpath should
		 * contain the simple class and the @SpringBootApplication annotation. The
		 * typesInUse is empty because the simple class does not import/use any types
		 */
		@Test
		@Order(1)
		@DisplayName("parsed project should have correct classpath")
		void parsedProjectShouldHaveCorrectClasspath(@TempDir Path baseDir) {
			// given
			pepareProject(baseDir);
			// when
			parsingResult = new ParserExecutionHelper().parseWithRewriteProjectParser(baseDir,
					new SpringRewriteProperties());
			// then
			verifyTypesOnClasspathAndNoTypesInUse(parsingResult);
		}

		/**
		 * Given: The parsed project from (1) When: The annotation is added to the simple
		 * class Then: The classpath should contain the simple class and
		 * the @SpringBootApplication annotation. The typesInUse contains
		 * the @SpringBootApplication
		 */
		@Test
		@Order(2)
		@DisplayName("annotating the class adds the annotation to typesInUse")
		void annotatingTheClassAddsTheAnnotationToTypesInUse() {
			// Given: parsed project
			List<SourceFile> sourceFiles = parsingResult.sourceFiles();
			assertThat(sourceFiles).isNotEmpty();
			// When: Add @SpringBootApplication annotation to class
			RecipeRun recipeRun = annotateClass(sourceFiles);
			// Then: annotation is in typesInUse
			verifyAnnotationIsNowInUsedTypes(recipeRun);
		}

		private static void verifyAnnotationIsNowInUsedTypes(RecipeRun recipeRun) {
			SourceFile after = recipeRun.getChangeset().getAllResults().get(0).getAfter();
			assertThat(after).isInstanceOf(J.CompilationUnit.class);
			J.CompilationUnit cu = (J.CompilationUnit) after;
			List<String> classpath = cu.getMarkers()
				.findFirst(JavaSourceSet.class)
				.get()
				.getClasspath()
				.stream()
				.map(JavaType.FullyQualified::getFullyQualifiedName)
				.toList();
			assertThat(classpath).contains("org.springframework.boot.autoconfigure.SpringBootApplication", "SomeClass");

			List<String> typesInUse = cu.getTypesInUse()
				.getTypesInUse()
				.stream()
				.map(JavaType.FullyQualified.class::cast)
				.map(JavaType.FullyQualified::getFullyQualifiedName)
				.toList();
			assertThat(typesInUse)
				.containsExactlyInAnyOrder("org.springframework.boot.autoconfigure.SpringBootApplication");
		}

		@NotNull
		private static RecipeRun annotateClass(List<SourceFile> sourceFiles) {
			RecipeRun recipeRun = new GenericOpenRewriteRecipe<>(() -> new JavaIsoVisitor<>() {
				@Override
				public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl,
						ExecutionContext executionContext) {
					J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
					if (cd.getSimpleName().equals("SomeClass")) {
						ClasspathDependencies classpathDependencies = ((J.CompilationUnit) getCursor()
							.dropParentUntil(J.CompilationUnit.class::isInstance)
							.getValue()).getMarkers().findFirst(ClasspathDependencies.class).get();
						String annotationFqName = "org.springframework.boot.autoconfigure.SpringBootApplication";
						cd = JavaTemplate.builder("@SpringBootApplication")
							.imports(annotationFqName)
							.javaParser(JavaParser.fromJavaVersion()
								.classpath(classpathDependencies.getDependencies())
								.logCompilationWarningsAndErrors(true))
							.build()
							.apply(getCursor(), cd.getCoordinates()
								.addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
						maybeAddImport(annotationFqName);
					}
					return cd;
				}
			}).run(new InMemoryLargeSourceSet(sourceFiles), new RewriteExecutionContext());
			return recipeRun;
		}

		private static void verifyTypesOnClasspathAndNoTypesInUse(RewriteProjectParsingResult parallelParsingResult) {
			J.CompilationUnit cuBefore = parallelParsingResult.sourceFiles()
				.stream()
				.filter(J.CompilationUnit.class::isInstance)
				.map(J.CompilationUnit.class::cast)
				.findFirst()
				.get();
			List<String> typesInUseBefore = cuBefore.getTypesInUse()
				.getTypesInUse()
				.stream()
				.map(JavaType.FullyQualified.class::cast)
				.map(JavaType.FullyQualified::getFullyQualifiedName)
				.toList();
			assertThat(typesInUseBefore).isEmpty();
			List<String> classpathBefore = cuBefore.getMarkers()
				.findFirst(JavaSourceSet.class)
				.get()
				.getClasspath()
				.stream()
				.map(JavaType.FullyQualified::getFullyQualifiedName)
				.toList();
			assertThat(classpathBefore).contains("org.springframework.boot.autoconfigure.SpringBootApplication",
					"SomeClass");
		}

		private static void pepareProject(Path baseDir) {
			TestProjectHelper.createTestProject(baseDir)
				.addResource("pom.xml",
						"""
								<?xml version="1.0" encoding="UTF-8"?>
								    <project xmlns="http://maven.apache.org/POM/4.0.0"
								          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
								          xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
								     <modelVersion>4.0.0</modelVersion>
								     <groupId>org.example</groupId>
								     <artifactId>artifact</artifactId>
								     <version>0.1.0-SNAPSHOT</version>
								         <properties>
								              <maven.compiler.source>17</maven.compiler.source>
								              <maven.compiler.target>17</maven.compiler.target>
								          </properties>
								     <dependencies>
								         <dependency>
								             <groupId>org.springframework.boot</groupId>
								             <artifactId>spring-boot-autoconfigure</artifactId>
								             <version>3.1.3</version>
								         </dependency>
								     </dependencies>
								</project>
								""")

				.addResource("src/main/java/SomeClass.java",
				// @formatter:off
                            """
                           public class SomeClass {}
                           """
                            // @formatter:on
				)
				.writeToFilesystem();
		}

	}

	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	class WithMultiModuleMavenProject {

	}

}
