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

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junitpioneer.jupiter.ExpectedToFail;
import org.junitpioneer.jupiter.Issue;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.SourceFile;
import org.openrewrite.java.tree.J;
import org.openrewrite.shaded.jgit.api.errors.GitAPIException;
import org.openrewrite.tree.ParsingEventListener;
import org.openrewrite.tree.ParsingExecutionContextView;
import org.springframework.rewrite.parser.maven.ComparingParserFactory;
import org.springframework.rewrite.parser.maven.RewriteMavenProjectParser;
import org.springframework.rewrite.test.util.DummyResource;
import org.springframework.rewrite.test.util.ParserLstParityTestHelper;
import org.springframework.rewrite.test.util.TestProjectHelper;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.fail;

/**
 * Test parity of generated LST between OpenRewrite parser logic and RewriteProjectParser.
 *
 * RewriteMavenProjectParser resembles the parser logic from OpenRewrite's Maven plugin
 *
 * @author Fabian Krüger
 */
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "The repository URIs of dependencies differ.")
@Issue("https://github.com/spring-projects/spring-rewrite-commons/issues/12")
@Disabled("https://github.com/spring-projects/spring-rewrite-commons/issues/81")
class RewriteProjectParserParityTest {

	@Test
	@DisplayName("parseResources")
	void parseResources() {
		Path baseDir = TestProjectHelper.getMavenProject("resources");
		ParserLstParityTestHelper.scanProjectDir(baseDir)
			.verifyParity((comparingParsingResult, testedParsingResult) -> {
				assertThat(comparingParsingResult.sourceFiles()).hasSize(5);
			});
	}

