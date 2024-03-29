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
import org.openrewrite.Validated;
import org.openrewrite.config.ClasspathScanningLoader;
import org.openrewrite.config.Environment;
import org.openrewrite.config.RecipeDescriptor;
import org.openrewrite.config.ResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.rewrite.parser.RecipeValidationErrorException;
import org.springframework.rewrite.parser.SpringRewriteProperties;

import java.util.*;
import java.util.function.Predicate;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

/**
 * <p>
 * RewriteRecipeDiscovery class.
 * </p>
 *
 * @author Fabian Krüger
 */
public class RewriteRecipeDiscovery {

	private static final Logger LOGGER = LoggerFactory.getLogger(RewriteRecipeDiscovery.class);

	private final SpringRewriteProperties springRewriteProperties;

	public RewriteRecipeDiscovery(SpringRewriteProperties springRewriteProperties) {
		this.springRewriteProperties = springRewriteProperties;
	}

	/**
	 *
	 */
	public List<Recipe> discoverRecipes() {
		ClasspathScanningLoader resourceLoader = new ClasspathScanningLoader(new Properties(), new String[] {});
		return Environment.builder().load(resourceLoader).build().listRecipes();
	}

	public List<Recipe> discoverFilteredRecipes(List<String> activeRecipes, Properties properties,
			String[] acceptPackages, ClasspathScanningLoader classpathScanningLoader) {
		if (activeRecipes.isEmpty()) {
			LOGGER.warn("No active recipes were provided.");
			return emptyList();
		}

		List<Recipe> recipes = new ArrayList<>();

		Environment environment = Environment.builder(properties).load(classpathScanningLoader).build();

		Recipe recipe = environment.activateRecipes(activeRecipes);

		if (recipe.getRecipeList().isEmpty()) {
			LOGGER.warn(
					"No recipes were activated. None of the provided 'activeRecipes' matched any of the applicable recipes.");
			return emptyList();
		}

		Collection<Validated<Object>> validated = recipe.validateAll();
		List<Validated.Invalid<Object>> failedValidations = validated.stream()
			.map(Validated::failures)
			.flatMap(Collection::stream)
			.collect(toList());
		if (!failedValidations.isEmpty()) {
			failedValidations
				.forEach(failedValidation -> LOGGER.error("Recipe validation error in " + failedValidation.getProperty()
						+ ": " + failedValidation.getMessage(), failedValidation.getException()));
			if (springRewriteProperties.isFailOnInvalidActiveRecipes()) {
				throw new RecipeValidationErrorException(
						"Recipe validation errors detected as part of one or more activeRecipe(s). Please check error logs.");
			}
			else {
				LOGGER.error(
						"Recipe validation errors detected as part of one or more activeRecipe(s). Execution will continue regardless.");
			}
		}

		recipes.add(recipe);

		return recipes;
	}

	// public List<Recipe> discoverFilteredRecipes(List<String> activeRecipes,
	// MavenProject mavenProject) {
	// if (activeRecipes.isEmpty()) {
	// log.warn("No active recipes were provided.");
	// return emptyList();
	// }
	//
	// List<Recipe> recipes = new ArrayList<>();
	//
	// AbstractRewriteMojoHelper helper = new AbstractRewriteMojoHelper(mavenProject);
	//
	// Environment env = helper.environment(getClass().getClassLoader());
	// Recipe recipe = env.activateAll();
	//// Recipe recipe = env.activateRecipes(activeRecipes);
	//
	// if (recipe.getRecipeList().isEmpty()) {
	// log.warn("No recipes were activated. None of the provided 'activeRecipes' matched
	// any of the applicable recipes.");
	// return emptyList();
	// }
	//
	// Collection<Validated<Object>> validated = recipe.validateAll();
	// List<Validated.Invalid<Object>> failedValidations =
	// validated.stream().map(Validated::failures)
	// .flatMap(Collection::stream).collect(toList());
	// if (!failedValidations.isEmpty()) {
	// failedValidations.forEach(failedValidation -> log.error(
	// "Recipe validation error in " + failedValidation.getProperty() + ": " +
	// failedValidation.getMessage(), failedValidation.getException()));
	// if (springRewriteProperties.isFailOnInvalidActiveRecipes()) {
	// throw new RecipeValidationErrorException("Recipe validation errors detected as part
	// of one or more activeRecipe(s). Please check error logs.");
	// } else {
	// log.error("Recipe validation errors detected as part of one or more
	// activeRecipe(s). Execution will continue regardless.");
	// }
	// }
	//
	// return recipes;
	// }

