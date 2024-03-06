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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.rewrite.plugin.shared.BuildConfig;
import org.springframework.rewrite.plugin.shared.DebugConfig;
import org.springframework.rewrite.plugin.shared.PluginInvocationResult;
import org.springframework.test.util.TestSocketUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * @author Fabian Krüger
 */
class RewriteMavenPluginTest {

	public static final String[] RECIPES = { "org.openrewrite.java.RemoveUnusedImports",
			"org.openrewrite.java.format.NormalizeLineBreaks" };

	public static final String[] EXTENDED_RECIPES = { "org.openrewrite.java.RemoveUnusedImports",
			"org.openrewrite.java.format.NormalizeLineBreaks",
			"org.openrewrite.java.migrate.jakarta.MaybeAddJakartaServletApi" };

	public static final String EXTENDED_REFCIPES_DEPS = "org.openrewrite.recipe:rewrite-migrate-java:2.9.0";

	public static final String[] DEFAULT_GOALS = new String[] { "resources", "compile", "testResources",
			"testCompile" };

	@Test
	@DisplayName("simple project simple config")
	void simpleProjectSimpleConfig() {
		Path baseDir = Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize();

		PluginInvocationResult result = RewriteMavenPlugin.run().recipes(RECIPES).onDir(baseDir);

		String out = result.capturedOutput();
		assertThat(result.success()).isTrue();
		assertTasksExecuted(out, tasksOf(DEFAULT_GOALS, "run"));
		assertRecipesExecuted(out, RECIPES);
	}

	@Test
	@DisplayName("builder runNoFork")
	void simpleProjectSimpleConfigRunNoFork() {
		Path baseDir = Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize();
		PluginInvocationResult result = RewriteMavenPlugin.runNoFork().recipes(RECIPES).onDir(baseDir);
		String out = result.capturedOutput();
		assertThat(result.success()).isTrue();
		assertTasksExecuted(out, "runNoFork");
		assertRecipesExecuted(out, RECIPES);
	}

	@Test
	@DisplayName("builder dryRun")
	void simpleProjectSimpleConfigDryRun() {
		Path baseDir = Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize();
		PluginInvocationResult result = RewriteMavenPlugin.dryRun().recipes(RECIPES).onDir(baseDir);
		String out = result.capturedOutput();
		assertThat(result.success()).isTrue();
		assertTasksExecuted(out, tasksOf(DEFAULT_GOALS, "dryRun"));
		assertRecipesExecuted(out, RECIPES);
	}

	@Test
	@DisplayName("builder dryRunNoFork")
	void simpleProjectSimpleConfigDryRunNoFork() {
		Path baseDir = Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize();
		PluginInvocationResult result = RewriteMavenPlugin.dryRunNoFork().recipes(RECIPES).onDir(baseDir);
		String out = result.capturedOutput();
		assertThat(result.success()).isTrue();
		assertTasksExecuted(out, "dryRunNoFork");
		assertRecipesExecuted(out, RECIPES);
	}

	@Test
	@DisplayName("builder discover")
	void simpleProjectSimpleConfigDiscover() {
		Path baseDir = Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize();
		PluginInvocationResult result = RewriteMavenPlugin.discover().recipes(RECIPES).onDir(baseDir);
		String out = result.capturedOutput();
		assertThat(result.success()).isTrue();
		assertTasksExecuted(out, "discover");
		assertThat(out).contains("[INFO] Available Recipes:");
	}

	@Test
	@DisplayName("builder cyclonedx")
	void simpleProjectSimpleConfigCyclonedx() {
		Path baseDir = Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize();
		PluginInvocationResult result = RewriteMavenPlugin.cyclonedx().recipes(RECIPES).onDir(baseDir);
		String out = result.capturedOutput();
		System.out.println(out);
		assertThat(result.success()).isTrue();
		assertTasksExecuted(out, "cyclonedx");
	}

	@Test
	@DisplayName("simple project full config")
	void simpleProjectFullConfig() {

		int port = TestSocketUtils.findAvailableTcpPort();
		Path baseDir = Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize();
		PluginInvocationResult result = RewriteMavenPlugin.run()
			.recipes(EXTENDED_RECIPES)
			.withDependencies(EXTENDED_REFCIPES_DEPS)
			.withDebugger(port, false)
			.withDebug()
			.withMemory("1G", "4G")
			.onDir(baseDir);

		String out = result.capturedOutput();
		assertThat(result.success()).isTrue();
		assertMemory(out, "1G", "4G");
		assertDebugConfig(out, port, false);
		assertTasksExecuted(out, tasksOf(DEFAULT_GOALS, "run"));
		assertRecipesExecuted(out, EXTENDED_RECIPES);
	}

