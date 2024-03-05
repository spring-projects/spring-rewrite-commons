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
package org.springframework.rewrite.maven;

import org.apache.maven.shared.invoker.*;

import javax.swing.text.html.Option;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Executes OpenRewrite recipes by invoking local Maven installation. It requires
 * MAVEN_HOME or M2_HOME to be set!
 *
 * @author Fabian Krüger
 */
public class MavenInvocationRewriteRecipeLauncher {

	private MavenInvocationRequestFactory mavenInvocationRequestFactory = new MavenInvocationRequestFactory();

	// private Invoker invoker = new DefaultInvoker();

	public static Builders.OnDirBuilder applyRecipes(String... recipeNames) {
		MavenInvokerRewriteRecipeLauncherBuilder builders = new MavenInvokerRewriteRecipeLauncherBuilder();
		return builders.applyRecipes(recipeNames);
	}

	public static Builders.OnDirBuilder applyRecipes(List<String> recipeNames) {
		MavenInvokerRewriteRecipeLauncherBuilder builders = new MavenInvokerRewriteRecipeLauncherBuilder();
		return builders.applyRecipes(recipeNames);
	}

	private InvocationResult invokeRewritePlugin(Invoker invoker, Path baseDir, List<String> recipeNames,
			List<String> dependencies, DebugConfig debugConfig, BuildConfig buildConfig, Consumer<String> lineConsumer,
			RewritePluginGoals rewritePluginGoals) {
		validateBaseDir(baseDir);

		if (recipeNames.isEmpty()) {
			throw new IllegalArgumentException("No recipe names provided.");
		}

		String openRewriteCommand = renderOpenRewriteCommand(recipeNames, dependencies, rewritePluginGoals);
		return invokeMaven(invoker, baseDir, debugConfig, buildConfig, lineConsumer, openRewriteCommand);
	}

	public InvocationResult invokeRewritePlugin(Path baseDir, List<String> recipeNames, List<String> dependencies,
			DebugConfig debugConfig, BuildConfig buildConfig, Consumer<String> lineConsumer,
			RewritePluginGoals rewritePluginGoals) {

		validateBaseDir(baseDir);

		if (recipeNames.isEmpty()) {
			throw new IllegalArgumentException("No recipe names provided.");
		}

		String openRewriteCommand = renderOpenRewriteCommand(recipeNames, dependencies, rewritePluginGoals);
		return invokeMaven(new DefaultInvoker(), baseDir, debugConfig, buildConfig, lineConsumer, openRewriteCommand);
	}

	private void validateBaseDir(Path baseDir) {
		if (baseDir == null) {
			throw new IllegalArgumentException("Given baseDir was null.");
		}
		else if (!Files.exists(baseDir)) {
			throw new IllegalArgumentException("Given baseDir '%s' does not exist.".formatted(baseDir));
		}
	}

	private InvocationResult invokeMaven(Invoker invoker, Path baseDir, DebugConfig debugConfig,
			BuildConfig buildConfig, Consumer<String> lineConsumer, String openRewriteCommand) {
		List<String> goals = new ArrayList<>();
		goals.add("clean");
		goals.add("package");
		goals.add("--fail-at-end");
		goals.add(openRewriteCommand);

		InvocationRequest request = mavenInvocationRequestFactory.createMavenInvocationRequest(baseDir, debugConfig,
				buildConfig, goals, lineConsumer);

		try {
			return invoker.execute(request);
		}
		catch (MavenInvocationException e) {
			throw new RuntimeException(e);
		}
	}

