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
package org.springframework.rewrite.plugin.polyglot;

import org.apache.commons.lang3.stream.Streams;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
public class RewritePluginTest {

	public static final String[] RECIPES = { "org.openrewrite.java.RemoveUnusedImports",
			"org.openrewrite.java.format.NormalizeLineBreaks" };

	public static final String[] EXTENDED_RECIPES = Stream
		.concat(Stream.of(RECIPES), Stream.of("org.openrewrite.java.migrate.jakarta.MaybeAddJakartaServletApi"))
		.toList()
		.toArray(String[]::new);

	public static final String EXTENDED_RECIPES_DEPS = "org.openrewrite.recipe:rewrite-migrate-java:2.9.0";

	@Nested
	class PolyglotTests {

		@Test
		@DisplayName("should prefer Maven over Gradle by default")
		void shouldPreferMavenOverGradle() {
			Path baseDir = Path.of("./testcode/polyglot-projects/simple-polyglot").toAbsolutePath().normalize();
			PluginInvocationResult result = RewritePlugin.run()
				.gradlePluginVersion("6.10.0")
				.recipes(RECIPES)
				.onDir(baseDir);

			assertThat(result.capturedOutput()).contains("[INFO] >>> rewrite-maven-plugin:");
		}

		@Test
		@DisplayName("should prefer Gradle over Maven with flag")
		void shouldPreferGradleOverMavenWithFlag() {
			Path baseDir = Path.of("./testcode/polyglot-projects/simple-polyglot").toAbsolutePath().normalize();
			PluginInvocationResult result = RewritePlugin.run()
				.gradlePluginVersion("6.10.0")
				.recipes(RECIPES)
				.preferGradle()
				.onDir(baseDir);

			assertThat(result.capturedOutput()).contains("> Task :rewriteRun");
		}

	}

	@Nested
	class MavenTests {

		public static final String[] DEFAULT_GOALS = new String[] { "resources", "compile", "testResources",
				"testCompile" };

		@Test
		@DisplayName("simple Maven run")
		void simpleMavenRun() {
			Path baseDir = Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize();
			verify(RewritePlugin.run(), baseDir, "run");
		}

		@Test
		@DisplayName("simple Maven dryRun")
		void simpleMavenDryRun() {
			Path baseDir = Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize();
			verify(RewritePlugin.dryRun(), baseDir, "dryRun");
		}

		@Test
		@DisplayName("simple Maven discover")
		void simpleMavenDiscover() {
			Path baseDir = Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize();
			PluginInvocationResult result = RewritePlugin.discover()
				.gradlePluginVersion("1.2.3")
				.recipes(RECIPES)
				.onDir(baseDir);

			assertBuildSuccessful(result);
			String out = result.capturedOutput();
			assertGoalsExecuted(out, "discover");
			assertThat(out).contains("[INFO] Available Recipes:");
		}

		@Test
		@DisplayName("simple Maven full api")
		void simpleMavenFullApi() {
			int port = TestSocketUtils.findAvailableTcpPort();
			Path baseDir = Path.of("./testcode/maven-projects/simple").toAbsolutePath().normalize();
			PluginInvocationResult result = RewritePlugin.run()
				.gradlePluginVersion("6.10.0")
				.recipes(EXTENDED_RECIPES)
				.dependencies(EXTENDED_RECIPES_DEPS)
				.withMemory("18M", "280M")
				.withDebug()
				.withDebugging(port, false)
				.onDir(baseDir);
			assertBuildSuccessful(result);
			String out = result.capturedOutput();
			assertGoalsExecuted(out, DEFAULT_GOALS, "run");
			assertMemory(out, "18M", "280M");
			assertDebugConfig(out, port, false);
			assertRecipesExecuted(out, EXTENDED_RECIPES);
		}

		private void verify(OpenRewritePluginBuilder.GradlePluginVersion rewriteGoalExecution, Path baseDir,
				String rewriteGoal) {
			PluginInvocationResult result = rewriteGoalExecution.gradlePluginVersion("1.2.3")
				.recipes(RECIPES)
				.onDir(baseDir);

			assertBuildSuccessful(result);
			String out = result.capturedOutput();
			assertGoalsExecuted(out, DEFAULT_GOALS, rewriteGoal);
			assertRecipesExecuted(out, RECIPES);
		}

		private void assertMemory(String out, String min, String max) {
			// Setting memory fails in GH Actions under Windows
			if (!System.getenv().containsKey("GITHUB_ACTIONS") && !System.getProperty("os.name").contains("Windows")) {
				assertThat(out).contains("env.MAVEN_OPTS= -Xms%s -Xmx%s".formatted(min, max));
			}
		}

		private void assertDebugConfig(String out, int port, boolean suspend) {
			assertThat(out).contains("Listening for transport dt_socket at address: %s".formatted(port),
					"-Xrunjdwp:transport=dt_socket,server=y,suspend=%s,address=%s".formatted(suspend ? "y" : "n",
							port));
		}