	public RecipeDescriptor findRecipeDescriptor(String anotherDummyRecipe) {
		ResourceLoader resourceLoader = new ClasspathScanningLoader(new Properties(), new String[] { "io.example" });
		Environment environment = Environment.builder().load(resourceLoader).build();

		Collection<RecipeDescriptor> recipeDescriptors = environment.listRecipeDescriptors();
		RecipeDescriptor descriptor = recipeDescriptors.stream()
			.filter(rd -> "AnotherDummyRecipe".equals(rd.getDisplayName()))
			.findFirst()
			.get();
		return descriptor;
	}

	public List<Recipe> findRecipesByTag(String tag) {
		return getFilteredRecipes(r -> r.getTags().contains(tag));
	}

	/**
	 * @param name of the recipe that should be returned.
	 * @return Optional recipe matching name or empty when no recipe matches.
	 * @throws IllegalArgumentException when more than one recipe was found.
	 */
	public Optional<Recipe> findRecipeByName(String name) {
		List<Recipe> filteredRecipes = getFilteredRecipes(r -> r.getName().equals(name));
		if (filteredRecipes.size() > 1) {
			throw new IllegalStateException("Found more than one recipe with name '%s'".formatted(name));
		}
		else if (filteredRecipes.isEmpty()) {
			return Optional.empty();
		}
		else {
			return Optional.of(filteredRecipes.get(0));
		}
	}

	public Recipe getRecipeByName(String name) {
		List<Recipe> filteredRecipes = getFilteredRecipes(r -> r.getName().equals(name));
		if (filteredRecipes.size() > 1) {
			throw new IllegalArgumentException("Found more than one recipe with name '%s'".formatted(name));
		}
		else if (filteredRecipes.isEmpty()) {
			throw new IllegalArgumentException("No recipe found with name '%s'".formatted(name));
		}
		else {
			return filteredRecipes.get(0);
		}
	}

	@NotNull
	public static List<Recipe> getFilteredRecipes(Predicate<Recipe> filterPredicate) {
		ResourceLoader resourceLoader = new ClasspathScanningLoader(new Properties(), new String[] {});
		Environment environment = Environment.builder().load(resourceLoader).build();
		List<Recipe> recipes = environment.listRecipes().stream().filter(filterPredicate).toList();
		return recipes;
	}

	// class AbstractRewriteMojoHelper extends AbstractRewriteMojo {
	//
	// public AbstractRewriteMojoHelper(MavenProject mavenProject) {
	// super.project = mavenProject;
	// }
	//
	// @Override
	// public void execute() throws MojoExecutionException, MojoFailureException {
	// throw new UnsupportedOperationException();
	// }
	//
	// @Override
	// public Environment environment(@Nullable ClassLoader recipeClassLoader) {
	// Environment.Builder env = Environment.builder(this.project.getProperties());
	// if (recipeClassLoader == null) {
	// env.scanRuntimeClasspath(new String[0]).scanUserHome();
	// } else {
	// env.load(new ClasspathScanningLoader(this.project.getProperties(),
	// recipeClassLoader));
	// }
	//
	//
	// /*env.load(new ResourceLoader() {
	// @Override
	// public Collection<Recipe> listRecipes() {
	// return List.of();
	// }
	//
	// @Override
	// public Collection<RecipeDescriptor> listRecipeDescriptors() {
	// return List.of();
	// }
	//
	// @Override
	// public Collection<NamedStyles> listStyles() {
	// return List.of();
	// }
	//
	// @Override
	// public Collection<CategoryDescriptor> listCategoryDescriptors() {
	// return List.of();
	// }
	//
	// @Override
	// public Map<String, List<Contributor>> listContributors() {
	// return Map.of();
	// }
	//
	// @Override
	// public Map<String, List<RecipeExample>> listRecipeExamples() {
	// return Map.of();
	// }
	// });*/
	// return env.build();
	// }
	//
	//// @Override
	//// protected Environment environment() {
	//// try {
	//// return super.environment();
	//// } catch (MojoExecutionException e) {
	//// throw new RuntimeException(e);
	//// }
	//// }
	//
	// @Override
	// public Path repositoryRoot() {
	// return super.repositoryRoot();
	// }
	// }

}
