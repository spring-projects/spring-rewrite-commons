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
package org.springframework.rewrite.parser;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junitpioneer.jupiter.Issue;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.rewrite.RewriteProjectParser;
import org.springframework.rewrite.boot.autoconfigure.SpringRewriteCommonsConfiguration;
import org.springframework.rewrite.embedder.MavenExecutor;
import org.springframework.rewrite.parser.maven.RewriteMavenProjectParser;
import org.springframework.rewrite.parser.maven.SbmTestConfiguration;
import org.springframework.rewrite.test.util.ParserExecutionHelper;
import org.springframework.rewrite.test.util.ParserParityTestHelper;
import org.springframework.rewrite.test.util.TestProjectHelper;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Fabian Krüger
 */
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "The repository URIs of dependencies differ.")
@Issue("https://github.com/spring-projects/spring-rewrite-commons/issues/12")
@SpringBootTest(classes = { SpringRewriteCommonsConfiguration.class, SbmTestConfiguration.class })
public class RewriteProjectParserIntegrationTest {

	@Autowired
	RewriteProjectParser sut;

	@Autowired
	ProjectScanner projectScanner;

	@Autowired
	RewriteMavenProjectParser mavenProjectParser;

	@Test
	@DisplayName("testFailingProject")
	void testFailingProject() {
		Path baseDir = Path.of("./testcode/maven-projects/failing");
		ParserParityTestHelper.scanProjectDir(baseDir)
			.parseSequentially()
			.verifyParity((comparingParsingResult, testedParsingResult) -> {
				assertThat(comparingParsingResult.sourceFiles().get(1)).isInstanceOf(J.CompilationUnit.class);
				J.CompilationUnit cu = (J.CompilationUnit) comparingParsingResult.sourceFiles().get(1);
				assertThat(cu.getTypesInUse()
					.getTypesInUse()
					.stream()
					.map(t -> t.toString())
					.anyMatch(t -> t.equals("javax.validation.constraints.Min"))).isTrue();

				assertThat(testedParsingResult.sourceFiles().get(1)).isInstanceOf(J.CompilationUnit.class);
				J.CompilationUnit cu2 = (J.CompilationUnit) testedParsingResult.sourceFiles().get(1);
				assertThat(cu2.getTypesInUse()
					.getTypesInUse()
					.stream()
					.map(t -> t.toString())
					.anyMatch(t -> t.equals("javax.validation.constraints.Min"))).isTrue();
			});
	}

	@Test
	@DisplayName("parseResources")
	void parseResources() {
		Path baseDir = TestProjectHelper.getMavenProject("resources");
		ParserParityTestHelper.scanProjectDir(baseDir)
			.parseSequentially()
			.verifyParity((comparingParsingResult, testedParsingResult) -> {
				assertThat(comparingParsingResult.sourceFiles()).hasSize(5);
			});
	}

	@Test
	@DisplayName("parseResources")
	void parseResourcesRewriteOnly() {
		Path baseDir = TestProjectHelper.getMavenProject("resources");
		RewriteProjectParsingResult parsingResult = new ParserExecutionHelper().parseWithComparingParser(baseDir,
				new SpringRewriteProperties(), new RewriteExecutionContext());
		List<String> list = parsingResult.sourceFiles()
			.get(3)
			.getMarkers()
			.findFirst(JavaSourceSet.class)
			.get()
			.getClasspath()
			.stream()
			.map(fqn -> fqn.getFullyQualifiedName())
			.toList();
		assertThat(list).contains("javax.validation.BootstrapConfiguration");
	}

	@Test
	// TODO: Move to maven-embedder
	@DisplayName("parseResources")
	void parseResourcesMavenExecutor() {
		Path baseDir = TestProjectHelper.getMavenProject("resources");
		AtomicReference<List<String>> cpRef = new AtomicReference<>();
		new MavenExecutor(event -> {
			MavenSession mavenSession = event.getSession();
			MavenProject application = mavenSession.getProjects()
				.stream()
				.filter(p -> p.getArtifactId().equals("application"))
				.findFirst()
				.get();
			List<Dependency> compileDependencies = application.getCompileDependencies();
			try {
				List<String> compileClasspathElements = application.getCompileClasspathElements();
				cpRef.set(compileClasspathElements);
			}
			catch (DependencyResolutionRequiredException e) {
				throw new RuntimeException(e);
			}

		}).execute(List.of("clean", "package"), baseDir);

		assertThat(cpRef.get()).contains(
				Path.of(System.getProperty("user.home"))
					.resolve(
							".m2/repository/javax/validation/validation-api/2.0.1.Final/validation-api-2.0.1.Final.jar")
					.toString(),
				Path.of(".")
					.resolve("testcode/maven-projects/resources/application/target/classes")
					.toAbsolutePath()
					.normalize()
					.toString());
	}

	@Test
	// TODO: Move to maven-embedder
	@DisplayName("parseResources")
	void parseTest1MavenExecutor() {
		Path baseDir = TestProjectHelper.getMavenProject("test1");
		AtomicReference<List<String>> cpRef = new AtomicReference<>();
		new MavenExecutor(event -> {
			MavenSession mavenSession = event.getSession();
			MavenProject application = mavenSession.getProjects()
				.stream()
				.filter(p -> p.getArtifactId().equals("dummy-root"))
				.findFirst()
				.get();
			List<Dependency> compileDependencies = application.getCompileDependencies();
			try {
				List<String> compileClasspathElements = application.getCompileClasspathElements();
				cpRef.set(compileClasspathElements);
			}
			catch (DependencyResolutionRequiredException e) {
				throw new RuntimeException(e);
			}

		}).execute(List.of("clean", "package"), baseDir);

		assertThat(cpRef.get()).contains(
				Path.of(System.getProperty("user.home"))
					.resolve(
							".m2/repository/javax/validation/validation-api/2.0.1.Final/validation-api-2.0.1.Final.jar")
					.toString(),
				Path.of(".")
					.resolve("testcode/maven-projects/test1/target/classes")
					.toAbsolutePath()
					.normalize()
					.toString());
	}

	@Test
	@DisplayName("parse4Modules")
	void parse4Modules() {
		Path baseDir = TestProjectHelper.getMavenProject("4-modules");
		ParserParityTestHelper.scanProjectDir(baseDir).verifyParity((comparingParsingResult, testedParsingResult) -> {
			assertThat(comparingParsingResult.sourceFiles()).hasSize(4);
			assertThat(testedParsingResult.sourceFiles()).hasSize(4);
		});
	}

}
