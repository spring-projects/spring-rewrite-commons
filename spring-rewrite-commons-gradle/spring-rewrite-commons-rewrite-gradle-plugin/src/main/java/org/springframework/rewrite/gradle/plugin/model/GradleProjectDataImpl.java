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

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.initialization.Settings;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.invocation.DefaultGradle;
import org.gradle.util.GradleVersion;
import org.openrewrite.gradle.toolingapi.*;
import org.springframework.rewrite.gradle.model.GradleProjectData;
import org.springframework.rewrite.gradle.model.JavaSourceSetData;
import org.springframework.rewrite.gradle.model.KotlinSourceSetData;

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

final class GradleProjectDataImpl implements GradleProjectData, Serializable {

	private static final Logger logger = Logging.getLogger(GradleProjectDataImpl.class);

	private static final Class<?>[] SUPPORTED_GRADLE_PROPERTY_VALUE_TYPES = new Class<?>[] { Number.class,
			Boolean.class, String.class, Character.class };

	private final String name;

	private final String path;

	private final String group;

	private final String version;

	private final List<GradlePluginDescriptor> plugins;

	private final List<MavenRepository> mavenRepositories;

	private final List<MavenRepository> mavenPluginRepositories;

	private final Map<String, GradleDependencyConfiguration> nameToConfiguration;

	private final GradleSettings gradleSettings;

	private final String gradleVersion;

	private final boolean rootProject;

	private final File rootProjectDir;

	private final Collection<GradleProjectData> subprojects;

	private final File projectDir;

	private final File buildDir;

	private final File buildscriptFile;

	private final Map<String, ?> properties;

	private final List<JavaSourceSetData> javaSourceSets;

	private final boolean multiPlatformKotlinProject;

	private final List<KotlinSourceSetData> kotlinSourceSets;

	private final Collection<File> buildscriptClasspath;

	private final Collection<File> settingsClasspath;

	public GradleProjectDataImpl(String name, String path, String group, String version,
			List<GradlePluginDescriptor> plugins, List<MavenRepository> mavenRepositories,
			List<MavenRepository> mavenPluginRepositories,
			Map<String, GradleDependencyConfiguration> nameToConfiguration, GradleSettings gradleSettings,
			String gradleVersion, boolean rootProject, File rootProjectDir, Collection<GradleProjectData> subprojects,
			File projectDir, File buildDir, File buildscriptFile, Map<String, ?> properties,
			List<JavaSourceSetData> javaSourceSets, boolean multiPlatformKotlinProject,
			List<KotlinSourceSetData> kotlinSourceSets, Collection<File> buildscriptClasspath,
			Collection<File> settingsClasspath) {
		this.name = name;
		this.path = path;
		this.group = group;
		this.version = version;
		this.plugins = plugins;
		this.mavenRepositories = mavenRepositories;
		this.mavenPluginRepositories = mavenPluginRepositories;
		this.nameToConfiguration = nameToConfiguration;
		this.gradleSettings = gradleSettings;
		this.gradleVersion = gradleVersion;
		this.rootProject = rootProject;
		this.rootProjectDir = rootProjectDir;
		this.subprojects = subprojects;
		this.projectDir = projectDir;
		this.buildDir = buildDir;
		this.buildscriptFile = buildscriptFile;
		this.properties = properties;
		this.javaSourceSets = javaSourceSets;
		this.multiPlatformKotlinProject = multiPlatformKotlinProject;
		this.kotlinSourceSets = kotlinSourceSets;
		this.buildscriptClasspath = buildscriptClasspath;
		this.settingsClasspath = settingsClasspath;
	}

