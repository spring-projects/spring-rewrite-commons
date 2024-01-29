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
package org.springframework.rewrite;

import org.jetbrains.annotations.NotNull;
import org.openrewrite.Recipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.rewrite.parser.RewriteProjectParsingResult;
import org.springframework.rewrite.resource.ProjectResourceSet;
import org.springframework.rewrite.resource.ProjectResourceSetFactory;
import org.springframework.rewrite.resource.ProjectResourceSetSerializer;
import org.springframework.util.StopWatch;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Helps to apply recipes.
 *
 * @author Fabian Krüger
 */
public class RewriteRecipeLauncher {

	private final RewriteProjectParser parser;

	private final RewriteRecipeDiscovery discovery;

	private final ProjectResourceSetFactory resourceSetFactory;

	private final ProjectResourceSetSerializer serializer;

	/**
	 * Listener API for progress events/messages
	 */
	public interface RewriteRecipeRunnerProgressListener {

		void progress(String progressMessage);

	}

	public RewriteRecipeLauncher(RewriteProjectParser parser, RewriteRecipeDiscovery discovery,
			ProjectResourceSetFactory resourceSetFactory, ProjectResourceSetSerializer serializer) {
		this.parser = parser;
		this.discovery = discovery;
		this.resourceSetFactory = resourceSetFactory;
		this.serializer = serializer;
	}

	/**
	 * Apply the recipe with {@code recipeName} to the project under {@code path}.
	 * @throws IllegalStateException when the recipe couldn't be discovered
	 */
	public void run(String recipeName, String path) {
		run(recipeName, path, __ -> {
		});
	}

	/**
	 * Apply the recipe with {@code recipeName} to the project under {@code path}.
	 * @throws IllegalStateException when the recipe couldn't be discovered
	 */
	public void run(String recipeName, String path, RewriteRecipeRunnerProgressListener listener) {
		Optional<Recipe> recipe = discoverRecipe(recipeName);
		if (recipe.isEmpty()) {
			throw new IllegalStateException("Could not find recipe " + recipeName + ".");
		}
		run(recipe.get(), path, listener);
	}

	/**
	 * Apply the {@link Recipe} to the project under {@code path}.
	 */
	public void run(Recipe recipe, String path) {
		run(recipe, path, __ -> {
		});
	}

	/**
	 * Apply the {@link Recipe} to the project under {@code path}.
	 */
	public void run(Recipe recipe, String path, RewriteRecipeRunnerProgressListener listener) {
		Path baseDir = getBaseDir(path);
		RewriteProjectParsingResult parsingResult = parseProject(baseDir, listener);
		applyRecipe(baseDir, parsingResult, recipe, listener);
	}

	@NotNull
	private RewriteProjectParsingResult parseProject(Path baseDir, RewriteRecipeRunnerProgressListener listener) {
		listener.progress("Start parsing dir '%s'".formatted(baseDir));
		StopWatch stopWatch = new StopWatch("parse");
		stopWatch.start();
		RewriteProjectParsingResult parsingResult = parser.parse(baseDir);
		stopWatch.stop();
		double parseTime = stopWatch.getTotalTime(TimeUnit.SECONDS);
		listener.progress("Parsed %d resources in %f sec.".formatted(parsingResult.sourceFiles().size(), parseTime));
		return parsingResult;
	}

	private void applyRecipe(Path baseDir, RewriteProjectParsingResult parsingResult, Recipe recipe,
			RewriteRecipeRunnerProgressListener listener) {
		Object recipeName = recipe.getName();
		StopWatch stopWatch = new StopWatch("parse");
		stopWatch.start();
		// Use ProjectResourceSet abstraction
		ProjectResourceSet projectResourceSet = resourceSetFactory.create(baseDir, parsingResult.sourceFiles());
		// To apply recipes
		listener.progress("Applying recipe %s, this may take a few minutes.".formatted(recipeName));
		projectResourceSet.apply(recipe);
		stopWatch.stop();
		double recipeRunTime = stopWatch.getTotalTime(TimeUnit.MINUTES);
		listener.progress("Applied recipe %s in %f min.".formatted(recipeName, recipeRunTime));
		// Synchronize changes with filesystem
		listener.progress("Write changes from %s.".formatted(recipeName));
		serializer.writeChanges(projectResourceSet);
	}

	@NotNull
	private Optional<Recipe> discoverRecipe(String recipeName) {
		// discover recipe
		List<Recipe> recipes = discovery.discoverRecipes();
		Optional<Recipe> recipe = recipes.stream().filter(r -> recipeName.equals(r.getName())).findFirst();
		return recipe;
	}

	@NotNull
	private static Path getBaseDir(String path) {
		Path baseDir = Path.of(".").toAbsolutePath().normalize();
		if (path != null) {
			baseDir = Path.of(path).toAbsolutePath().normalize();
		}
		return baseDir;
	}

}
