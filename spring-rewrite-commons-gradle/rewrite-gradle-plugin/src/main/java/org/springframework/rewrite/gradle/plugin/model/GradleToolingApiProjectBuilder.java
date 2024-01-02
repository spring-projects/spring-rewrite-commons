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
package org.springframework.rewrite.gradle.plugin.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.plugins.PluginManager;
import org.gradle.invocation.DefaultGradle;
import org.gradle.plugin.use.PluginId;
import org.gradle.util.GradleVersion;
import org.openrewrite.gradle.toolingapi.*;
import org.springframework.rewrite.gradle.model.GradleProjectData;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

public class GradleToolingApiProjectBuilder {

	@Builder
	@Getter
	static class MavenRepositoryImpl implements MavenRepository, Serializable {

		String id;

		String uri;

		String releases;

		String snapshots;

		boolean knownToExist;

		String username;

		String password;

		Boolean DeriveMetadataIfMissing;

	}

	@AllArgsConstructor
	@Getter
	static class GradlePluginDescriptorImpl implements GradlePluginDescriptor, Serializable {

		String fullyQualifiedClassName;

		String id;

	}

	@AllArgsConstructor
	@Getter
	@EqualsAndHashCode
	static class GroupArtifactVersionImpl implements GroupArtifactVersion, Serializable {

		String groupId;

		String artifactId;

		String version;

	}

	@AllArgsConstructor
	@Getter
	@EqualsAndHashCode
	static class GroupArtifactImpl implements GroupArtifact, Serializable {

		String groupId;

		String artifactId;

	}

	@AllArgsConstructor
	@Getter
	@Builder
	@EqualsAndHashCode
	static class DependencyImpl implements Dependency, Serializable {

		GroupArtifactVersion gav;

		String classifier;

		String type;

		String scope;

		List<GroupArtifact> exclusions;

		String optional;

	}

	@AllArgsConstructor
	@Getter
	@EqualsAndHashCode
	static class ResolvedGroupArtifactVersionImpl implements ResolvedGroupArtifactVersion, Serializable {

		String artifactId;

		String groupId;

		String version;

		String datedSnapshotVersion;

	}

	@AllArgsConstructor
	@Builder
	@Getter
	@EqualsAndHashCode
	static class ResolvedDependencyImpl implements ResolvedDependency, Serializable {

		MavenRepositoryImpl repository;

		ResolvedGroupArtifactVersionImpl gav;

		DependencyImpl requested;

		List<ResolvedDependency> dependencies;

		int depth;

	}

	@AllArgsConstructor
	@Getter
	static class GradleDependencyConfigurationImpl implements GradleDependencyConfiguration, Serializable {

		String name;

		String description;

		boolean transitive;

		boolean canBeConsumed;

		boolean canBeResolved;

		List<GradleDependencyConfiguration> extendsFrom;

		List<Dependency> requested;

		List<ResolvedDependency> resolved;

	}

	@AllArgsConstructor
	@Getter
	static class GradleProjectImpl implements GradleProject, Serializable {

		String name;

		String path;

		List<GradlePluginDescriptor> plugins;

		List<MavenRepository> mavenRepositories;

		List<MavenRepository> mavenPluginRepositories;

		Map<String, GradleDependencyConfiguration> nameToConfiguration;

	}

	static MavenRepositoryImpl GRADLE_PLUGIN_PORTAL = MavenRepositoryImpl.builder()
		.id("Gradle Central Plugin Repository")
		.uri("https://plugins.gradle.org/m2")
		.releases(String.valueOf(true))
		.snapshots(String.valueOf(true))
		.build();

	public static GradleProject gradleProject(Project project) {
		return new GradleProjectImpl(project.getName(), project.getPath(),
				pluginDescriptors(project.getPluginManager()), mapRepositories(project.getRepositories()),
				pluginMavenRepos(project), dependencyConfigurations(project.getConfigurations()));
	}