	// public static InvocationResult invokeMaven(Path baseDir, BuildConfig buildConfig,
	// DebugConfig debugConfig,
	// Consumer<String> lineConsumer, List<String> providedGoals) {
	// List<String> goals = new ArrayList<>(providedGoals);
	//
	// InvocationRequest mavenInvocationRequest = new
	// MavenInvocationRequestFactory().createMavenInvocationRequest(baseDir, debugConfig,
	// buildConfig, providedGoals, lineConsumer);
	//
	// String mavenHome = System.getenv("MAVEN_HOME");
	// if (mavenHome == null) {
	// mavenHome = System.getenv("M2_HOME");
	// if (mavenHome == null) {
	// throw new IllegalStateException(
	// "MAVEN_HOME or M2_HOME must be set but System.getenv(\"MAVEN_HOME\") and
	// System.getenv(\"M2_HOME\") returned null.");
	// }
	// }
	//
	// if (buildConfig.isSkipTests()) {
	// goals.add("-DskipTests");
	// }
	//
	// Invoker invoker = new DefaultInvoker();
	// InvocationRequest request = new DefaultInvocationRequest();
	// request.setGoals(goals);
	// request.setBatchMode(true);
	// request.setMavenHome(new File(mavenHome));
	// request.setBaseDirectory(baseDir.toFile());
	// request.setOutputHandler(s -> lineConsumer.accept(s));
	//
	// StringBuilder mavenOpts = new StringBuilder();
	// mavenOpts.append("-Xms1G -Xmx6G ");
	// if (debugConfig != null && debugConfig.isDebugEnabled()) {
	// mavenOpts.append(" -Xdebug
	// -Xrunjdwp:transport=dt_socket,server=y,suspend=%s,address=%s "
	// .formatted(debugConfig.isSuspend(), debugConfig.getPort()));
	// }
	// request.setMavenOpts(mavenOpts.toString());
	//
	// try {
	// InvocationResult result = invoker.execute(request);
	// return result;
	// }
	// catch (MavenInvocationException e) {
	// throw new RuntimeException(e);
	// }
	// }

	private static String renderOpenRewriteCommand(List<String> recipeNames, List<String> dependencies,
			RewritePluginGoals rewritePluginGoal) {
		StringBuilder sb = new StringBuilder();
		sb.append("org.openrewrite.maven:rewrite-maven-plugin:").append(rewritePluginGoal.getGoalName()).append(" ");
		String recipesList = recipeNames.stream().collect(Collectors.joining(","));
		sb.append("-Drewrite.activeRecipes=").append(recipesList);
		if (!dependencies.isEmpty()) {
			String dependenciesList = dependencies.stream().collect(Collectors.joining(","));
			sb.append(" ").append("-Drewrite.recipeArtifactCoordinates=").append(dependenciesList);
		}
		return sb.toString();
	}

	public class Builders {

		public interface StartBuilder {

			OnDirBuilder applyRecipes(String... recipeNames);

			OnDirBuilder applyRecipes(List<String> recipeNames);

		}

		public interface OnDirBuilder {

			OptionalBuilder onDir(Path baseDir);

		}

		public interface OptionalBuilder {

			OptionalBuilder withDebugConfig(DebugConfig debugConfig);

			OptionalBuilder withDependencies(String... dependencyGavs);

			OptionalBuilder withDependencies(List<String> dependencyGavs);

			OptionalBuilder withBuildConfig(BuildConfig buildConfig);

			OptionalBuilder withOutputListener(Consumer<String> outputListener);

			OptionalBuilder withInvoker(Invoker invoker);

			InvocationResult run();

			InvocationResult runNoFork();

			InvocationResult dryRun();

			InvocationResult dryRunNoFork();

			InvocationResult discover();

			InvocationResult cyclonedx();

		}

	}

