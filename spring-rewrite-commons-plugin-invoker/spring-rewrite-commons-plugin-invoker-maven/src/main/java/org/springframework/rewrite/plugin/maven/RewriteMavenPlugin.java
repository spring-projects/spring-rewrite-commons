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

import org.springframework.rewrite.plugin.shared.BuildConfig;
import org.springframework.rewrite.plugin.shared.DebugConfig;
import org.springframework.rewrite.plugin.shared.PluginInvocationResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.rewrite.plugin.maven.RewriteMavenPlugin.Goal.*;

public class RewriteMavenPlugin
		implements RewriteMavenPluginBuilder.FinalizingBuilder, RewriteMavenPluginBuilder.Recipes {

	private Goal goal;

	private DebugConfig debugConfig;

	private List<String> recipes = new ArrayList<>();

	private String minMemory;

	private String maxMemory;

	private MavenInvocationRequestFactory factory = new MavenInvocationRequestFactory();

	private List<String> dependencies = new ArrayList<>();

	private boolean debug;

	@Override
	public RewriteMavenPluginBuilder.FinalizingBuilder withDependencies(String... dependencies) {
		this.dependencies = Arrays.asList(dependencies);
		return this;
	}

	public static RewriteMavenPluginBuilder.Recipes run() {
		Goal run = RUN;
		return executeMavenRewritePlugin(run);
	}

	public static RewriteMavenPluginBuilder.Recipes runNoFork() {
		Goal run = RUN_NO_FORK;
		return executeMavenRewritePlugin(run);
	}

	public static RewriteMavenPluginBuilder.Recipes dryRun() {
		Goal run = DRY_RUN;
		return executeMavenRewritePlugin(run);
	}

	public static RewriteMavenPluginBuilder.Recipes dryRunNoFork() {
		Goal run = DRY_RUN_NO_FORK;
		return executeMavenRewritePlugin(run);
	}

	public static RewriteMavenPluginBuilder.Recipes discover() {
		Goal run = DISCOVER;
		return executeMavenRewritePlugin(run);
	}

	public static RewriteMavenPluginBuilder.Recipes cyclonedx() {
		Goal run = CYCLONEDX;
		return executeMavenRewritePlugin(run);
	}

	private static RewriteMavenPlugin executeMavenRewritePlugin(Goal run) {
		RewriteMavenPlugin rewriteMavenPlugin = new RewriteMavenPlugin();
		rewriteMavenPlugin.goal = run;
		return rewriteMavenPlugin;
	}

	@Override
	public RewriteMavenPluginBuilder.FinalizingBuilder recipes(String... recipeNames) {
		this.recipes = Arrays.asList(recipeNames);
		return this;
	}

	@Override
	public RewriteMavenPluginBuilder.FinalizingBuilder withDebugger(int port, boolean suspend) {
		debugConfig = DebugConfig.from(port, suspend);
		return this;
	}

	@Override
	public RewriteMavenPluginBuilder.FinalizingBuilder withDebug() {
		this.debug = true;
		return this;
	}

	@Override
	public PluginInvocationResult onDir(Path baseDir) {
		BuildConfig.Builder builder = BuildConfig.builder();
		if (minMemory != null) {
			builder.withMemory(minMemory, maxMemory);
		}
		MavenInvocationResult result = execute(baseDir, debug, debugConfig, builder.build(), goal, recipes,
				dependencies);

		return new PluginInvocationResult(result.getExitCode() != 0 ? false : true, result.getCapturedLines());
	}

	static MavenInvocationResult execute(Path baseDir, boolean debug, DebugConfig debugConfig, BuildConfig buildConfig,
			Goal openRewriteGoal, List<String> recipes1, List<String> dependencies1) {
		String openRewriteCommand = renderOpenRewriteCommand(recipes1, dependencies1, openRewriteGoal);
		List<String> goals = List.of("--fail-at-end", openRewriteCommand);
		return MavenInvoker.runGoals(baseDir, debugConfig, debug, buildConfig, goals);
	}

	private static String renderOpenRewriteCommand(List<String> recipeNames, List<String> dependencies,
			Goal rewritePluginGoal) {
		StringBuilder sb = new StringBuilder();
		sb.append("org.openrewrite.maven:rewrite-maven-plugin:").append(rewritePluginGoal.getMaven()).append(" ");
		String recipesList = recipeNames.stream().collect(Collectors.joining(","));
		sb.append("-Drewrite.activeRecipes=").append(recipesList);
		if (!dependencies.isEmpty()) {
			String dependenciesList = dependencies.stream().collect(Collectors.joining(","));
			sb.append(" ").append("-Drewrite.recipeArtifactCoordinates=").append(dependenciesList);
		}
		return sb.toString();
	}

	@Override
	public RewriteMavenPluginBuilder.FinalizingBuilder withMemory(String minMemory, String maxMemory) {
		this.minMemory = minMemory;
		this.maxMemory = maxMemory;
		return this;
	}

	public enum Goal {

		RUN("run"), RUN_NO_FORK("runNoFork"), DRY_RUN("dryRun"), DRY_RUN_NO_FORK("dryRunNoFork"), DISCOVER("discover"),
		CYCLONEDX("cyclonedx");

		private final String maven;

		Goal(String maven) {
			this.maven = maven;
		}

		public String getMaven() {
			return maven;
		}

	}

}
