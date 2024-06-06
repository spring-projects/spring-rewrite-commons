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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.rewrite.plugin.gradle.RewriteGradlePlugin;
import org.springframework.rewrite.plugin.gradle.OpenRewriteGradlePluginBuilder;
import org.springframework.rewrite.plugin.maven.RewriteMavenPlugin;
import org.springframework.rewrite.plugin.maven.RewriteMavenPluginBuilder;
import org.springframework.rewrite.plugin.shared.DebugConfig;
import org.springframework.rewrite.plugin.shared.PluginInvocationResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Fabian Krüger
 */
public class RewritePlugin implements OpenRewritePluginBuilder.GradlePluginVersion, OpenRewritePluginBuilder.Recipes,
		OpenRewritePluginBuilder.FinalizingBuilder {

	private static final Logger log = LoggerFactory.getLogger(RewriteGradlePlugin.class);

	private Action action;

	private String gradlePluginVersion;

	private String mavenPluginVersion;

	private List<String> recipes = new ArrayList<>();

	private boolean debug;

	private DebugConfig debugConfig = DebugConfig.disabled();

	private List<String> dependencies = new ArrayList<>();

	private boolean preferGradle = false;

	private String minMemory;

	private String maxMemory;

	public static OpenRewritePluginBuilder.GradlePluginVersion dryRun() {
		RewritePlugin plugin = new RewritePlugin();
		plugin.action = Action.DRY_RUN;
		return plugin;
	}

	public static OpenRewritePluginBuilder.GradlePluginVersion run() {
		RewritePlugin plugin = new RewritePlugin();
		plugin.action = Action.RUN;
		return plugin;
	}

	public static OpenRewritePluginBuilder.GradlePluginVersion discover() {
		RewritePlugin plugin = new RewritePlugin();
		plugin.action = Action.DISCOVER;
		return plugin;
	}

	@Override
	public OpenRewritePluginBuilder.Recipes gradlePluginVersion(String pluginVersion) {
		this.gradlePluginVersion = pluginVersion;
		return this;
	}

	@Override
	public OpenRewritePluginBuilder.GradlePluginVersion mavenPluginVersion(String mavenPluginVersion) {
		this.mavenPluginVersion = mavenPluginVersion;
		return this;
	}

	@Override
	public OpenRewritePluginBuilder.FinalizingBuilder recipes(String... recipes) {
		this.recipes = Arrays.asList(recipes);
		return this;
	}

	@Override
	public PluginInvocationResult onDir(Path baseDir) {
		PluginInvocationResult result;
		boolean mavenProject = isMavenProject(baseDir);
		boolean gradleProject = isGradleProject(baseDir);

		if (gradleProject && mavenProject && preferGradle) {
			result = executeGradle(baseDir);
		}
		else if (mavenProject) {
			result = executeMaven(baseDir);
		}
		else if (gradleProject) {
			result = executeGradle(baseDir);
		}
		else {
			throw new IllegalArgumentException(
					"Neither pom.xml, build.gradle, nor build.gradle.kts was found in '%s'".formatted(baseDir));
		}
		return new PluginInvocationResult(true, result.capturedOutput());
	}

	private boolean isPolyglotProject(Path baseDir) {
		return isMavenProject(baseDir) && isGradleProject(baseDir);
	}

	private PluginInvocationResult executeGradle(Path baseDir) {
		PluginInvocationResult result;
		OpenRewriteGradlePluginBuilder.Recipes builder;
		switch (action) {
			case DRY_RUN -> builder = RewriteGradlePlugin.dryRun();
			case DISCOVER -> builder = RewriteGradlePlugin.discover();
			default -> builder = RewriteGradlePlugin.run();
		}
		OpenRewriteGradlePluginBuilder.FinalizingBuilder finalizingBuilder = builder
			.recipes(recipes.toArray(String[]::new))
			.usingPluginVersion(gradlePluginVersion);

		if (minMemory != null) {
			finalizingBuilder = finalizingBuilder.withMemory(minMemory, maxMemory);
		}

		if (debug) {
			finalizingBuilder = finalizingBuilder.withDebug();
		}

		if (!dependencies.isEmpty()) {
			finalizingBuilder.withDependencies(this.dependencies.toArray(String[]::new));
		}

		finalizingBuilder = finalizingBuilder.withDebugConfig(debugConfig);

		result = finalizingBuilder.onDir(baseDir);
		return result;
	}

	private PluginInvocationResult executeMaven(Path baseDir) {
		PluginInvocationResult result;
		RewriteMavenPluginBuilder.Recipes builder;
		switch (action) {
			case DRY_RUN -> builder = RewriteMavenPlugin.dryRun();
			case DISCOVER -> builder = RewriteMavenPlugin.discover();
			default -> builder = RewriteMavenPlugin.run();
		}
		RewriteMavenPluginBuilder.FinalizingBuilder finalizingBuilder = builder
			.recipes(this.recipes.toArray(String[]::new))
			.withMavenPluginVersion("5.32.1");
		if (minMemory != null) {
			finalizingBuilder = finalizingBuilder.withMemory(minMemory, maxMemory);
		}
		if (debug) {
			finalizingBuilder = finalizingBuilder.withDebug();
		}
		if (debugConfig != null && debugConfig.isDebugEnabled()) {
			finalizingBuilder = finalizingBuilder.withDebugger(debugConfig.getPort(), debugConfig.isSuspend());
		}
		if (!dependencies.isEmpty()) {
			finalizingBuilder.withDependencies(dependencies.toArray(String[]::new));
		}
		result = finalizingBuilder.onDir(baseDir);
		return result;
	}

	private boolean isGradleProject(Path baseDir) {
		return Files.exists(baseDir.resolve("build.gradle")) || Files.exists(baseDir.resolve("build.gradle.kts"));
	}

	private boolean isMavenProject(Path baseDir) {
		return Files.exists(baseDir.resolve("pom.xml"));
	}

	@Override
	public OpenRewritePluginBuilder.FinalizingBuilder dependencies(String... dependencies) {
		this.dependencies = Arrays.asList(dependencies);
		return this;
	}

	@Override
	public OpenRewritePluginBuilder.FinalizingBuilder withMemory(String minMemory, String maxMemory) {
		this.minMemory = minMemory;
		this.maxMemory = maxMemory;
		return this;
	}

	@Override
	public OpenRewritePluginBuilder.FinalizingBuilder withDebugging() {
		return this;
	}

	@Override
	public OpenRewritePluginBuilder.FinalizingBuilder withDebugging(int port, boolean suspend) {
		this.debugConfig = DebugConfig.from(port, suspend);
		return this;
	}

	@Override
	public OpenRewritePluginBuilder.FinalizingBuilder withDebug() {
		this.debug = true;
		return this;
	}

	@Override
	public OpenRewritePluginBuilder.FinalizingBuilder preferGradle() {
		this.preferGradle = true;
		return this;
	}

	public enum Action {

		RUN, DRY_RUN, DISCOVER

	}

}

class OpenRewritePluginBuilder {

	public interface GradlePluginVersion {

		Recipes gradlePluginVersion(String pluginVersion);

		GradlePluginVersion mavenPluginVersion(String s);

	}

	public interface Recipes {

		OpenRewritePluginBuilder.FinalizingBuilder recipes(String... recipes);

	}

	public interface FinalizingBuilder {

		PluginInvocationResult onDir(Path baseDir);

		FinalizingBuilder dependencies(String... dependencies);

		FinalizingBuilder withMemory(String min, String max);

		FinalizingBuilder withDebugging();

		FinalizingBuilder withDebugging(int port, boolean suspend);

		FinalizingBuilder withDebug();

		FinalizingBuilder preferGradle();

	}

}