	static List<MavenRepository> pluginMavenRepos(Project project) {
		Set<MavenRepository> pluginRepositories = new HashSet<>();
		if (GradleVersion.current().compareTo(GradleVersion.version("4.4")) >= 0) {
			Settings settings = ((DefaultGradle) project.getGradle()).getSettings();
			pluginRepositories.addAll(mapRepositories(settings.getPluginManagement().getRepositories()));
			pluginRepositories.addAll(mapRepositories(settings.getBuildscript().getRepositories()));
		}
		pluginRepositories.addAll(mapRepositories(project.getBuildscript().getRepositories()));
		if (pluginRepositories.isEmpty()) {
			pluginRepositories.add(GRADLE_PLUGIN_PORTAL);
		}

		return new ArrayList<>(pluginRepositories);
	}

	static List<MavenRepository> mapRepositories(List<ArtifactRepository> repositories) {
		return repositories.stream()
			.filter(MavenArtifactRepository.class::isInstance)
			.map(MavenArtifactRepository.class::cast)
			.map(repo -> MavenRepositoryImpl.builder()
				.id(repo.getName())
				.uri(repo.getUrl().toString())
				.releases(String.valueOf(true))
				.snapshots(String.valueOf(true))
				.build())
			.collect(toList());
	}

	public static List<GradlePluginDescriptor> pluginDescriptors(PluginManager pluginManager) {
		if (pluginManager instanceof PluginManagerInternal) {
			return pluginDescriptors((PluginManagerInternal) pluginManager);
		}
		return emptyList();
	}

	public static List<GradlePluginDescriptor> pluginDescriptors(PluginManagerInternal pluginManager) {
		return pluginManager.getPluginContainer()
			.stream()
			.map(plugin -> new GradlePluginDescriptorImpl(plugin.getClass().getName(),
					pluginIdForClass(pluginManager, plugin.getClass())))
			.collect(toList());
	}

