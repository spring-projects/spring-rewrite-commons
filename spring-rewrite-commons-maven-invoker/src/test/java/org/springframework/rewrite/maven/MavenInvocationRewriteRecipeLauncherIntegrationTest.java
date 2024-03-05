package org.springframework.rewrite.maven;

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

import org.apache.maven.shared.invoker.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * @author Fabian Krüger
 */
class MavenInvocationRewriteRecipeLauncherIntegrationTest {

	@Mock
	private Path baseDir = Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize();

	@Test
	@DisplayName("invokeRewritePlugin() method test")
	void invokeRewritePluginMethodTest() {
		List<String> capturedLines = new ArrayList<>();
		MavenInvocationRewriteRecipeLauncher sut = new MavenInvocationRewriteRecipeLauncher();
		sut.invokeRewritePlugin(Path.of("./testcode/maven-projects/simple"),
				List.of("org.openrewrite.java.RemoveUnusedImports"), new ArrayList<>(), DebugConfig.from(9090, false),
				BuildConfig.builder().skipTests(true).withMemory("64M", "256M").build(), capturedLines::add,
				RewritePluginGoals.DRY_RUN);
		assertThat(capturedLines).contains("Listening for transport dt_socket at address: 9090",
				"[INFO] --- maven-clean-plugin:2.5:clean (default-clean) @ simple ---", "[INFO] Tests are skipped.",
				"[INFO] Using active recipe(s) [org.openrewrite.java.RemoveUnusedImports]",
				"[INFO] Using active styles(s) []", "[INFO] BUILD SUCCESS");
	}

	@Test
	@DisplayName("mavenInvocationRequestBuilder success")
	void mavenInvoker() {
		List<String> capturedLines = new ArrayList<>();
		Path baseDir = Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize();

		InvocationResult result = MavenInvocationRewriteRecipeLauncher
			.applyRecipes("org.openrewrite.java.RemoveUnusedImports", "org.openrewrite.java.format.AutoFormat")
			.onDir(baseDir)
			.withOutputListener(capturedLines::add)
			.dryRun();

		assertThat(result.getExitCode()).isEqualTo(0);
		assertThat(capturedLines).contains("[INFO] Scanning for projects...",
				"[INFO] --- maven-clean-plugin:2.5:clean (default-clean) @ simple ---",
				"[INFO] Using active recipe(s) [org.openrewrite.java.RemoveUnusedImports, org.openrewrite.java.format.AutoFormat]",
				"[INFO] BUILD SUCCESS");
	}

	@Test
	@DisplayName("with debug enabled")
	void withDebugEnabled() {
		List<String> capturedLines = new ArrayList<>();

		InvocationResult result = MavenInvocationRewriteRecipeLauncher
			.applyRecipes("org.openrewrite.java.RemoveUnusedImports")
			.onDir(Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize())
			.withOutputListener(capturedLines::add)
			.withDebugConfig(DebugConfig.fromDefault())
			.withBuildConfig(BuildConfig.builder().withMemory("1G", "6G").build())
			.run();
		assertThat(capturedLines).contains("Listening for transport dt_socket at address: 5005");
		assertThat(result.getExitCode()).isEqualTo(0);
	}

	@Test
	@DisplayName("cyclonedx")
	void cyclonedx() {
		List<String> lines = new ArrayList<>();
		MavenInvocationRewriteRecipeLauncher.Builders.OptionalBuilder builder = getBuilder(lines);
		InvocationResult result = builder.cyclonedx();
		assertThat(result.getExitCode()).isEqualTo(0);
		assertThat(lines.stream().anyMatch(l -> l.endsWith(":cyclonedx (default-cli) @ simple ---"))).isTrue();
		assertThat(baseDir.resolve("target/simple-0.1.0-SNAPSHOT-cyclonedx.xml")).exists();
	}

	@Test
	@DisplayName("dryRun")
	void dryRun() {
		List<String> lines = new ArrayList<>();
		MavenInvocationRewriteRecipeLauncher.Builders.OptionalBuilder builder = getBuilder(lines);
		InvocationResult result = builder.dryRun();
		assertThat(result.getExitCode()).isEqualTo(0);
		assertThat(lines.stream().anyMatch(l -> l.endsWith(":dryRun (default-cli) @ simple ---"))).isTrue();
	}

	@Test
	@DisplayName("dryRunNoFork")
	void dryRunNoFork() {
		List<String> lines = new ArrayList<>();
		MavenInvocationRewriteRecipeLauncher.Builders.OptionalBuilder builder = getBuilder(lines);
		InvocationResult result = builder.dryRunNoFork();
		assertThat(result.getExitCode()).isEqualTo(0);
		assertThat(lines.stream().anyMatch(l -> l.endsWith(":dryRunNoFork (default-cli) @ simple ---"))).isTrue();
	}

	@Test
	@DisplayName("runNoFork")
	void runNoFork() {
		List<String> lines = new ArrayList<>();
		MavenInvocationRewriteRecipeLauncher.Builders.OptionalBuilder builder = getBuilder(lines);
		InvocationResult result = builder.dryRunNoFork();
		assertThat(result.getExitCode()).isEqualTo(0);
		assertThat(lines.stream().anyMatch(l -> l.endsWith(":dryRunNoFork (default-cli) @ simple ---"))).isTrue();
	}

	@Test
	@DisplayName("discover")
	void discover() {
		List<String> lines = new ArrayList<>();
		MavenInvocationRewriteRecipeLauncher.Builders.OptionalBuilder builder = getBuilder(lines);
		InvocationResult result = builder.discover();
		assertThat(result.getExitCode()).isEqualTo(0);
		assertThat(lines).contains("[INFO] Available Recipes:", "[INFO]     org.openrewrite.FindSourceFiles");
		assertThat(lines.stream().anyMatch(l -> l.endsWith(":discover (default-cli) @ simple ---"))).isTrue();
	}

	private static MavenInvocationRewriteRecipeLauncher.Builders.OptionalBuilder getBuilder(List<String> lines) {
		Path baseDir = Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize();
		MavenInvocationRewriteRecipeLauncher.Builders.OptionalBuilder builder = MavenInvocationRewriteRecipeLauncher
			.applyRecipes("org.openrewrite.java.RemoveUnusedImports")
			.onDir(baseDir)
			.withOutputListener(lines::add);
		return builder;
	}

}