		private void assertBuildSuccessful(PluginInvocationResult result) {
			assertThat(result.success()).isTrue();
		}

		private void assertGoalsExecuted(String out, String... goals) {
			if (goals.length == 0) {
				fail("No goals provided");
			}
			Stream.of(goals).forEach(goal -> assertThat(out).contains(":" + goal + " "));
		}

		private void assertGoalsExecuted(String out, String[] defaultTasks, String rewriteTask) {
			String[] tasks = tasksOf(defaultTasks, rewriteTask);
			Streams.of(tasks).forEach(t -> assertThat(out).contains(t));
		}

		private void assertRecipesExecuted(String out, String... recipes) {
			assertThat(out)
				.contains("active recipe(s) [%s]".formatted(Stream.of(recipes).collect(Collectors.joining(", "))));
		}

	}

	@Nested
	class GradleTests {

		public static final Path BASE_DIR = Path.of("./testcode/gradle-projects/simple").toAbsolutePath().normalize();

		public static final String[] DEFAULT_TASKS = new String[] { "compileJava", "processResources", "classes",
				"compileTestJava", "rewriteResolveDependencies" };

		@Test
		@DisplayName("simple Gradle run")
		void simpleGradleRun() {
			verify(RewritePlugin.run(), "rewriteRun");
		}

		@Test
		@DisplayName("simple Gradle dryRun")
		void simpleGradleDryRun() {
			verify(RewritePlugin.dryRun(), "rewriteDryRun");
		}

		@Test
		@DisplayName("simple Gradle discover")
		void simpleGradleDiscover() {
			PluginInvocationResult result = RewritePlugin.discover()
				.gradlePluginVersion("6.10.0")
				.recipes(RECIPES)
				.onDir(BASE_DIR);

			String out = result.capturedOutput();
			assertSuccessfulBuild(out);
			assertTasksExecuted(out, "rewriteDiscover");
			assertThat(out).contains("Available Recipes:");
		}

		@Test
		@DisplayName("simple Gradle full API")
		void simpleGradleFullApi() {
			int port = TestSocketUtils.findAvailableTcpPort();
			Path baseDir = Path.of("./testcode/gradle-projects/simple").toAbsolutePath().normalize();
			PluginInvocationResult result = RewritePlugin.run()
				.gradlePluginVersion("6.10.0")
				.recipes(EXTENDED_RECIPES)
				.dependencies(EXTENDED_RECIPES_DEPS)
				.withDebug()
				.withMemory("64M", "484M")
				.withDebugging(port, false)
				.onDir(baseDir);

			assertThat(result.success()).isTrue();
			String out = result.capturedOutput();
			assertSuccessfulBuild(out);
			assertMemory(out, "64M", "484M");
			assertDebugConfig(out, port, false);
			assertTasksExecuted(out, DEFAULT_TASKS, "rewriteRun");
			assertRecipesExecuted(out, EXTENDED_RECIPES);
		}

		private void verify(OpenRewritePluginBuilder.GradlePluginVersion run, String run1) {
			PluginInvocationResult result = run.gradlePluginVersion("6.10.0").recipes(RECIPES).onDir(BASE_DIR);

			String out = result.capturedOutput();
			assertSuccessfulBuild(out);
			assertTasksExecuted(out, DEFAULT_TASKS, run1);
			assertRecipesExecuted(out, RECIPES);
		}

		private void assertMemory(String out, String min, String max) {
			assertThat(out).contains("-Xms%s".formatted(min));
			assertThat(out).contains("-Xmx%s".formatted(max));
		}

		private void assertDebugConfig(String out, int port, boolean suspend) {
			assertThat(out).contains("-agentlib:jdwp=transport=dt_socket,server=y,suspend=%s,address=%s"
				.formatted(suspend ? "y" : "n", port));
		}

		private void assertSuccessfulBuild(String out) {
			assertThat(out).contains("BUILD SUCCESSFUL in");
		}

		private void assertTasksExecuted(String out, String[] defaultTasks, String rewriteTask) {
			String[] tasks = tasksOf(defaultTasks, rewriteTask);
			assertTasksExecuted(out, tasks);
		}

		private static void assertTasksExecuted(String out, String... tasks) {
			List<String> tasksOutput = Stream.of(tasks).map(t -> "Task :" + t).toList();
			assertThat(out).contains(tasksOutput);
		}

		private void assertRecipesExecuted(String out, String[] recipes) {
			assertThat(out).containsIgnoringWhitespaces("All sources parsed, running active recipes: "
					+ Stream.of(recipes).collect(Collectors.joining(",")));
		}

	}

	private String[] tasksOf(String[] defaultTasks, String rewriteTask) {
		List<String> tasksList = new ArrayList<>();
		tasksList.addAll(Arrays.asList(defaultTasks));
		tasksList.add(rewriteTask);
		return tasksList.toArray(String[]::new);
	}

}
