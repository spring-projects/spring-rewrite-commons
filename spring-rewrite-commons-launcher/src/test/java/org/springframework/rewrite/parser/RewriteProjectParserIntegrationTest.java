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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junitpioneer.jupiter.Issue;
import org.openrewrite.java.tree.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.rewrite.RewriteProjectParser;
import org.springframework.rewrite.boot.autoconfigure.SpringRewriteCommonsConfiguration;
import org.springframework.rewrite.parser.maven.RewriteMavenProjectParser;
import org.springframework.rewrite.parser.maven.SbmTestConfiguration;
import org.springframework.rewrite.test.util.ParserParityTestHelper;
import org.springframework.rewrite.test.util.TestProjectHelper;

import java.nio.file.Path;

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
		ParserParityTestHelper.scanProjectDir(baseDir).verifyParity((comparingParsingResult, testedParsingResult) -> {
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
		ParserParityTestHelper.scanProjectDir(baseDir).verifyParity((comparingParsingResult, testedParsingResult) -> {
			assertThat(comparingParsingResult.sourceFiles()).hasSize(5);
		});
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
