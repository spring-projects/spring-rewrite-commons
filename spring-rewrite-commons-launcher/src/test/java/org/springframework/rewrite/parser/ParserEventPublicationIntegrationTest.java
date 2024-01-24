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
import org.openrewrite.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.rewrite.parser.events.FinishedParsingResourceEvent;
import org.springframework.rewrite.parser.events.StartedParsingProjectEvent;
import org.springframework.rewrite.parser.events.SuccessfullyParsedProjectEvent;
import org.springframework.rewrite.utils.LinuxWindowsPathUnifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Fabian Krüger
 */
@SpringBootTest(
		classes = { RewriteParserConfiguration.class, ParserEventPublicationIntegrationTest.TestEventListener.class })
public class ParserEventPublicationIntegrationTest {

	@Autowired
	RewriteProjectParser sut;

	@Autowired
	ProjectScanner projectScanner;

	@Autowired
	SpringRewriteProperties springRewriteProperties;

	@Autowired
	ExecutionContext executionContext;

	private static List<String> capturedEvents = new ArrayList<>();

	private static StartedParsingProjectEvent startedParsingEvent;

	private static SuccessfullyParsedProjectEvent finishedParsingEvent;

	@Test
	@DisplayName("Should publish parsing events")
	void shouldPublishParsingEvents() {
		Path baseDir = Path.of("./testcode/maven-projects/multi-module-events");
		springRewriteProperties.setIgnoredPathPatterns(Set.of("{**/target/**,target/**}", "**.adoc"));
		List<Resource> resources = projectScanner.scan(baseDir);

		RewriteProjectParsingResult parsingResult = sut.parse(baseDir, resources);

		assertThat(parsingResult.sourceFiles()).hasSize(5);
		assertThat(parsingResult.sourceFiles()
			.stream()
			.map(s -> LinuxWindowsPathUnifier.unifiedPathString(s.getSourcePath()))
			.toList()).containsExactly("pom.xml", "module-b/pom.xml", "module-a/pom.xml",
					"module-b/src/test/resources/application.yaml", "module-a/src/main/java/com/acme/SomeClass.java");

		assertThat(capturedEvents).hasSize(5);

		assertThat(capturedEvents.get(0)).isEqualTo("pom.xml");
		assertThat(capturedEvents.get(1)).isEqualTo("module-b/pom.xml");
		assertThat(capturedEvents.get(2)).isEqualTo("module-a/pom.xml");
		assertThat(capturedEvents.get(3)).isEqualTo("module-b/src/test/resources/application.yaml");
		assertThat(capturedEvents.get(4)).isEqualTo("module-a/src/main/java/com/acme/SomeClass.java");
		// ResourceParser not firing events
		// TODO: reactivate after
		// https://github.com/openrewrite/rewrite-maven-plugin/issues/622
		// assertThat(capturedEvents.get(4).sourceFile().getSourcePath().toString())
		// .isEqualTo("module-a/src/test/resources/application.yaml");

		assertThat(startedParsingEvent).isNotNull();
		assertThat(startedParsingEvent.resources()).isSameAs(resources);
		assertThat(finishedParsingEvent).isNotNull();
		assertThat(finishedParsingEvent.sourceFiles()).isSameAs(parsingResult.sourceFiles());
	}

	@TestConfiguration
	static class TestEventListener {

		@EventListener(FinishedParsingResourceEvent.class)
		public void onEvent(FinishedParsingResourceEvent event) {
			String unifiedPathString = LinuxWindowsPathUnifier.unifiedPathString(event.sourceFile().getSourcePath());
			capturedEvents.add(unifiedPathString);
		}

		@EventListener(StartedParsingProjectEvent.class)
		public void onStartedParsingProjectEvent(StartedParsingProjectEvent event) {
			startedParsingEvent = event;
		}

		@EventListener(SuccessfullyParsedProjectEvent.class)
		public void onFinishedParsingProjectEvent(SuccessfullyParsedProjectEvent event) {
			finishedParsingEvent = event;
		}

	}

}
