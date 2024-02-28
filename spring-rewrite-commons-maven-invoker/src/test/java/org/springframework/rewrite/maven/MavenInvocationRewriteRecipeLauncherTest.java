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
import org.springframework.test.util.ReflectionTestUtils;

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
class MavenInvocationRewriteRecipeLauncherTest {

	private static final int SUCCESS = 0;

	private MavenInvocationRewriteRecipeLauncher.Builders.OptionalBuilder invokerBuilder;

	@Mock
	Invoker mavenInvoker = mock(Invoker.class);

	private Path baseDir = Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize();

	private List<String> capturedLines = new ArrayList<>();

	private Consumer<String> outputListener = capturedLines::add;

	@BeforeEach
	void beforeEach() throws MavenInvocationException {
		outputListener = capturedLines::add;
		invokerBuilder = MavenInvocationRewriteRecipeLauncher
			.applyRecipes("org.openrewrite.java.RemoveUnusedImports", "org.openrewrite.java.format.AutoFormat")
			.onDir(baseDir)
			.withInvoker(mavenInvoker)
			.withOutputListener(outputListener);

		InvocationResult result = new DummyInvocationResult();
		when(mavenInvoker.execute(any(InvocationRequest.class))).thenReturn(result);
	}

	@Test
	@DisplayName("minimal")
	void minimal() throws MavenInvocationException {
		InvocationResult actualResult = invokerBuilder.run();

		assertThat(actualResult.getExitCode()).isEqualTo(0);
		String[] expectedGoals = { "clean", "package", "--fail-at-end",
				"org.openrewrite.maven:rewrite-maven-plugin:run -Drewrite.activeRecipes=org.openrewrite.java.RemoveUnusedImports,org.openrewrite.java.format.AutoFormat" };
		verifyCallToMaven(expectedGoals);
	}

	@Test
	@DisplayName("with dependencies")
	void withDependencies() throws MavenInvocationException {
		InvocationResult actualResult = invokerBuilder
			.withDependencies("com.example:some-dep:1.0.0", "com.example:another-dep:1.0.0")
			.run();
		assertThat(actualResult.getExitCode()).isEqualTo(0);
		String[] expectedGoals = { "clean", "package", "--fail-at-end",
				"org.openrewrite.maven:rewrite-maven-plugin:run -Drewrite.activeRecipes=org.openrewrite.java.RemoveUnusedImports,org.openrewrite.java.format.AutoFormat -Drewrite.recipeArtifactCoordinates=com.example:some-dep:1.0.0,com.example:another-dep:1.0.0" };
		verifyCallToMaven(expectedGoals);
	}

	@Test
	@DisplayName("with build tools config")
	void withBuildToolConfig() throws MavenInvocationException {

		InvocationResult actualResult = invokerBuilder
			.withBuildConfig(BuildConfig.builder().withMemory("64M", "256M").skipTests(true).build())
			.run();
		assertThat(actualResult.getExitCode()).isEqualTo(0);
		String[] expectedGoals = { "clean", "package", "--fail-at-end",
				"org.openrewrite.maven:rewrite-maven-plugin:run -Drewrite.activeRecipes=org.openrewrite.java.RemoveUnusedImports,org.openrewrite.java.format.AutoFormat" };
		verifyCallToMaven(expectedGoals, BuildConfig.builder().withMemory("64M", "256M").skipTests(true).build());
	}

	@Test
	@DisplayName("builder")
	void builder(@TempDir Path tempDir) {
		InvocationResult result = MavenInvocationRewriteRecipeLauncher.applyRecipes("recipe1").onDir(tempDir).run();

		MavenInvocationRewriteRecipeLauncher.applyRecipes("recipe1").onDir(tempDir).withDependencies("", "").run();

		MavenInvocationRewriteRecipeLauncher.applyRecipes(List.of("NoName")).onDir(tempDir).withDependencies("").run();

		// build tool config only
		MavenInvocationRewriteRecipeLauncher.applyRecipes(List.of("NoName"))
			.onDir(tempDir)
			.withDependencies("")
			.withBuildConfig(BuildConfig.defaultConfig())
			.run();

		// debug config only
		MavenInvocationRewriteRecipeLauncher.applyRecipes("NoName")
			.onDir(tempDir)
			.withDependencies("")
			.withDebugConfig(DebugConfig.disabled())
			.run();

		MavenInvocationRewriteRecipeLauncher.applyRecipes(List.of("NoName"))
			.onDir(tempDir)
			.withDependencies("")
			.withDebugConfig(DebugConfig.disabled())
			.dryRun();

		// build tool and debug config
		MavenInvocationRewriteRecipeLauncher.applyRecipes(List.of("NoName"))
			.onDir(tempDir)
			.withDependencies("")
			.withBuildConfig(BuildConfig.defaultConfig())
			.withDebugConfig(DebugConfig.disabled())
			.run();

		MavenInvocationRewriteRecipeLauncher.applyRecipes("NoName").onDir(tempDir).run();
	}

	@Test
	@DisplayName("missing recipeNames should throw exception")
	void missingRecipeNamesShouldThrowException() {
		assertThrows(IllegalArgumentException.class, () -> {
			MavenInvocationRewriteRecipeLauncher.applyRecipes("").onDir(Path.of("")).run();
		});
	}

	@Test
	@DisplayName("mavenInvocationRequestBuilder extra memory")
	void mavenInvokerExtraMemory() {
		List<String> lines = new ArrayList<>();
		Path baseDir = Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize();

		InvocationResult result = MavenInvocationRewriteRecipeLauncher
			.applyRecipes("org.openrewrite.java.RemoveUnusedImports")
			.onDir(baseDir)
			.withBuildConfig(BuildConfig.builder().skipTests(false).withMemory("32M", "1G").build())
			.withOutputListener(lines::add)
			.dryRun();

		assertThat(result.getExitCode()).isEqualTo(SUCCESS);
		assertThat(lines).contains("[INFO] Scanning for projects...",
				"[INFO] --- maven-clean-plugin:2.5:clean (default-clean) @ simple ---", "[INFO] BUILD SUCCESS");
	}

	private void verifyCallToMaven(String[] expectedGoals) throws MavenInvocationException {
		BuildConfig buildConfig = BuildConfig.defaultConfig();
		verifyCallToMaven(expectedGoals, buildConfig, DebugConfig.disabled(), baseDir, outputListener,
				Arrays.asList(expectedGoals));
	}

	private void verifyCallToMaven(String[] expectedGoals, BuildConfig buildConfig) throws MavenInvocationException {
		verifyCallToMaven(expectedGoals, buildConfig, DebugConfig.disabled(), baseDir, outputListener,
				Arrays.asList(expectedGoals));
	}

	private void verifyCallToMaven(String[] expectedGoals, BuildConfig buildConfig, DebugConfig debugConfig,
			Path baseDir, Consumer<String> lineConsumer, List<String> givenGoals) throws MavenInvocationException {
		InvocationRequest mavenInvocationRequest = new MavenInvocationRequestFactory()
			.createMavenInvocationRequest(baseDir, debugConfig, buildConfig, givenGoals, lineConsumer);
		ArgumentCaptor<InvocationRequest> captor = ArgumentCaptor.forClass(InvocationRequest.class);
		verify(mavenInvoker).execute(captor.capture());
		InvocationRequest request = captor.getValue();
		assertThat(request).usingRecursiveComparison().isEqualTo(mavenInvocationRequest);
	}

}