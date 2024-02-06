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

import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaParser;
import org.openrewrite.quark.Quark;
import org.openrewrite.text.PlainText;
import org.slf4j.LoggerFactory;
import org.springframework.rewrite.test.util.DummyResource;
import org.springframework.rewrite.test.util.TestProjectHelper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Fabian Krüger
 */
public class RewriteResourceParserTest {

	@Test
	@DisplayName("should parse resources matching plain text mask")
	void shouldParseAsText(@TempDir Path baseDir) {
		List<String> plainTextMask = List.of("**.txt");
		DummyResource someText = new DummyResource(baseDir.resolve("src/main/resources/i-am-text.txt"), "some text");
		new TestProjectHelper(baseDir).withResources(someText).writeToFilesystem();
		Log logger = new Slf4jToMavenLoggerAdapter(LoggerFactory.getLogger(RewriteResourceParserTest.class));
		RewriteResourceParser resourceParser = new RewriteResourceParser(baseDir, new ArrayList<String>(),
				plainTextMask, 11, new ArrayList<Path>(), JavaParser.fromJavaVersion(), new RewriteExecutionContext());
		List<SourceFile> sourceFiles = resourceParser.parse(baseDir, List.of(someText), new HashSet<>()).toList();
		SourceFile parse = sourceFiles.get(0);
		assertThat(sourceFiles).hasSize(1);
		assertThat(parse).isInstanceOf(PlainText.class);
		assertThat(parse.printAll()).isEqualTo("some text");
	}

	@Test
	@DisplayName("should parse resources not matching plain text mask as Quark")
	void shouldParseAsQuark(@TempDir Path baseDir) {
		List<String> plainTextMask = List.of("**.foo");
		DummyResource someText = new DummyResource(baseDir.resolve("src/main/resources/i-am-text.txt"), "some text");
		new TestProjectHelper(baseDir).withResources(someText).writeToFilesystem();
		Log logger = new Slf4jToMavenLoggerAdapter(LoggerFactory.getLogger(RewriteResourceParserTest.class));
		RewriteResourceParser resourceParser = new RewriteResourceParser(baseDir, new ArrayList<String>(),
				plainTextMask, 11, new ArrayList<Path>(), JavaParser.fromJavaVersion(), new RewriteExecutionContext());
		List<SourceFile> sourceFiles = resourceParser.parse(baseDir, List.of(someText), new HashSet<>()).toList();
		SourceFile parse = sourceFiles.get(0);
		assertThat(sourceFiles).hasSize(1);
		assertThat(parse).isInstanceOf(Quark.class);
		assertThat(parse.printAll()).isEqualTo(""); // no access to content
	}

}