	static GradleProjectDataImpl from(Project project) {
		GradleProject toolingRewriiteGradleProject = GradleToolingApiProjectBuilder.gradleProject(project);
		return new GradleProjectDataImpl(project.getName(), project.getPath(), project.getGroup().toString(),
				project.getVersion().toString(),
				GradleToolingApiProjectBuilder.pluginDescriptors(project.getPluginManager()),
				GradleToolingApiProjectBuilder.mapRepositories(project.getRepositories()),
				GradleToolingApiProjectBuilder.pluginMavenRepos(project),
				GradleToolingApiProjectBuilder.dependencyConfigurations(project.getConfigurations()),
				GradleVersion.current().compareTo(GradleVersion.version("4.4")) >= 0 ? GradleToolingApiSettingsBuilder
					.gradleSettings(((DefaultGradle) project.getGradle()).getSettings()) : null,
				project.getGradle().getGradleVersion(), project == project.getRootProject(),
				project.getRootProject().getProjectDir(), subprojects(project.getSubprojects()),
				project.getProjectDir(), project.getBuildDir(), project.getBuildscript().getSourceFile(),
				properties(project.getProperties()), javaSourceSets(project), isMultiPlatformKotlinProject(project),
				kotlinSourceSets(project),
				project.getBuildscript().getConfigurations().getByName("classpath").resolve(),
				settingsClasspath(project));
	}

	private static Collection<GradleProjectData> subprojects(Collection<Project> subprojects) {
		List<GradleProjectData> sub = new ArrayList<>(subprojects.size());
		for (Project s : subprojects) {
			sub.add(from(s));
		}
		return sub;
	}

	private static Map<String, ?> properties(Map<String, ?> props) {
		return props.entrySet()
			.stream()
			.filter(e -> Arrays.stream(SUPPORTED_GRADLE_PROPERTY_VALUE_TYPES).anyMatch(c -> c.isInstance(e.getValue())))
			.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
	}

	private static List<JavaSourceSetData> javaSourceSets(Project project) {
		JavaPluginConvention javaConvention = (JavaPluginConvention) project.getConvention()
			.findPlugin(JavaPluginConvention.class);
		if (javaConvention == null) {
			return Collections.emptyList();
		}
		else {
			List<JavaSourceSetData> sourceSetData = new ArrayList<>(javaConvention.getSourceSets().size());
			for (SourceSet sourceSet : javaConvention.getSourceSets()) {
				sourceSetData.add(new JavaSourceSetDataImpl(sourceSet.getName(), sourceSet.getAllSource().getFiles(),
						sourceSet.getResources().getSourceDirectories().getFiles(), sourceSet.getAllJava().getFiles(),
						sourceSet.getOutput().getClassesDirs().getFiles(), sourceSet.getCompileClasspath().getFiles(),
						javaSourceSetImplementationClasspath(project, sourceSet),
						sourceSetJavaVersion(project, sourceSet)));
			}
			return sourceSetData;
		}
	}

	private static Collection<File> javaSourceSetImplementationClasspath(Project project, SourceSet sourceSet) {
		// classpath doesn't include the transitive dependencies of the implementation
		// configuration
		// These aren't needed for compilation, but we want them so recipes have access to
		// comprehensive type information
		// The implementation configuration isn't resolvable, so we need a new
		// configuration that extends from it
		Configuration implementation = project.getConfigurations()
			.getByName(sourceSet.getImplementationConfigurationName());
		Configuration rewriteImplementation = project.getConfigurations()
			.maybeCreate("rewrite" + sourceSet.getImplementationConfigurationName());
		rewriteImplementation.extendsFrom(new Configuration[] { implementation });

		try {
			return rewriteImplementation.resolve();
		}
		catch (Exception e) {
			return Collections.emptySet();
		}
	}

	private static JavaVersionDataImpl sourceSetJavaVersion(Project project, SourceSet sourceSet) {
		final JavaCompile javaCompileTask = (JavaCompile) project.getTasks()
			.getByName(sourceSet.getCompileJavaTaskName());
		return new JavaVersionDataImpl(System.getProperty("java.runtime.version"), System.getProperty("java.vm.vendor"),
				javaCompileTask.getSourceCompatibility(), javaCompileTask.getTargetCompatibility());
	}

