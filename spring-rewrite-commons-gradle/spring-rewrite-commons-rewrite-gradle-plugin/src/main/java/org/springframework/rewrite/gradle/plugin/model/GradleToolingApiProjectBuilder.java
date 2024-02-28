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

	static class MavenRepositoryImpl implements MavenRepository, Serializable {

		String id;

		String uri;

		String releases;

		String snapshots;

		boolean knownToExist;

		String username;

		String password;

		Boolean DeriveMetadataIfMissing;

		MavenRepositoryImpl(String id, String uri, String releases, String snapshots, boolean knownToExist,
				String username, String password, Boolean DeriveMetadataIfMissing) {
			this.id = id;
			this.uri = uri;
			this.releases = releases;
			this.snapshots = snapshots;
			this.knownToExist = knownToExist;
			this.username = username;
			this.password = password;
			this.DeriveMetadataIfMissing = DeriveMetadataIfMissing;
		}

		public static MavenRepositoryImplBuilder builder() {
			return new MavenRepositoryImplBuilder();
		}

		public String getId() {
			return this.id;
		}

		public String getUri() {
			return this.uri;
		}

		public String getReleases() {
			return this.releases;
		}

		public String getSnapshots() {
			return this.snapshots;
		}

		public boolean isKnownToExist() {
			return this.knownToExist;
		}

		public String getUsername() {
			return this.username;
		}

		public String getPassword() {
			return this.password;
		}

		public Boolean getDeriveMetadataIfMissing() {
			return this.DeriveMetadataIfMissing;
		}

		public static class MavenRepositoryImplBuilder {

			private String id;

			private String uri;

			private String releases;

			private String snapshots;

			private boolean knownToExist;

			private String username;

			private String password;

			private Boolean DeriveMetadataIfMissing;

			MavenRepositoryImplBuilder() {
			}

			public MavenRepositoryImplBuilder id(String id) {
				this.id = id;
				return this;
			}

			public MavenRepositoryImplBuilder uri(String uri) {
				this.uri = uri;
				return this;
			}

			public MavenRepositoryImplBuilder releases(String releases) {
				this.releases = releases;
				return this;
			}

			public MavenRepositoryImplBuilder snapshots(String snapshots) {
				this.snapshots = snapshots;
				return this;
			}

			public MavenRepositoryImplBuilder knownToExist(boolean knownToExist) {
				this.knownToExist = knownToExist;
				return this;
			}

			public MavenRepositoryImplBuilder username(String username) {
				this.username = username;
				return this;
			}

			public MavenRepositoryImplBuilder password(String password) {
				this.password = password;
				return this;
			}

			public MavenRepositoryImplBuilder DeriveMetadataIfMissing(Boolean DeriveMetadataIfMissing) {
				this.DeriveMetadataIfMissing = DeriveMetadataIfMissing;
				return this;
			}

			public MavenRepositoryImpl build() {
				return new MavenRepositoryImpl(this.id, this.uri, this.releases, this.snapshots, this.knownToExist,
						this.username, this.password, this.DeriveMetadataIfMissing);
			}

			public String toString() {
				return "GradleToolingApiProjectBuilder.MavenRepositoryImpl.MavenRepositoryImplBuilder(id=" + this.id
						+ ", uri=" + this.uri + ", releases=" + this.releases + ", snapshots=" + this.snapshots
						+ ", knownToExist=" + this.knownToExist + ", username=" + this.username + ", password="
						+ this.password + ", DeriveMetadataIfMissing=" + this.DeriveMetadataIfMissing + ")";
			}

		}

	}

	static class GradlePluginDescriptorImpl implements GradlePluginDescriptor, Serializable {

		String fullyQualifiedClassName;

		String id;

		public GradlePluginDescriptorImpl(String fullyQualifiedClassName, String id) {
			this.fullyQualifiedClassName = fullyQualifiedClassName;
			this.id = id;
		}

		public String getFullyQualifiedClassName() {
			return this.fullyQualifiedClassName;
		}

		public String getId() {
			return this.id;
		}

	}

	static class GroupArtifactVersionImpl implements GroupArtifactVersion, Serializable {

		String groupId;

		String artifactId;

		String version;

		public GroupArtifactVersionImpl(String groupId, String artifactId, String version) {
			this.groupId = groupId;
			this.artifactId = artifactId;
			this.version = version;
		}

		public String getGroupId() {
			return this.groupId;
		}

		public String getArtifactId() {
			return this.artifactId;
		}

		public String getVersion() {
			return this.version;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			GroupArtifactVersionImpl that = (GroupArtifactVersionImpl) o;
			return Objects.equals(groupId, that.groupId) && Objects.equals(artifactId, that.artifactId)
					&& Objects.equals(version, that.version);
		}

		@Override
		public int hashCode() {
			return Objects.hash(groupId, artifactId, version);
		}

	}

	static class GroupArtifactImpl implements GroupArtifact, Serializable {

		String groupId;

		String artifactId;

		public GroupArtifactImpl(String groupId, String artifactId) {
			this.groupId = groupId;
			this.artifactId = artifactId;
		}

		public String getGroupId() {
			return this.groupId;
		}

		public String getArtifactId() {
			return this.artifactId;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			GroupArtifactImpl that = (GroupArtifactImpl) o;
			return Objects.equals(groupId, that.groupId) && Objects.equals(artifactId, that.artifactId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(groupId, artifactId);
		}

	}

	static class DependencyImpl implements Dependency, Serializable {

		GroupArtifactVersion gav;

		String classifier;

		String type;

		String scope;

		List<GroupArtifact> exclusions;

		String optional;

		public DependencyImpl(GroupArtifactVersion gav, String classifier, String type, String scope,
				List<GroupArtifact> exclusions, String optional) {
			this.gav = gav;
			this.classifier = classifier;
			this.type = type;
			this.scope = scope;
			this.exclusions = exclusions;
			this.optional = optional;
		}

		public static DependencyImplBuilder builder() {
			return new DependencyImplBuilder();
		}

		public GroupArtifactVersion getGav() {
			return this.gav;
		}

		public String getClassifier() {
			return this.classifier;
		}

		public String getType() {
			return this.type;
		}

		public String getScope() {
			return this.scope;
		}

		public List<GroupArtifact> getExclusions() {
			return this.exclusions;
		}

		public String getOptional() {
			return this.optional;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			DependencyImpl that = (DependencyImpl) o;
			return Objects.equals(gav, that.gav) && Objects.equals(classifier, that.classifier)
					&& Objects.equals(type, that.type) && Objects.equals(scope, that.scope)
					&& Objects.equals(exclusions, that.exclusions) && Objects.equals(optional, that.optional);
		}

		@Override
		public int hashCode() {
			return Objects.hash(gav, classifier, type, scope, exclusions, optional);
		}

		public static class DependencyImplBuilder {

			private GroupArtifactVersion gav;

			private String classifier;

			private String type;

			private String scope;

			private List<GroupArtifact> exclusions;

			private String optional;

			DependencyImplBuilder() {
			}

			public DependencyImplBuilder gav(GroupArtifactVersion gav) {
				this.gav = gav;
				return this;
			}

			public DependencyImplBuilder classifier(String classifier) {
				this.classifier = classifier;
				return this;
			}

			public DependencyImplBuilder type(String type) {
				this.type = type;
				return this;
			}

			public DependencyImplBuilder scope(String scope) {
				this.scope = scope;
				return this;
			}

			public DependencyImplBuilder exclusions(List<GroupArtifact> exclusions) {
				this.exclusions = exclusions;
				return this;
			}

			public DependencyImplBuilder optional(String optional) {
				this.optional = optional;
				return this;
			}

			public DependencyImpl build() {
				return new DependencyImpl(this.gav, this.classifier, this.type, this.scope, this.exclusions,
						this.optional);
			}

			public String toString() {
				return "GradleToolingApiProjectBuilder.DependencyImpl.DependencyImplBuilder(gav=" + this.gav
						+ ", classifier=" + this.classifier + ", type=" + this.type + ", scope=" + this.scope
						+ ", exclusions=" + this.exclusions + ", optional=" + this.optional + ")";
			}

		}

	}

	static class ResolvedGroupArtifactVersionImpl implements ResolvedGroupArtifactVersion, Serializable {

		String artifactId;

		String groupId;

		String version;

		String datedSnapshotVersion;

		public ResolvedGroupArtifactVersionImpl(String artifactId, String groupId, String version,
				String datedSnapshotVersion) {
			this.artifactId = artifactId;
			this.groupId = groupId;
			this.version = version;
			this.datedSnapshotVersion = datedSnapshotVersion;
		}

		public String getArtifactId() {
			return this.artifactId;
		}

		public String getGroupId() {
			return this.groupId;
		}

		public String getVersion() {
			return this.version;
		}

		public String getDatedSnapshotVersion() {
			return this.datedSnapshotVersion;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			ResolvedGroupArtifactVersionImpl that = (ResolvedGroupArtifactVersionImpl) o;
			return Objects.equals(artifactId, that.artifactId) && Objects.equals(groupId, that.groupId)
					&& Objects.equals(version, that.version)
					&& Objects.equals(datedSnapshotVersion, that.datedSnapshotVersion);
		}

		@Override
		public int hashCode() {
			return Objects.hash(artifactId, groupId, version, datedSnapshotVersion);
		}

	}

	static class ResolvedDependencyImpl implements ResolvedDependency, Serializable {

		MavenRepositoryImpl repository;

		ResolvedGroupArtifactVersionImpl gav;

		DependencyImpl requested;

		List<ResolvedDependency> dependencies;

		int depth;

		public ResolvedDependencyImpl(MavenRepositoryImpl repository, ResolvedGroupArtifactVersionImpl gav,
				DependencyImpl requested, List<ResolvedDependency> dependencies, int depth) {
			this.repository = repository;
			this.gav = gav;
			this.requested = requested;
			this.dependencies = dependencies;
			this.depth = depth;
		}

		public static ResolvedDependencyImplBuilder builder() {
			return new ResolvedDependencyImplBuilder();
		}

		public MavenRepositoryImpl getRepository() {
			return this.repository;
		}

		public ResolvedGroupArtifactVersionImpl getGav() {
			return this.gav;
		}

		public DependencyImpl getRequested() {
			return this.requested;
		}

		public List<ResolvedDependency> getDependencies() {
			return this.dependencies;
		}

		public int getDepth() {
			return this.depth;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			ResolvedDependencyImpl that = (ResolvedDependencyImpl) o;
			return depth == that.depth && Objects.equals(repository, that.repository) && Objects.equals(gav, that.gav)
					&& Objects.equals(requested, that.requested) && Objects.equals(dependencies, that.dependencies);
		}

		@Override
		public int hashCode() {
			return Objects.hash(repository, gav, requested, dependencies, depth);
		}

		public static class ResolvedDependencyImplBuilder {

			private MavenRepositoryImpl repository;

			private ResolvedGroupArtifactVersionImpl gav;

			private DependencyImpl requested;

			private List<ResolvedDependency> dependencies;

			private int depth;

			ResolvedDependencyImplBuilder() {
			}

			public ResolvedDependencyImplBuilder repository(MavenRepositoryImpl repository) {
				this.repository = repository;
				return this;
			}

			public ResolvedDependencyImplBuilder gav(ResolvedGroupArtifactVersionImpl gav) {
				this.gav = gav;
				return this;
			}

			public ResolvedDependencyImplBuilder requested(DependencyImpl requested) {
				this.requested = requested;
				return this;
			}

			public ResolvedDependencyImplBuilder dependencies(List<ResolvedDependency> dependencies) {
				this.dependencies = dependencies;
				return this;
			}

			public ResolvedDependencyImplBuilder depth(int depth) {
				this.depth = depth;
				return this;
			}

			public ResolvedDependencyImpl build() {
				return new ResolvedDependencyImpl(this.repository, this.gav, this.requested, this.dependencies,
						this.depth);
			}

			public String toString() {
				return "GradleToolingApiProjectBuilder.ResolvedDependencyImpl.ResolvedDependencyImplBuilder(repository="
						+ this.repository + ", gav=" + this.gav + ", requested=" + this.requested + ", dependencies="
						+ this.dependencies + ", depth=" + this.depth + ")";
			}

		}

	}

	static class GradleDependencyConfigurationImpl implements GradleDependencyConfiguration, Serializable {

		String name;

		String description;

		boolean transitive;

		boolean canBeConsumed;

		boolean canBeResolved;

		List<GradleDependencyConfiguration> extendsFrom;

		List<Dependency> requested;

		List<ResolvedDependency> resolved;

		public GradleDependencyConfigurationImpl(String name, String description, boolean transitive,
				boolean canBeConsumed, boolean canBeResolved, List<GradleDependencyConfiguration> extendsFrom,
				List<Dependency> requested, List<ResolvedDependency> resolved) {
			this.name = name;
			this.description = description;
			this.transitive = transitive;
			this.canBeConsumed = canBeConsumed;
			this.canBeResolved = canBeResolved;
			this.extendsFrom = extendsFrom;
			this.requested = requested;
			this.resolved = resolved;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getDescription() {
			return description;
		}

		@Override
		public boolean isTransitive() {
			return transitive;
		}

		@Override
		public boolean isCanBeConsumed() {
			return canBeConsumed;
		}

		@Override
		public boolean isCanBeResolved() {
			return canBeResolved;
		}

		@Override
		public List<GradleDependencyConfiguration> getExtendsFrom() {
			return extendsFrom;
		}

		@Override
		public List<Dependency> getRequested() {
			return requested;
		}

		@Override
		public List<ResolvedDependency> getResolved() {
			return resolved;
		}

	}

	static class GradleProjectImpl implements GradleProject, Serializable {

		String name;

		String path;

		List<GradlePluginDescriptor> plugins;

		List<MavenRepository> mavenRepositories;

		List<MavenRepository> mavenPluginRepositories;

		Map<String, GradleDependencyConfiguration> nameToConfiguration;

		public GradleProjectImpl(String name, String path, List<GradlePluginDescriptor> plugins,
				List<MavenRepository> mavenRepositories, List<MavenRepository> mavenPluginRepositories,
				Map<String, GradleDependencyConfiguration> nameToConfiguration) {
			this.name = name;
			this.path = path;
			this.plugins = plugins;
			this.mavenRepositories = mavenRepositories;
			this.mavenPluginRepositories = mavenPluginRepositories;
			this.nameToConfiguration = nameToConfiguration;
		}

		public String getName() {
			return this.name;
		}

		public String getPath() {
			return this.path;
		}

		public List<GradlePluginDescriptor> getPlugins() {
			return this.plugins;
		}

		public List<MavenRepository> getMavenRepositories() {
			return this.mavenRepositories;
		}

		public List<MavenRepository> getMavenPluginRepositories() {
			return this.mavenPluginRepositories;
		}

		public Map<String, GradleDependencyConfiguration> getNameToConfiguration() {
			return this.nameToConfiguration;
		}

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