	@Test
	@DisplayName("execute dryRun")
	void executeDryRun() {

		int port = TestSocketUtils.findAvailableTcpPort();
		Path baseDir = Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize();
		MavenInvocationResult result = RewriteMavenPlugin.execute(baseDir, true, DebugConfig.from(port, false),
				BuildConfig.builder().withMemory("1G", "4G").build(), RewriteMavenPlugin.Goal.DRY_RUN,
				Arrays.asList(RECIPES), List.of());
		String out = result.getCapturedLines();
		assertDebugConfig(out, port, false);
		assertMemory(out, "1G", "4G");
		assertTasksExecuted(out, "dryRun");
	}

	@Test
	@DisplayName("execute dryRunNoFork")
	void executeDryRunNoFork() {
		Path baseDir = Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize();
		verify(baseDir, RewriteMavenPlugin.Goal.DRY_RUN_NO_FORK, "dryRunNoFork");
	}

	@Test
	@DisplayName("execute run")
	void executeRun() {
		Path baseDir = Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize();
		verify(baseDir, RewriteMavenPlugin.Goal.RUN, "run");
	}

	@Test
	@DisplayName("execute runNoFork")
	void executeRunNoFork() {
		Path baseDir = Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize();
		verify(baseDir, RewriteMavenPlugin.Goal.RUN_NO_FORK, "runNoFork");
	}

	@Test
	@DisplayName("execute discover")
	void executeDiscover() {
		Path baseDir = Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize();
		MavenInvocationResult result = RewriteMavenPlugin.execute(baseDir, false, DebugConfig.disabled(),
				BuildConfig.fromDefault(), RewriteMavenPlugin.Goal.DISCOVER, Arrays.asList(EXTENDED_RECIPES),
				List.of(EXTENDED_REFCIPES_DEPS));
		String out = result.getCapturedLines();
		assertThat(result.getExitCode()).isEqualTo(0);
		assertTasksExecuted(out, "discover");
		assertThat(out).contains("[INFO] Available Recipes:");
	}

	@Test
	@DisplayName("execute cylonedx")
	void executeCyclonedx() {
		Path baseDir = Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize();
		MavenInvocationResult result = RewriteMavenPlugin.execute(baseDir, false, DebugConfig.disabled(),
				BuildConfig.fromDefault(), RewriteMavenPlugin.Goal.CYCLONEDX, Arrays.asList(EXTENDED_RECIPES),
				List.of(EXTENDED_REFCIPES_DEPS));
		String out = result.getCapturedLines();
		assertThat(result.getExitCode()).isEqualTo(0);
		assertTasksExecuted(out, "cyclonedx");
	}

	private void verify(Path baseDir, RewriteMavenPlugin.Goal runNoFork, String expectedGoal) {
		MavenInvocationResult result = RewriteMavenPlugin.execute(baseDir, false, DebugConfig.disabled(),
				BuildConfig.fromDefault(), runNoFork, Arrays.asList(EXTENDED_RECIPES), List.of(EXTENDED_REFCIPES_DEPS));
		String out = result.getCapturedLines();
		assertThat(result.getExitCode()).isEqualTo(0);
		assertRecipesExecuted(out, EXTENDED_RECIPES);
		assertTasksExecuted(out, expectedGoal);
	}

	private void assertTasksExecuted(String out, String... goals) {
		if (goals.length == 0) {
			fail("No goals provided");
		}
		Stream.of(goals).forEach(goal -> assertThat(out).contains(":" + goal + " "));
	}

	private String[] tasksOf(String[] defaultTasks, String rewriteTask) {
		List<String> tasksList = new ArrayList<>();
		tasksList.addAll(Arrays.asList(defaultTasks));
		tasksList.add(rewriteTask);
		return tasksList.toArray(String[]::new);
	}

	private void assertRecipesExecuted(String out, String... recipes) {
		assertThat(out)
			.contains("active recipe(s) [%s]".formatted(Stream.of(recipes).collect(Collectors.joining(", "))));
	}

	private void assertMemory(String out, String min, String max) {
		// Setting memory fails in GH Actions under Windows
		if (!System.getenv().containsKey("GITHUB_ACTIONS") && !System.getProperty("os.name").contains("Windows")) {
			assertThat(out).contains("env.MAVEN_OPTS= -Xms%s -Xmx%s".formatted(min, max));
		}
	}

	private void assertDebugConfig(String out, int port, boolean suspend) {
		assertThat(out).contains("Listening for transport dt_socket at address: %s".formatted(port),
				"-Xrunjdwp:transport=dt_socket,server=y,suspend=%s,address=%s".formatted(suspend ? "y" : "n", port));
	}

}