	private static boolean isMultiPlatformKotlinProject(Project project) {
		try {
			return project.getPlugins().hasPlugin("org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension")
					|| project.getExtensions().findByName("kotlin") != null && project.getExtensions()
						.findByName("kotlin")
						.getClass()
						.getCanonicalName()
						.startsWith("org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension");
		}
		catch (Throwable t) {
			return false;
		}
	}

	private static List<KotlinSourceSetData> kotlinSourceSets(Project project) {
		NamedDomainObjectContainer sourceSets;
		try {
			Object kotlinExtension = project.getExtensions().getByName("kotlin");
			Class<?> clazz = kotlinExtension.getClass()
				.getClassLoader()
				.loadClass("org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension");
			sourceSets = (NamedDomainObjectContainer) clazz.getMethod("getSourceSets").invoke(kotlinExtension);
		}
		catch (Exception e) {
			return Collections.emptyList();
		}

		SortedSet<String> sourceSetNames;
		try {
			sourceSetNames = (SortedSet<String>) sourceSets.getClass().getMethod("getNames").invoke(sourceSets);
		}
		catch (Exception e) {
			return Collections.emptyList();
		}

		List<KotlinSourceSetData> kotlinSourceSetData = new ArrayList<>(sourceSetNames.size());

		for (String sourceSetName : sourceSetNames) {
			try {
				Object sourceSet = sourceSets.getClass()
					.getMethod("getByName", String.class)
					.invoke(sourceSets, sourceSetName);
				final SourceDirectorySet kotlinDirectorySet = (SourceDirectorySet) sourceSet.getClass()
					.getMethod("getKotlin")
					.invoke(sourceSet);
				String implementationName = (String) sourceSet.getClass()
					.getMethod("getImplementationConfigurationName")
					.invoke(sourceSet);
				Configuration implementation = project.getConfigurations().getByName(implementationName);
				Configuration rewriteImplementation = (Configuration) project.getConfigurations()
					.maybeCreate("rewrite" + implementationName);
				rewriteImplementation.extendsFrom(new Configuration[] { implementation });

				Set implementationClasspath;
				try {
					implementationClasspath = rewriteImplementation.resolve();
				}
				catch (Exception e) {
					implementationClasspath = Collections.emptySet();
				}

				String compileName = (String) sourceSet.getClass()
					.getMethod("getCompileOnlyConfigurationName")
					.invoke(sourceSet);
				Configuration compileOnly = project.getConfigurations().getByName(compileName);
				Configuration rewriteCompileOnly = project.getConfigurations().maybeCreate("rewrite" + compileName);
				rewriteCompileOnly.setCanBeResolved(true);
				rewriteCompileOnly.extendsFrom(new Configuration[] { compileOnly });
				final Set<File> compClasspath = rewriteCompileOnly.getFiles();
				kotlinSourceSetData.add(new KotlinSourceSetDataImpl(sourceSetName, kotlinDirectorySet.getFiles(),
						compClasspath, implementationClasspath));
			}
			catch (Exception e) {
				logger.warn("Failed to resolve sourceSet from {}:{}. Some type information may be incomplete",
						project.getPath(), sourceSetName);
			}
		}

		return kotlinSourceSetData;
	}

	private static Collection<File> settingsClasspath(Project project) {
		if (GradleVersion.current().compareTo(GradleVersion.version("4.4")) >= 0) {
			try {
				Settings settings = ((DefaultGradle) project.getGradle()).getSettings();
				return settings.getBuildscript().getConfigurations().getByName("classpath").resolve();
			}
			catch (IllegalStateException e) {
				// ignore - return empty list
			}
		}
		return emptyList();
	}

	public String getName() {
		return this.name;
	}

	public String getPath() {
		return this.path;
	}

	public String getGroup() {
		return this.group;
	}

