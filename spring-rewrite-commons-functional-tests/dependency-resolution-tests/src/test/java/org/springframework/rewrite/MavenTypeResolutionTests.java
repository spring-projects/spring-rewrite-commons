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

import org.intellij.lang.annotations.Language;
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
import org.openrewrite.java.tree.Statement;
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

public class MavenTypeResolutionTests {

	@Nested
	class ClassesFromMainCanBeResolvedInTest {

		@Test
		@DisplayName("TestClass should have MainClass in used types")
		void testClassShouldHaveMainClassInUsedTypes(@TempDir Path baseDir) {

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

				.addResource("src/main/java/main/SomeClass.java", """
											package main;
						public class SomeClass {}
						""")
				.addResource("src/test/java/test/SomeTest.java", """
										package test;
										import main.SomeClass;
						public class SomeTest {
							private SomeClass someClass;
						}
						""")
				.writeToFilesystem();

			RewriteProjectParsingResult parsingResult = new ParserExecutionHelper()
				.parseWithRewriteProjectParser(baseDir, new SpringRewriteProperties());

			SourceFile sourceFile = parsingResult.sourceFiles()
				.stream()
				.filter(f -> f.getSourcePath().toString().endsWith("SomeTest.java"))
				.findFirst()
				.get();
			assertThat(sourceFile).isNotNull();
			J.CompilationUnit cu = (J.CompilationUnit) sourceFile;
			cu.getClasses().get(0).getBody().getStatements().get(0);
			assertThat(
					((JavaType.Class) ((J.VariableDeclarations) cu.getClasses().get(0).getBody().getStatements().get(0))
						.getTypeExpression()
						.getType()).getFullyQualifiedName())
				.isEqualTo("main.SomeClass");
		}

	}