	@Test
	@DisplayName("testFailingProject")
	void testFailingProject() {
		Path baseDir = Path.of("./testcode/maven-projects/failing");
		ParserLstParityTestHelper.scanProjectDir(baseDir)
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
	@DisplayName("parse4Modules")
	void parse4Modules() {
		Path baseDir = TestProjectHelper.getMavenProject("4-modules");
		ParserLstParityTestHelper.scanProjectDir(baseDir)
			.verifyParity((comparingParsingResult, testedParsingResult) -> {
				assertThat(comparingParsingResult.sourceFiles()).hasSize(4);
				assertThat(testedParsingResult.sourceFiles()).hasSize(4);
			});
	}

	@Test
	@DisplayName("Parsing Simplistic Maven Project ")
	void parsingSimplisticMavenProject(@TempDir Path tempDir) throws GitAPIException {
		@Language("xml")
		String pomXml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
				    <modelVersion>4.0.0</modelVersion>
				    <groupId>org.example</groupId>
				    <artifactId>root-project</artifactId>
				    <version>1.0.0</version>
				    <properties>
				        <maven.compiler.target>17</maven.compiler.target>
				        <maven.compiler.source>17</maven.compiler.source>
				        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
				    </properties>
				        <repositories>
				            <repository>
				                <id>jcenter</id>
				                <name>jcenter</name>
				                <url>https://jcenter.bintray.com</url>
				            </repository>
				            <repository>
				                <id>mavencentral</id>
				                <name>mavencentral</name>
				                <url>https://repo.maven.apache.org/maven2</url>
				            </repository>
				        </repositories>
				    <dependencies>
				        <dependency>
				            <groupId>org.springframework.boot</groupId>
				            <artifactId>spring-boot-starter</artifactId>
				            <version>3.1.1</version>
				        </dependency>
				    </dependencies>
				</project>
				""";

		@Language("java")
		String javaClass = """
				package com.example;
				import org.springframework.boot.SpringApplication;
				import org.springframework.boot.autoconfigure.SpringBootApplication;

				@SpringBootApplication
				public class MyMain {
				    public static void main(String[] args){
				        SpringApplication.run(MyMain.class, args);
				    }
				}
				""";

		TestProjectHelper.createTestProject(tempDir)
			.withResources(new DummyResource(tempDir.resolve("src/main/java/com/example/MyMain.java"), javaClass),
					new DummyResource(tempDir.resolve("pom.xml"), pomXml))
			.initializeGitRepo() // trigger creation of GIT related marker
			.writeToFilesystem();

		SpringRewriteProperties comparingSpringRewriteProperties = new SpringRewriteProperties();
		Set<String> ignoredPathPatterns = Set.of("**/testcode/**", "testcode/**", ".rewrite-cache/**", "**/target/**",
				"**/.git/**");
		comparingSpringRewriteProperties.setIgnoredPathPatterns(ignoredPathPatterns);
		comparingSpringRewriteProperties.setPomCacheEnabled(true);
		comparingSpringRewriteProperties.setPomCacheEnabled(true);

		ParserLstParityTestHelper.scanProjectDir(tempDir)
			.withParserProperties(comparingSpringRewriteProperties)
			.verifyParity();
	}

	@NotNull
	private static InMemoryExecutionContext createExecutionContext() {
		return new InMemoryExecutionContext(t -> t.printStackTrace());
	}

	@Test
	@DisplayName("Parse multi-module-1")
	void parseMultiModule1() {
		Path baseDir = getMavenProject("multi-module-1");

		ParserLstParityTestHelper.scanProjectDir(baseDir).verifyParity();
	}

	@Test
	@DisplayName("Should Parse Maven Config Project")
	@Disabled("https://github.com/openrewrite/rewrite/issues/3409")
	void shouldParseMavenConfigProject() {
		Path baseDir = Path.of("./testcode/maven-projects/maven-config").toAbsolutePath().normalize();
		SpringRewriteProperties springRewriteProperties = new SpringRewriteProperties();
		springRewriteProperties.setIgnoredPathPatterns(Set.of(".mvn"));
		RewriteMavenProjectParser mavenProjectParser = new ComparingParserFactory().createComparingParser();
		RewriteProjectParsingResult parsingResult = mavenProjectParser.parse(baseDir,
				new InMemoryExecutionContext(t -> fail(t.getMessage())));
		assertThat(parsingResult.sourceFiles()).hasSize(2);
	}

	@Test
	@DisplayName("parseCheckstyle")
	@Issue("https://github.com/spring-projects-experimental/spring-boot-migrator/issues/875")
	void parseCheckstyle() {
		Path baseDir = getMavenProject("checkstyle");
		ParserLstParityTestHelper.scanProjectDir(baseDir)
			.parseSequentially()
			.verifyParity((comparingParsingResult, testedParsingResult) -> {
				assertThat(
						comparingParsingResult.sourceFiles().stream().map(sf -> sf.getSourcePath().toString()).toList())
					.contains(Path.of("checkstyle/rules.xml").toString());
				assertThat(
						comparingParsingResult.sourceFiles().stream().map(sf -> sf.getSourcePath().toString()).toList())
					.contains(Path.of("checkstyle/suppressions.xml").toString());
				assertThat(testedParsingResult.sourceFiles().stream().map(sf -> sf.getSourcePath().toString()).toList())
					.contains(Path.of("checkstyle/rules.xml").toString());
				assertThat(testedParsingResult.sourceFiles().stream().map(sf -> sf.getSourcePath().toString()).toList())
					.contains(Path.of("checkstyle/suppressions.xml").toString());
			});
	}

	@Test
	@DisplayName("Parse complex Maven reactor project")
	@ExpectedToFail("https://github.com/openrewrite/rewrite/issues/3409")
	void parseComplexMavenReactorProject() {
		Path projectRoot = Path.of("./testcode/maven-projects/cwa-server").toAbsolutePath().normalize();
		TestProjectHelper.createTestProject(projectRoot)
			.deleteDirIfExists()
			.cloneGitProject("https://github.com/corona-warn-app/cwa-server.git")
			.checkoutTag("v3.2.0")
			.writeToFilesystem();

		SpringRewriteProperties springRewriteProperties = new SpringRewriteProperties();
		springRewriteProperties.setIgnoredPathPatterns(Set.of(".rewrite/**", "internal/**"));

		List<String> parsedFiles = new ArrayList<>();
		ExecutionContext executionContext = createExecutionContext();
		ParsingExecutionContextView.view(executionContext).setParsingListener(new ParsingEventListener() {
			@Override
			public void parsed(Parser.Input input, SourceFile sourceFile) {
				DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
					.withLocale(Locale.US)
					.withZone(ZoneId.systemDefault());
				String format = dateTimeFormatter.format(Instant.now());
				System.out.println("%s: Parsed file: %s".formatted(format, sourceFile.getSourcePath()));
				parsedFiles.add(sourceFile.getSourcePath().toString());
			}
		});

		ParserLstParityTestHelper.scanProjectDir(projectRoot)
			.parseSequentially()
			.withExecutionContextForComparingParser(executionContext)
			.withParserProperties(springRewriteProperties)
			.verifyParity();
	}

	private Path getMavenProject(String s) {
		return Path.of("./testcode/maven-projects/").resolve(s).toAbsolutePath().normalize();
	}

	private enum ParserType {

		SBM, COMPARING

	}

}