	public String getVersion() {
		return this.version;
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

	public GradleSettings getGradleSettings() {
		return this.gradleSettings;
	}

	public String getGradleVersion() {
		return this.gradleVersion;
	}

	public boolean isRootProject() {
		return this.rootProject;
	}

	public File getRootProjectDir() {
		return this.rootProjectDir;
	}

	public Collection<GradleProjectData> getSubprojects() {
		return this.subprojects;
	}

	public File getProjectDir() {
		return this.projectDir;
	}

	public File getBuildDir() {
		return this.buildDir;
	}

	public File getBuildscriptFile() {
		return this.buildscriptFile;
	}

	public Map<String, ?> getProperties() {
		return this.properties;
	}

	public List<JavaSourceSetData> getJavaSourceSets() {
		return this.javaSourceSets;
	}

	public boolean isMultiPlatformKotlinProject() {
		return this.multiPlatformKotlinProject;
	}

	public List<KotlinSourceSetData> getKotlinSourceSets() {
		return this.kotlinSourceSets;
	}

	public Collection<File> getBuildscriptClasspath() {
		return this.buildscriptClasspath;
	}

	public Collection<File> getSettingsClasspath() {
		return this.settingsClasspath;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		GradleProjectDataImpl that = (GradleProjectDataImpl) o;
		return rootProject == that.rootProject && multiPlatformKotlinProject == that.multiPlatformKotlinProject
				&& Objects.equals(name, that.name) && Objects.equals(path, that.path)
				&& Objects.equals(group, that.group) && Objects.equals(version, that.version)
				&& Objects.equals(plugins, that.plugins) && Objects.equals(mavenRepositories, that.mavenRepositories)
				&& Objects.equals(mavenPluginRepositories, that.mavenPluginRepositories)
				&& Objects.equals(nameToConfiguration, that.nameToConfiguration)
				&& Objects.equals(gradleSettings, that.gradleSettings)
				&& Objects.equals(gradleVersion, that.gradleVersion)
				&& Objects.equals(rootProjectDir, that.rootProjectDir) && Objects.equals(subprojects, that.subprojects)
				&& Objects.equals(projectDir, that.projectDir) && Objects.equals(buildDir, that.buildDir)
				&& Objects.equals(buildscriptFile, that.buildscriptFile) && Objects.equals(properties, that.properties)
				&& Objects.equals(javaSourceSets, that.javaSourceSets)
				&& Objects.equals(kotlinSourceSets, that.kotlinSourceSets)
				&& Objects.equals(buildscriptClasspath, that.buildscriptClasspath)
				&& Objects.equals(settingsClasspath, that.settingsClasspath);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, path, group, version, plugins, mavenRepositories, mavenPluginRepositories,
				nameToConfiguration, gradleSettings, gradleVersion, rootProject, rootProjectDir, subprojects,
				projectDir, buildDir, buildscriptFile, properties, javaSourceSets, multiPlatformKotlinProject,
				kotlinSourceSets, buildscriptClasspath, settingsClasspath);
	}

	public String toString() {
		return "GradleProjectDataImpl(name=" + this.getName() + ", path=" + this.getPath() + ", group="
				+ this.getGroup() + ", version=" + this.getVersion() + ", plugins=" + this.getPlugins()
				+ ", mavenRepositories=" + this.getMavenRepositories() + ", mavenPluginRepositories="
				+ this.getMavenPluginRepositories() + ", nameToConfiguration=" + this.getNameToConfiguration()
				+ ", gradleSettings=" + this.getGradleSettings() + ", gradleVersion=" + this.getGradleVersion()
				+ ", rootProject=" + this.isRootProject() + ", rootProjectDir=" + this.getRootProjectDir()
				+ ", subprojects=" + this.getSubprojects() + ", projectDir=" + this.getProjectDir() + ", buildDir="
				+ this.getBuildDir() + ", buildscriptFile=" + this.getBuildscriptFile() + ", properties="
				+ this.getProperties() + ", javaSourceSets=" + this.getJavaSourceSets()
				+ ", multiPlatformKotlinProject=" + this.isMultiPlatformKotlinProject() + ", kotlinSourceSets="
				+ this.getKotlinSourceSets() + ", buildscriptClasspath=" + this.getBuildscriptClasspath()
				+ ", settingsClasspath=" + this.getSettingsClasspath() + ")";
	}

}