	@Nested
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	class WithSingleModuleMavenProject {

		private static RewriteProjectParsingResult parsingResult;

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
			// formatter:off
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

				.addResource("src/main/java/SomeClass.java", """
						public class SomeClass {}
						""")
				.writeToFilesystem();
			// formatter:on
		}

	}

	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	@Nested
	class WithMultiModuleMavenProject {

		/**
		 * formatter:off Multi-module project with two modules module-1 and module-2.
		 * module-2 has a dependency to spring and a class in main Module2Class and one in
		 * test Module2Test module-1 has a dependency to module-2 and a class in main
		 * Module1Class and one in test Module1Test, Module1Class depends on Module2Class
		 * and MOdule1Test depends on Module2Class and Module2Test. formatter:on
		 */
		@Test
		@DisplayName("Module-1 should have classpath from module-2")
		void module1ShouldHaveClasspathFromModule2(@TempDir Path baseDir) {
			TestProjectHelper.createTestProject(baseDir)
				.addResource("pom.xml", PARENT_POM)
				.addResource("module-1/pom.xml", MODULE_1_POM)
				.addResource("module-2/pom.xml", MODULE_2_POM)
				.addResource("module-1/src/main/java/com/example/module1/main/Module1Class.java", MODULE_1_CLASS)
				.addResource("module-1/src/test/java/com/example/module1/test/Module1Test.java", MODULE_1_TEST)
				.addResource("module-2/src/main/java/com/example/module2/main/Module2Class.java", MODULE_2_CLASS)
				.addResource("module-2/src/test/java/com/example/module2/test/Module2Test.java", MODULE_2_TEST)
				.writeToFilesystem();

			RewriteProjectParsingResult parsingResult = new ParserExecutionHelper()
				.parseWithRewriteProjectParser(baseDir, new SpringRewriteProperties());

			// Module2Class
			J.CompilationUnit module2class = getCompilationContainginTypeEndingWith("Module2Class.java", parsingResult);
			// classpath
			List<String> cpModule2 = getClasspath(module2class);
			// typesInUse
			List<String> module2classTypesInUse = getTypesInUse(module2class);

			assertThat(module2classTypesInUse).contains("org.springframework.boot.autoconfigure.SpringBootApplication");
			assertThat(cpModule2).contains("org.springframework.boot.autoconfigure.SpringBootApplication");
			// Module2Test
			J.CompilationUnit module2test = getCompilationContainginTypeEndingWith("Module2Test.java", parsingResult);
			// typesInUse
			List<String> module2testTypesInUse = getTypesInUse(module2test);
			// classpath
			List<String> classpathModule2test = getClasspath(module2test);

			Statement actual = module2test.getClasses().get(0).getBody().getStatements().get(0);
			assertThat(classpathModule2test).contains("com.example.module2.main.Module2Class",
					"com.example.module2.test.Module2Test",
					"org.springframework.boot.autoconfigure.SpringBootApplication",
					"org.springframework.boot.test.context.SpringBootTest");

			// Module1Class
			J.CompilationUnit module1class = getCompilationContainginTypeEndingWith("Module1Class.java", parsingResult);
			// typesInUse
			List<String> module1classTypesInUse = getTypesInUse(module1class);
			// classpath
			List<String> module1classClasspath = getClasspath(module1class);

			assertThat(module1classTypesInUse).contains("com.example.module2.main.Module2Class");
			assertThat(module1classClasspath).contains("com.example.module1.main.Module1Class",
					"com.example.module2.main.Module2Class",
					"org.springframework.boot.autoconfigure.SpringBootApplication");

			assertThat(module1classClasspath).doesNotContain("com.example.module2.test.Module2Test",
					"org.springframework.boot.test.context.SpringBootTest");

			// Module1Test
			J.CompilationUnit module1test = getCompilationContainginTypeEndingWith("Module1Test.java", parsingResult);
			// typesInUse
			List<String> module1testTypesInUse = getTypesInUse(module1test);
			// classpath
			List<String> classpathModule1test = getClasspath(module1test);

			assertThat(module1testTypesInUse).contains("com.example.module2.main.Module2Class",
					"com.example.module2.test.Module2Test", "org.springframework.boot.test.context.SpringBootTest");
			assertThat(classpathModule1test).contains("com.example.module1.main.Module1Class",
					"com.example.module1.test.Module1Test",
					"org.springframework.boot.autoconfigure.SpringBootApplication",
					"org.springframework.boot.test.context.SpringBootTest", "com.example.module2.main.Module2Class",
					"com.example.module2.test.Module2Test");

		}

		// @formatter:off

		@Language("xml")
		private static final String PARENT_POM =
				"""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                     <modelVersion>4.0.0</modelVersion>
                     <groupId>com.example</groupId>
                     <artifactId>parent</artifactId>
                     <version>0.1.0-SNAPSHOT</version>
                     <packaging>pom</packaging>
                     <properties>
                          <maven.compiler.source>17</maven.compiler.source>
                          <maven.compiler.target>17</maven.compiler.target>
                      </properties>
                      <modules>
                          <module>module-1</module>
                          <module>module-2</module>
                      </modules>
                </project>
                """;

		@Language("xml")
		private static final String MODULE_1_POM =
				"""
				<?xml version="1.0" encoding="UTF-8"?>
				<project xmlns="http://maven.apache.org/POM/4.0.0"
				xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
					<modelVersion>4.0.0</modelVersion>
					<parent>
						<groupId>com.example</groupId>
						<artifactId>parent</artifactId>
						<version>0.1.0-SNAPSHOT</version>
						<relativePath>../pom.xml</relativePath>
					</parent>
					<artifactId>module-1</artifactId>
					<properties>
						<maven.compiler.source>17</maven.compiler.source>
						<maven.compiler.target>17</maven.compiler.target>
					</properties>
					<dependencies>
						<dependency>
							<groupId>com.example</groupId>
							<artifactId>module-2</artifactId>
							<version>0.1.0-SNAPSHOT</version>
						</dependency>
						<dependency>
							<groupId>org.springframework.boot</groupId>
							<artifactId>spring-boot-test</artifactId>
							<version>3.1.2</version>
							<scope>test</scope>
						</dependency>
					</dependencies>
				</project>
               	""";

		private static final String MODULE_1_CLASS =
				"""
                package com.example.module1.main;
                import com.example.module2.main.Module2Class;

                public class Module1Class {
                    private Module2Class module2Class;
                }
                """;

		private static final String MODULE_1_TEST =
				"""
                package com.example.module1.test;
                import com.example.module2.main.Module2Class;
                import com.example.module2.test.Module2Test;
				import org.springframework.boot.test.context.SpringBootTest;

				@SpringBootTest
                public class Module1Test extends Module2Test {
                    private Module1Class module1Class;
                    private Module2Class module2Class;
                }
                """;

		@Language("xml")
		private static final String MODULE_2_POM =
				"""
                <?xml version="1.0" encoding="UTF-8"?>
                    <project xmlns="http://maven.apache.org/POM/4.0.0"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                     <modelVersion>4.0.0</modelVersion>
                     <parent>
                         <groupId>com.example</groupId>
                         <artifactId>parent</artifactId>
                         <version>0.1.0-SNAPSHOT</version>
                         <relativePath>../pom.xml</relativePath>
                     </parent>
                     <artifactId>module-2</artifactId>
                     <properties>
                          <maven.compiler.source>17</maven.compiler.source>
                          <maven.compiler.target>17</maven.compiler.target>
                      </properties>
                      <dependencies>
                         <dependency>
                             <groupId>org.springframework.boot</groupId>
                             <artifactId>spring-boot-autoconfigure</artifactId>
                             <version>3.1.2</version>
                         </dependency>
                         <dependency>
                             <groupId>org.springframework.boot</groupId>
                             <artifactId>spring-boot-test</artifactId>
                             <version>3.1.2</version>
                             <scope>test</scope>
                         </dependency>
                      </dependencies>
                </project>
                """;
		private static final String MODULE_2_CLASS =
				"""
				package com.example.module2.main;
				import org.springframework.boot.autoconfigure.SpringBootApplication;

				@SpringBootApplication
				public class Module2Class {
				}
				""";

		private static final String MODULE_2_TEST =
				"""
				package com.example.module2.test;
				import com.example.module2.main.Module2Class;
				import org.springframework.boot.test.context.SpringBootTest;

				@SpringBootTest
				public class Module2Test {
					Module2Class module2Class;
				}
				""";

		// @formatter:on

		@NotNull
		private static List<String> getClasspath(J.CompilationUnit module2class) {
			return module2class.getMarkers()
				.findFirst(JavaSourceSet.class)
				.get()
				.getClasspath()
				.stream()
				.map(JavaType.FullyQualified::getFullyQualifiedName)
				.toList();
		}

		@NotNull
		private static List<String> getTypesInUse(J.CompilationUnit module2class) {
			return module2class.getTypesInUse()
				.getTypesInUse()
				.stream()
				.map(JavaType.FullyQualified.class::cast)
				.map(JavaType.FullyQualified::getFullyQualifiedName)
				.toList();
		}

		@NotNull
		private static J.CompilationUnit getCompilationContainginTypeEndingWith(String endsWith,
				RewriteProjectParsingResult parsingResult) {
			return (J.CompilationUnit) parsingResult.sourceFiles()
				.stream()
				.filter(sf -> sf.getSourcePath().toString().endsWith(endsWith))
				.findFirst()
				.get();
		}

	}

}
