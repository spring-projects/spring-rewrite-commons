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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.rewrite.plugin.shared.BuildConfig;
import org.springframework.rewrite.plugin.shared.DebugConfig;
import org.springframework.rewrite.plugin.shared.PluginInvocationResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.rewrite.plugin.gradle.RewriteGradlePlugin.Task.*;

/**
 * @author Fabian Krüger
 */
public class RewriteGradlePlugin implements OpenRewriteGradlePluginBuilder.Recipes,
		OpenRewriteGradlePluginBuilder.FinalizingBuilder, OpenRewriteGradlePluginBuilder.PluginVersion {

	private static final Logger log = LoggerFactory.getLogger(RewriteGradlePlugin.class);

	private Task task;

	private List<String> dependencies = new ArrayList<>();

	private String pluginVersion;

	private List<String> recipes = new ArrayList<>();

	private DebugConfig debugConfig = DebugConfig.disabled();

	private String minMemory;

	private String maxMemory;

	private boolean debug = false;

	public static OpenRewriteGradlePluginBuilder.Recipes run() {
		return executeGradleRewritePlugin(RUN);
	}

	public static OpenRewriteGradlePluginBuilder.Recipes dryRun() {
		return executeGradleRewritePlugin(DRY_RUN);
	}

	public static OpenRewriteGradlePluginBuilder.Recipes discover() {
		return executeGradleRewritePlugin(DISCOVER);
	}

	private static OpenRewriteGradlePluginBuilder.Recipes executeGradleRewritePlugin(Task task) {
		RewriteGradlePlugin plugin = new RewriteGradlePlugin();
		plugin.task = task;
		return plugin;
	}

	@Override
	public OpenRewriteGradlePluginBuilder.PluginVersion recipes(String... recipeNames) {
		this.recipes = Arrays.asList(recipeNames);
		return this;
	}

	@Override
	public OpenRewriteGradlePluginBuilder.FinalizingBuilder withDebugger(int port, boolean suspend) {
		this.debugConfig = DebugConfig.from(port, suspend);
		return this;
	}

	@Override
	public OpenRewriteGradlePluginBuilder.FinalizingBuilder withDependencies(String... dependencies) {
		this.dependencies = Arrays.asList(dependencies);
		return this;
	}

	@Override
	public OpenRewriteGradlePluginBuilder.FinalizingBuilder withDebug() {
		this.debug = true;
		return this;
	}

	@Override
	public OpenRewriteGradlePluginBuilder.FinalizingBuilder withMemory(String minMemory, String maxMemory) {
		this.minMemory = minMemory;
		this.maxMemory = maxMemory;
		return this;
	}

	@Override
	public PluginInvocationResult onDir(Path baseDir) {
		baseDir = baseDir.toAbsolutePath().normalize();
		BuildConfig buildConfig = BuildConfig.defaultConfig();
		if (minMemory != null && !minMemory.isBlank()) {
			buildConfig = BuildConfig.builder().withMemory(minMemory, maxMemory).build();
		}
		GradleInvocationResult result = execute(baseDir, debug, debugConfig, buildConfig, task, dependencies,
				pluginVersion, recipes.toArray(String[]::new));
		boolean success = result.getError() == null || result.getError().isEmpty();
		return new PluginInvocationResult(success, result.getOutput());
	}

	@Override
	public OpenRewriteGradlePluginBuilder.FinalizingBuilder withDebugConfig(DebugConfig debugConfig) {
		this.debugConfig = debugConfig;
		return this;
	}

	/**
	 * Executes given OpenRewrite recipes using Gradle Tooling API.
	 * @param baseDir the dir getting parsed
	 * @param debug print debug output
	 * @param debugConfig set remote debug config to debug the recipe execution
	 */
	static GradleInvocationResult execute(Path baseDir, boolean debug, DebugConfig debugConfig, BuildConfig buildConfig,
			Task goal, List<String> dependencies, String pluginVersion, String... recipes) {

		try (TempGradleInitFile initFile = new TempGradleInitFile(baseDir, dependencies, pluginVersion)) {
			String initFileLocation = initFile.getPath().toString();

			List<String> args = new ArrayList<>();

			// provide init file location
			args.add("-I%s".formatted(initFileLocation));
			// the recipes
			args.add("-Drewrite.activeRecipe=%s".formatted(Stream.of(recipes).collect(Collectors.joining(","))));
			// debug outout
			if (debug) {
				args.add("-d");
			}
			// Remote debugging
			if (debugConfig.isDebugEnabled()) {
				args.add("-Dorg.gradle.logging.stacktrace=full");
				args.add("-Dorg.gradle.debug=true");
				args.add("-Dorg.gradle.debug.port=" + debugConfig.getPort());
				args.add("-Dorg.gradle.debug.suspend=" + debugConfig.isSuspend());
			}
			// JVM Memory
			List<String> jvmArgs = new ArrayList<>();
			if (buildConfig.hasMemorySettings()) {
				String min = buildConfig.getMemorySettings().getMin();
				String max = buildConfig.getMemorySettings().getMax();
				jvmArgs.add("-Xms%s -Xmx%s".formatted(min, max));
				String jvmArgsStr = jvmArgs.stream().collect(Collectors.joining(" "));
				args.add("-Dorg.gradle.jvmargs=%s".formatted(jvmArgsStr));
			}

			if (log.isDebugEnabled()) {
				log.debug("Command: ./gradlew %s %s".formatted(goal.getTask(),
						args.stream().collect(Collectors.joining(" "))));
				log.debug(initFileLocation + " \n" + initFile.getContent());
			}

			return GradleInvoker.runTasks(baseDir, args.toArray(String[]::new), goal.getTask());
		}
		finally {
			TempGradleInitFile.clear(baseDir);
		}
	}

	@Override
	public OpenRewriteGradlePluginBuilder.FinalizingBuilder usingPluginVersion(String version) {
		this.pluginVersion = version;
		return this;
	}

	enum Task {

		RUN("rewriteRun"), DRY_RUN("rewriteDryRun"), DISCOVER("rewriteDiscover");

		private final String task;

		Task(String task) {
			this.task = task;
		}

		public String getTask() {
			return task;
		}

	}

}
