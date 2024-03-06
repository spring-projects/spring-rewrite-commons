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
package org.springframework.rewrite.plugin.gradle;

import org.junit.jupiter.api.DisplayName;
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

/**
 * @author Fabian Krüger
 */
public class RewriteGradlePluginTest {

	public static final String[] RECIPES = { "org.openrewrite.java.RemoveUnusedImports",
			"org.openrewrite.java.format.NormalizeLineBreaks" };

	public static final String[] EXTENDED_RECIPES = { "org.openrewrite.java.RemoveUnusedImports",
			"org.openrewrite.java.format.NormalizeLineBreaks",
			"org.openrewrite.java.migrate.jakarta.MaybeAddJakartaServletApi" };

	public static final String[] DEFAULT_TASKS = new String[] { "compileJava", "processResources", "classes",
			"compileTestJava", "rewriteResolveDependencies" };

	public static final String EXTENDED_RECIPES_DEPS = "org.openrewrite.recipe:rewrite-migrate-java:2.9.0";

	@Test
	@DisplayName("simple project minimal setup")
	void simpleProjectMinimalSetup() {
		Path baseDir = Path.of("./testcode/gradle-projects/simple");
		PluginInvocationResult result = RewriteGradlePlugin.dryRun()
			.recipes(RECIPES)
			.usingPluginVersion("6.10.0")
			.onDir(baseDir);

		String out = result.capturedOutput();
		assertThat(result.success()).isTrue();
		assertSuccessfulBuild(out);
		assertTasksExecuted(out, DEFAULT_TASKS, "rewriteDryRun");
		assertRecipesExecuted(out, RECIPES);
	}

	@Test
	@DisplayName("simple project skip tests")
	void simpleProjectSkipTests() {
		Path baseDir = Path.of("./testcode/gradle-projects/simple");
		PluginInvocationResult result = RewriteGradlePlugin.run()
			.recipes(RECIPES)
			.usingPluginVersion("6.10.0")
			.withMemory("256M", "1G")
			.onDir(baseDir);

		String out = result.capturedOutput();
		assertThat(result.success()).isTrue();
		assertSuccessfulBuild(out);
		assertTasksExecuted(out, DEFAULT_TASKS, "rewriteRun");
		assertRecipesExecuted(out, RECIPES);
	}

	@Test
	@DisplayName("simple project full config")
	void simpleProjectFullConfig() {
		Path baseDir = Path.of("./testcode/gradle-projects/simple");

		int port = TestSocketUtils.findAvailableTcpPort();

		PluginInvocationResult result = RewriteGradlePlugin.run()
			.recipes(EXTENDED_RECIPES)
			.usingPluginVersion("6.10.0")
			.withDebugger(port, false)
			.withDependencies(EXTENDED_RECIPES_DEPS)
			.withDebug()
			.withMemory("64M", "484M")
			.onDir(baseDir);

		assertThat(result.success()).isTrue();
		String out = result.capturedOutput();
		assertMemory(out, "64M", "484M");
		assertDebugConfig(out, port, false);
		assertRecipesExecuted(out, EXTENDED_RECIPES);
		assertTasksExecuted(out, DEFAULT_TASKS, "rewriteRun");
	}

	private void assertDebugConfig(String out, int port, boolean suspend) {
		assertThat(out).contains("-agentlib:jdwp=transport=dt_socket,server=y,suspend=%s,address=%s"
			.formatted(suspend ? "y" : "n", port));
	}

	private void assertMemory(String out, String min, String max) {
		assertThat(out).contains("-Xms%s".formatted(min));
		assertThat(out).contains("-Xmx%s".formatted(max));
	}

	private void assertSuccessfulBuild(String out) {
		assertThat(out).contains("BUILD SUCCESSFUL in");
	}

	private void assertTasksExecuted(String out, String[] defaultTasks, String rewriteTask) {
		String[] tasks = tasksOf(defaultTasks, rewriteTask);
		List<String> tasksOutput = Stream.of(tasks).map(t -> "Task :" + t).toList();
		assertThat(out).contains(tasksOutput);
	}

	private void assertRecipesExecuted(String out, String[] recipes) {
		assertThat(out).containsIgnoringWhitespaces(
				"All sources parsed, running active recipes: " + Stream.of(recipes).collect(Collectors.joining(",")));
	}

	private String[] tasksOf(String[] defaultTasks, String[] testTasks, String rewriteTask) {
		List<String> tasksList = new ArrayList<>();
		tasksList.addAll(Arrays.asList(defaultTasks));
		if (testTasks != null) {
			tasksList.addAll(Arrays.asList(testTasks));
		}
		tasksList.add(rewriteTask);
		return tasksList.toArray(String[]::new);
	}

	private String[] tasksOf(String[] defaultTasks, String rewriteTask) {
		return tasksOf(defaultTasks, null, rewriteTask);
	}

}