	private static String pluginIdForClass(PluginManagerInternal pluginManager, Class<?> pluginClass) {
		try {
			Method findPluginIdForClass = PluginManagerInternal.class.getMethod("findPluginIdForClass", Class.class);
			// noinspection unchecked
			Optional<PluginId> maybePluginId = (Optional<PluginId>) findPluginIdForClass.invoke(pluginManager,
					pluginClass);
			return maybePluginId.map(PluginId::getId).orElse(null);
		}
		catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
			// On old versions of gradle that don't have this method, returning null is
			// fine
		}
		return null;
	}

	private static final Map<GroupArtifactImpl, GroupArtifactImpl> groupArtifactCache = new ConcurrentHashMap<>();

	private static GroupArtifactImpl groupArtifact(Dependency dep) {
		// noinspection ConstantConditions
		return groupArtifactCache
			.computeIfAbsent(new GroupArtifactImpl(dep.getGav().getGroupId(), dep.getGav().getArtifactId()), it -> it);
	}

	private static GroupArtifactImpl groupArtifact(org.gradle.api.artifacts.ResolvedDependency dep) {
		return groupArtifactCache.computeIfAbsent(new GroupArtifactImpl(dep.getModuleGroup(), dep.getModuleName()),
				it -> it);
	}

	private static final Map<GroupArtifactVersionImpl, GroupArtifactVersionImpl> groupArtifactVersionCache = new ConcurrentHashMap<>();

	private static GroupArtifactVersionImpl groupArtifactVersion(org.gradle.api.artifacts.ResolvedDependency dep) {
		return groupArtifactVersionCache.computeIfAbsent(new GroupArtifactVersionImpl(dep.getModuleGroup(),
				dep.getModuleName(), unspecifiedToNull(dep.getModuleVersion())), it -> it);
	}

	private static GroupArtifactVersionImpl groupArtifactVersion(org.gradle.api.artifacts.Dependency dep) {
		return groupArtifactVersionCache.computeIfAbsent(
				new GroupArtifactVersionImpl(dep.getGroup(), dep.getName(), unspecifiedToNull(dep.getVersion())),
				it -> it);
	}

	private static final Map<ResolvedGroupArtifactVersionImpl, ResolvedGroupArtifactVersionImpl> resolvedGroupArtifactVersionCache = new ConcurrentHashMap<>();

	private static ResolvedGroupArtifactVersionImpl resolvedGroupArtifactVersion(
			org.gradle.api.artifacts.ResolvedDependency dep) {
		return resolvedGroupArtifactVersionCache.computeIfAbsent(new ResolvedGroupArtifactVersionImpl(
				dep.getModuleName(), dep.getModuleGroup(), dep.getModuleVersion(), null), it -> it);
	}

	/**
	 * Some Gradle dependency functions will have the String "unspecified" to indicate a
	 * missing value. Rewrite's dependency API represents these missing things as "null"
	 */
	private static String unspecifiedToNull(String maybeUnspecified) {
		if ("unspecified".equals(maybeUnspecified)) {
			return null;
		}
		return maybeUnspecified;
	}

	static Map<String, GradleDependencyConfiguration> dependencyConfigurations(
			ConfigurationContainer configurationContainer) {
		Map<String, GradleDependencyConfiguration> results = new HashMap<>();
		List<Configuration> configurations = new ArrayList<>(configurationContainer);
		for (Configuration conf : configurations) {
			try {
				List<Dependency> requested = conf.getAllDependencies()
					.stream()
					.map(dep -> dependency(dep, conf))
					.collect(Collectors.toList());

				List<ResolvedDependency> resolved;
				Map<GroupArtifactImpl, Dependency> gaToRequested = requested.stream()
					.collect(Collectors.toMap(GradleToolingApiProjectBuilder::groupArtifact, dep -> dep, (a, b) -> a));
				// Archives and default are redundant with other configurations
				// Newer versions of gradle display warnings with long stack traces when
				// attempting to resolve them
				// Some Scala plugin we don't care about creates configurations that, for
				// some unknown reason, are difficult to resolve
				if (conf.isCanBeResolved() && !"archives".equals(conf.getName()) && !"default".equals(conf.getName())
						&& !conf.getName().startsWith("incrementalScalaAnalysis")) {
					ResolvedConfiguration resolvedConf = conf.getResolvedConfiguration();
					Map<GroupArtifactImpl, org.gradle.api.artifacts.ResolvedDependency> gaToResolved = resolvedConf
						.getFirstLevelModuleDependencies()
						.stream()
						.collect(Collectors.toMap(dep -> GradleToolingApiProjectBuilder.groupArtifact(dep), dep -> dep,
								(a, b) -> a));
					resolved = resolved(gaToRequested, gaToResolved);
				}
				else {
					resolved = emptyList();
				}
				// TODO: Doesn't look like 'transitive' is needed at all for Gradle
				// tooling model side
				List<ResolvedDependency> transitive = resolveTransitiveDependencies(resolved, new LinkedHashSet<>());
				GradleDependencyConfigurationImpl dc = new GradleDependencyConfigurationImpl(conf.getName(),
						conf.getDescription(), conf.isTransitive(), conf.isCanBeConsumed(), conf.isCanBeResolved(),
						emptyList(), requested, resolved);
				results.put(conf.getName(), dc);
			}
			catch (Exception e) {
				GradleDependencyConfigurationImpl dc = new GradleDependencyConfigurationImpl(conf.getName(),
						conf.getDescription(), conf.isTransitive(), conf.isCanBeConsumed(), conf.isCanBeResolved(),
						emptyList(), emptyList(), emptyList());
				results.put(conf.getName(), dc);
			}
		}

		// Record the relationships between dependency configurations
		for (Configuration conf : configurations) {
			if (conf.getExtendsFrom().isEmpty()) {
				continue;
			}
			GradleDependencyConfigurationImpl dc = (GradleDependencyConfigurationImpl) results.get(conf.getName());
			if (dc != null) {
				List<GradleDependencyConfiguration> extendsFrom = conf.getExtendsFrom()
					.stream()
					.map(it -> results.get(it.getName()))
					.collect(Collectors.toList());
				dc.extendsFrom = extendsFrom;
			}
		}
		return results;
	}

	static List<ResolvedDependency> resolveTransitiveDependencies(List<ResolvedDependency> resolved,
			Set<ResolvedDependency> alreadyResolved) {
		for (ResolvedDependency dependency : resolved) {
			if (alreadyResolved.add(dependency)) {
				alreadyResolved.addAll(resolveTransitiveDependencies(dependency.getDependencies(), alreadyResolved));
			}
		}
		return new ArrayList<>(alreadyResolved);
	}

	private static final Map<GroupArtifactVersionImpl, DependencyImpl> requestedCache = new ConcurrentHashMap<>();

	private static DependencyImpl dependency(org.gradle.api.artifacts.Dependency dep, Configuration configuration) {
		GroupArtifactVersionImpl gav = groupArtifactVersion(dep);
		return requestedCache.computeIfAbsent(gav,
				it -> DependencyImpl.builder()
					.gav(gav)
					.type("jar")
					.scope(configuration.getName())
					.exclusions(emptyList())
					.build());
	}

	private static List<ResolvedDependency> resolved(Map<GroupArtifactImpl, Dependency> gaToRequested,
			Map<GroupArtifactImpl, org.gradle.api.artifacts.ResolvedDependency> gaToResolved) {
		Map<ResolvedGroupArtifactVersionImpl, ResolvedDependencyImpl> resolvedCache = new HashMap<>();
		return gaToResolved.entrySet().stream().map(entry -> {
			GroupArtifactImpl ga = entry.getKey();
			org.gradle.api.artifacts.ResolvedDependency resolved = entry.getValue();

			// Gradle knows which repository it got a dependency from, but haven't been
			// able to find where that info lives
			ResolvedGroupArtifactVersionImpl resolvedGav = resolvedGroupArtifactVersion(resolved);
			ResolvedDependencyImpl resolvedDependency = resolvedCache.get(resolvedGav);
			if (resolvedDependency == null) {
				resolvedDependency = ResolvedDependencyImpl.builder()
					.gav(resolvedGav)
					// There may not be a requested entry if a dependency substitution
					// rule took effect
					// the DependencyHandler has the substitution mapping buried inside
					// it, but not exposed publicly
					// Possible improvement to dig that out and use it
					.requested((DependencyImpl) gaToRequested.getOrDefault(ga, dependency(resolved)))
					.dependencies(resolved.getChildren()
						.stream()
						.map(child -> resolved(child, 1, resolvedCache))
						.collect(toList()))
					.depth(0)
					.build();
				resolvedCache.put(resolvedGav, resolvedDependency);
			}
			return resolvedDependency;
		}).collect(Collectors.toList());
	}

	/**
	 * When there is a resolved dependency that cannot be matched up with a requested
	 * dependency, construct a requested dependency corresponding to the exact version
	 * which was resolved. This isn't strictly accurate, but there is no obvious way to
	 * access the resolution of transitive dependencies to figure out what versions are
	 * requested during the resolution process.
	 */
	private static DependencyImpl dependency(org.gradle.api.artifacts.ResolvedDependency dep) {
		GroupArtifactVersionImpl gav = groupArtifactVersion(dep);
		return requestedCache.computeIfAbsent(gav,
				it -> DependencyImpl.builder()
					.gav(gav)
					.type("jar")
					.scope(dep.getConfiguration())
					.exclusions(emptyList())
					.build());
	}

	private static ResolvedDependencyImpl resolved(org.gradle.api.artifacts.ResolvedDependency dep, int depth,
			Map<ResolvedGroupArtifactVersionImpl, ResolvedDependencyImpl> resolvedCache) {
		ResolvedGroupArtifactVersionImpl resolvedGav = resolvedGroupArtifactVersion(dep);
		ResolvedDependencyImpl resolvedDependency = resolvedCache.get(resolvedGav);
		if (resolvedDependency == null) {

			List<ResolvedDependency> dependencies = new ArrayList<>();

			resolvedDependency = ResolvedDependencyImpl.builder()
				.gav(resolvedGav)
				.requested(dependency(dep))
				.dependencies(dependencies)
				.depth(depth)
				.build();
			// we add a temporal resolved dependency in the cache to avoid stackoverflow
			// with dependencies that have cycles
			resolvedCache.put(resolvedGav, resolvedDependency);
			dep.getChildren().forEach(child -> dependencies.add(resolved(child, depth + 1, resolvedCache)));
		}
		return resolvedDependency;
	}

	@SuppressWarnings("unused")
	public static void clearCaches() {
		requestedCache.clear();
		groupArtifactCache.clear();
		groupArtifactVersionCache.clear();
		resolvedGroupArtifactVersionCache.clear();
	}

	public static GradleProjectData createProjectData(Project project) {
		return GradleProjectDataImpl.from(project);
	}

}
