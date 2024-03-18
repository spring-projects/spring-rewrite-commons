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
package org.springframework.rewrite.plugin.maven;

import org.apache.maven.shared.invoker.InvocationRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.rewrite.plugin.shared.BuildConfig;
import org.springframework.rewrite.plugin.shared.DebugConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Fabian Krüger
 */
public class MavenInvocationRequestFactoryTest {

	@Test
	@DisplayName("build standard request")
	void buildStandardRequest() {
		MavenInvocationRequestFactory sut = new MavenInvocationRequestFactory();
		List<String> goals = List.of("clean", "package", "--fail-at-end");
		Consumer<String> lineConsumer = s -> {
		};
		Path baseDir = Path.of("./testcode/does-not-exist");

		InvocationRequest request = sut.createMavenInvocationRequest(baseDir, DebugConfig.disabled(), false,
				BuildConfig.defaultConfig(), goals, lineConsumer);

		assertThat(request.getGoals()).isEqualTo(goals);
		assertThat(request.getBaseDirectory()).isEqualTo(baseDir.toFile());
		assertThat(request.getMavenOpts()).isBlank();
		assertThat(request.getOutputHandler(null)).isNotNull();
	}

	@Test
	@DisplayName("build debug request")
	void buildDebugRequest() {
		MavenInvocationRequestFactory sut = new MavenInvocationRequestFactory();
		List<String> goals = List.of("clean", "package", "--fail-at-end");
		Consumer<String> lineConsumer = s -> {
		};
		Path baseDir = Path.of("./testcode/does-not-exist");

		InvocationRequest request = sut.createMavenInvocationRequest(baseDir, DebugConfig.from(1234, true), true,
				BuildConfig.defaultConfig(), goals, lineConsumer);

		assertThat(request.getMavenOpts())
			.isEqualTo(" -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=1234 ");
	}

	@Test
	@DisplayName("throws exception on empty baseDir")
	void errorListener() {
		MavenInvocationRequestFactory sut = new MavenInvocationRequestFactory();
		List<String> lines = new ArrayList<>();
		Path baseDir = Path.of("./testcode/does-not-exist").toAbsolutePath().normalize();
		@NotNull
		InvocationRequest result = sut.createMavenInvocationRequest(baseDir, DebugConfig.disabled(), false,
				BuildConfig.defaultConfig(), List.of("clean"), lines::add);
	}

	@Test
	@DisplayName("build declared memory request and skip tests")
	void buildDeclaredMemoryRequestAndSkipTests() {

		MavenInvocationRequestFactory sut = new MavenInvocationRequestFactory();
		List<String> goals = List.of("clean", "package", "--fail-at-end");
		Consumer<String> lineConsumer = s -> {
		};
		Path baseDir = Path.of("./testcode/does-not-exist");

		InvocationRequest request = sut.createMavenInvocationRequest(baseDir, DebugConfig.disabled(), false,
				BuildConfig.builder().withMemory("12M", "2G").skipTests(true).build(), goals, lineConsumer);

		assertThat(request.getMavenOpts()).isEqualTo("-Xms12M -Xmx2G ");
		assertThat(request.getGoals()).contains("-DskipTests");
	}

}