	public static class MavenInvokerRewriteRecipeLauncherBuilder
			implements Builders.StartBuilder, Builders.OnDirBuilder, Builders.OptionalBuilder {

		private Path baseDir;

		private List<String> recipes = new ArrayList<>();

		private List<String> dependencies = new ArrayList<>();

		private BuildConfig buildConfig = BuildConfig.defaultConfig();

		private DebugConfig debugConfig = DebugConfig.disabled();

		private Consumer<String> outputListener = line -> System.out.println(line);

		private Invoker invoker = new DefaultInvoker();

		public Builders.OptionalBuilder onDir(String baseDir) {
			this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
			return this;
		}

		@Override
		public Builders.OnDirBuilder applyRecipes(String... recipeNames) {
			validateRecipeNames(Arrays.asList(recipeNames));
			return this.applyRecipes(Arrays.asList(recipeNames));
		}

		@Override
		public Builders.OnDirBuilder applyRecipes(List<String> recipeNames) {
			validateRecipeNames(recipeNames);
			this.recipes = recipeNames;
			return this;
		}

		@Override
		public Builders.OptionalBuilder onDir(Path baseDir) {
			this.baseDir = baseDir.toAbsolutePath().normalize();
			return this;
		}

		@Override
		public Builders.OptionalBuilder withOutputListener(Consumer<String> outputListener) {
			this.outputListener = outputListener;
			return this;
		}

		@Override
		public Builders.OptionalBuilder withInvoker(Invoker invoker) {
			this.invoker = invoker;
			return this;
		}

		/**
		 * mvn rewrite:run - RunBuilder the configured recipes and apply the changes
		 * locally.
		 */
		@Override
		public InvocationResult run() {
			return executeRewriteGoal(invoker, baseDir, recipes, dependencies, debugConfig, buildConfig, outputListener,
					RewritePluginGoals.RUN);
		}

		/**
		 * mvn rewrite:runNoFork - RunBuilder the configured recipes and apply the changes
		 * locally. This variant does not fork the Maven life cycle and can be a more
		 * efficient choice when using Rewrite within a CI workflow when combined with
		 * other Maven goals.
		 */
		@Override
		public InvocationResult runNoFork() {
			return executeRewriteGoal(invoker, baseDir, recipes, dependencies, debugConfig, buildConfig, outputListener,
					RewritePluginGoals.RUN_NO_FORK);
		}

		/**
		 * mvn rewrite:dryRun - Generate warnings to the console for any recipe that would
		 * make changes and generates a diff file in each maven modules' target folder.
		 */
		@Override
		public InvocationResult dryRun() {
			return executeRewriteGoal(invoker, baseDir, recipes, dependencies, debugConfig, buildConfig, outputListener,
					RewritePluginGoals.DRY_RUN);
		}

		/**
		 * mvn rewrite:dryRunNoFork - Generate warnings to the console for any recipe that
		 * would make changes and generates a diff file in each maven modules' target
		 * folder. This variant does not fork the Maven life cycle and can be a more
		 * efficient choice when using Rewrite within a CI workflow when combined with
		 * other Maven goals.
		 */
		@Override
		public InvocationResult dryRunNoFork() {
			return executeRewriteGoal(invoker, baseDir, recipes, dependencies, debugConfig, buildConfig, outputListener,
					RewritePluginGoals.DRY_RUN_NO_FORK);
		}

		/**
		 * mvn rewrite:discover - Generate a report of available recipes found on the
		 * classpath.
		 */
		@Override
		public InvocationResult discover() {
			return executeRewriteGoal(invoker, baseDir, recipes, dependencies, debugConfig, buildConfig, outputListener,
					RewritePluginGoals.DISCOVER);
		}

		/**
		 * mvn rewrite:cyclonedx - Generate a CycloneDx bill of materials outlining the
		 * project's dependencies, including transitive dependencies.
		 *
		 * @see <a href="https://cyclonedx.org/">CycloneDx</a>
		 */
		@Override
		public InvocationResult cyclonedx() {
			return executeRewriteGoal(invoker, baseDir, recipes, dependencies, debugConfig, buildConfig, outputListener,
					RewritePluginGoals.CYCLONEDX);
		}

		private InvocationResult executeRewriteGoal(Invoker invoker, Path baseDir, List<String> recipeNames,
				List<String> dependencies, DebugConfig debugConfig, BuildConfig buildConfig,
				Consumer<String> lineConsumer, RewritePluginGoals rewritePluginGoals) {
			MavenInvocationRewriteRecipeLauncher launcher = new MavenInvocationRewriteRecipeLauncher();
			InvocationResult result = launcher.invokeRewritePlugin(invoker, baseDir, recipeNames, dependencies,
					debugConfig, buildConfig, lineConsumer, rewritePluginGoals);
			return result;
		}

		private void validateRecipeNames(List<String> recipeNames) {
			if (recipeNames.isEmpty()) {
				throw new IllegalArgumentException("At least one recipe name must be provided.");
			}

			if (recipeNames.stream().anyMatch(String::isBlank)) {
				throw new IllegalArgumentException("At least one recipe name was blank.");
			}
		}

		@Override
		public Builders.OptionalBuilder withDependencies(String... dependencyGavs) {
			return withDependencies(Arrays.asList(dependencyGavs));
		}

		@Override
		public Builders.OptionalBuilder withDependencies(List<String> dependencyGavs) {
			this.dependencies = dependencyGavs;
			return this;
		}

		@Override
		public Builders.OptionalBuilder withBuildConfig(BuildConfig buildConfig) {
			this.buildConfig = buildConfig;
			return this;
		}

		@Override
		public Builders.OptionalBuilder withDebugConfig(DebugConfig debugConfig) {
			this.debugConfig = debugConfig;
			return this;
		}

	}

}
