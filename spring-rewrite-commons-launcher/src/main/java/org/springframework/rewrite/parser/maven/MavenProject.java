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
package org.springframework.rewrite.parser.maven;

import org.apache.maven.model.*;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.maven.utilities.MavenArtifactDownloader;
import org.openrewrite.xml.tree.Xml;
import org.springframework.core.io.Resource;
import org.springframework.rewrite.parser.ProjectId;
import org.springframework.rewrite.utils.LinuxWindowsPathUnifier;
import org.springframework.rewrite.utils.ResourceUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Predicate;

/**
 * @author Fabian Krüger
 */
public class MavenProject {

	private final Path projectRoot;

	private final MavenBuildFile buildFile;

	/**
	 * All {@link MavenProject}s of this build.
	 */
	private List<MavenProject> reactorProjects = new ArrayList<>();

	/**
	 * List of {@link MavenProject}s that depend on this project.
	 */
	private final List<MavenProject> dependentProjects = new ArrayList<>();

	/**
	 * List of {@link MavenProject}s this project depends on.
	 */
	private final List<MavenProject> dependencyProjects = new ArrayList<>();

	private final List<MavenProject> moduleProjects = new ArrayList<>();

	private final MavenArtifactDownloader rewriteMavenArtifactDownloader;

	private final List<Resource> resources;

	private ProjectId projectId;

	public MavenProject(Path baseDir, Resource rootPom, MavenArtifactDownloader rewriteMavenArtifactDownloader,
			List<Resource> resources) {
		this(baseDir, rootPom, List.of(), rewriteMavenArtifactDownloader, resources);
	}

	public MavenProject(Path baseDir, Resource pomFile, List<MavenProject> dependsOnModels,
			MavenArtifactDownloader rewriteMavenArtifactDownloader, List<Resource> resources) {
		this.projectRoot = baseDir;
		this.buildFile = new MavenBuildFile(pomFile);
		if (dependsOnModels != null) {
			this.dependentProjects.addAll(dependsOnModels);
		}
		this.rewriteMavenArtifactDownloader = rewriteMavenArtifactDownloader;
		this.resources = resources;
		projectId = new ProjectId(getGroupId(), getArtifactId());
	}

	public List<MavenProject> getDependentProjects() {
		return dependentProjects;
	}

	public void setDependencyProjects(List<MavenProject> dependencyProjects) {
		this.dependencyProjects.clear();
		this.dependencyProjects.addAll(dependencyProjects);
	}

	public List<MavenProject> getDependencyProjects() {
		return dependencyProjects;
	}

	public File getFile() {
		return buildFile.getPath().toFile();
	}

	public MavenBuildFile getBuildFile() {
		return buildFile;
	}

	public Path getBasedir() {
		// TODO: 945 Check if this is correct
		return buildFile == null ? null : buildFile.getPath().getParent();
	}

	public void setReactorProjects(List<MavenProject> collected) {
		this.reactorProjects = collected;
	}

	/**
	 * @return all {@link MavenProject}s belonging to the same reactor build.
	 */
	public List<MavenProject> getCollectedProjects() {
		return reactorProjects;
	}

	/**
	 * @return absolute Path of Module
	 */
	public Path getModulePath() {
		return projectRoot.resolve(getModuleDir());
	}

	/**
	 * @return Path for Module relative to {@code baseDir}.
	 */
	public Path getModuleDir() {
		if (getBasedir() == null) {
			return null;
		}
		else if ("pom.xml".equals(LinuxWindowsPathUnifier.relativize(projectRoot, buildFile.getPath()).toString())) {
			return Path.of("");
		}
		else {
			return LinuxWindowsPathUnifier.relativize(projectRoot, buildFile.getPath()).getParent();
		}
	}

	public String getGroupIdAndArtifactId() {
		return this.buildFile.getGroupIdAndArtifactId();
	}

	public Path getPomFilePath() {
		return buildFile.getPath();
	}

	public Plugin getPlugin(String s) {
		return buildFile.getBuild() == null ? null : buildFile.getBuild().getPluginsAsMap().get(s);
	}

	public Properties getProperties() {
		return buildFile.getProperties();
	}

	public MavenRuntimeInformation getMavenRuntimeInformation() {
		// FIXME: 945 implement this
		return new MavenRuntimeInformation();
	}

	public String getName() {
		return buildFile.getName();
	}

	public String getGroupId() {
		return buildFile.getGroupId() == null ? buildFile.getParent().getGroupId() : buildFile.getGroupId();
	}

	public String getArtifactId() {
		return buildFile.getArtifactId();
	}

	/**
	 * FIXME: when the version of parent pom is null (inherited by it's parent) the
	 * version will be null.
	 */
	public String getVersion() {
		return buildFile.getVersion() == null ? buildFile.getParent().getVersion() : buildFile.getVersion();
	}

	@Override
	public String toString() {
		String groupId = buildFile.getGroupId() == null ? buildFile.getParent().getGroupId() : buildFile.getGroupId();
		return groupId + ":" + buildFile.getArtifactId();
	}

	public String getBuildDirectory() {
		String s = buildFile.getBuild() != null ? buildFile.getBuild().getDirectory() : null;
		return s == null ? buildFile.getPath().getParent().resolve("target").toAbsolutePath().normalize().toString()
				: s;
	}

	public String getSourceDirectory() {
		String s = buildFile.getBuild() != null ? buildFile.getBuild().getSourceDirectory() : null;
		return s == null
				? buildFile.getPath().getParent().resolve("src/main/java").toAbsolutePath().normalize().toString() : s;
	}

	public List<Path> getCompileClasspathElements() {
		Scope scope = Scope.Compile;
		return getClasspathElements(scope);
	}

	public List<Path> getTestClasspathElements() {
		return getClasspathElements(Scope.Test);
	}

	@NotNull
	private List<Path> getClasspathElements(Scope scope) {
		Xml.Document pomSourceFile = getSourceFile();
		return getClasspathJars(scope, pomSourceFile);
	}

	@NotNull
	private List<Path> getClasspathJars(Scope scope, Xml.Document pomSourceFile) {
		MavenArtifactDownloader downloader = rewriteMavenArtifactDownloader;
		return getClasspathJars(scope, pomSourceFile, downloader);
	}

	@NotNull
	public static List<Path> getClasspathJars(Scope scope, Xml.Document pomSourceFile,
			MavenArtifactDownloader downloader) {
		MavenResolutionResult pom = pomSourceFile.getMarkers().findFirst(MavenResolutionResult.class).get();
		List<ResolvedDependency> resolvedDependencies = pom.getDependencies().get(scope);
		if (resolvedDependencies != null) {
			return resolvedDependencies
				// FIXME: 945 - deal with dependencies to projects in reactor
				//
				.stream()
				.filter(rd -> rd.getRepository() != null)
				.map(rd -> downloader.downloadArtifact(rd))
				.filter(Objects::nonNull)
				.distinct()
				.toList();
		}
		else {
			return new ArrayList<>();
		}
	}

	public String getTestSourceDirectory() {
		String s = buildFile.getBuild() != null ? buildFile.getBuild().getSourceDirectory() : null;
		return s == null
				? buildFile.getPath().getParent().resolve("src/test/java").toAbsolutePath().normalize().toString() : s;
	}

	public void setSourceFile(Xml.Document sourceFile) {
		this.buildFile.setSourceFile(sourceFile);
	}

	private static List<Resource> listJavaSources(List<Resource> resources, Path sourceDirectory) {
		return resources.stream().filter(whenIn(sourceDirectory)).filter(whenFileNameEndsWithJava()).toList();
	}

	@NotNull
	private static Predicate<Resource> whenFileNameEndsWithJava() {
		return p -> ResourceUtil.getPath(p).getFileName().toString().endsWith(".java");
	}

	@NotNull
	private static Predicate<Resource> whenIn(Path sourceDirectory) {
		return r -> {
			String resourcePath = LinuxWindowsPathUnifier.unifiedPathString(r);
			String sourceDirectoryPath = LinuxWindowsPathUnifier.unifiedPathString(sourceDirectory);
			return resourcePath.startsWith(sourceDirectoryPath);
		};
	}

	public List<Resource> getJavaSourcesInTarget() {
		return listJavaSources(getResources(), getBasedir().resolve(getBuildDirectory()));
	}

	private List<Resource> getResources() {
		return this.resources;
	}

	/**
	 * @return All {@link Resource}s found under {@code src/main/java} of this project.
	 */
	public List<Resource> getMainJavaSources() {
		Path sourceDir = getProjectRoot().resolve(getModuleDir()).resolve("src/main/java");
		return listJavaSources(resources, sourceDir);
	}

	public List<Resource> getTestJavaSources() {
		return listJavaSources(resources, getProjectRoot().resolve(getModuleDir()).resolve("src/test/java"));
	}

	public ProjectId getProjectId() {
		return projectId;
	}

	public Object getProjectEncoding() {
		return buildFile.getProperties().get("project.build.sourceEncoding");
	}

	public Path getProjectRoot() {
		return projectRoot;
	}

	@Deprecated
	public Resource getPomFile() {
		return buildFile.getPomFileResource();
	}

	public Xml.Document getSourceFile() {
		return buildFile.getSourceFile();
	}

	public boolean dependsOn(MavenProject model) {
		return dependentProjects.stream()
			.anyMatch(
					m -> m.getGroupId().equals(model.getGroupId()) && m.getArtifactId().equals(model.getArtifactId()));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		MavenProject that = (MavenProject) o;
		return Objects.equals(buildFile, that.buildFile);
	}

	@Override
	public int hashCode() {
		return Objects.hash(buildFile);
	}

	public class MavenBuildFile extends org.apache.maven.model.Model {

		private final Resource pomFileResource;

		// FIXME: 945 temporary method, model should nopt come from Maven
		@Deprecated
		private final Resource resource;

		private Xml.Document sourceFile;

		private final org.apache.maven.model.Model delegate;

		private static final MavenXpp3Reader XPP_3_READER = new MavenXpp3Reader();

		public MavenBuildFile(Resource pomFileResource) {
			this.pomFileResource = pomFileResource;
			assertPomFile(pomFileResource);
			this.resource = pomFileResource;
			try {
				this.delegate = XPP_3_READER.read(ResourceUtil.getInputStream(resource));
				this.delegate.setPomFile(resource.getFile());
				List<Dependency> dependencies = this.delegate.getDependencies();
				dependencies.forEach(d -> {

				});
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
			catch (XmlPullParserException e) {
				throw new RuntimeException(e);
			}
		}

		private void assertPomFile(Resource resource) {
			if (!LinuxWindowsPathUnifier.unifiedPathString(resource).endsWith("pom.xml")) {
				throw new IllegalArgumentException(
						"Provided resource '%s' is not a pom.xml file.".formatted(ResourceUtil.getPath(resource)));
			}
		}

		public void setSourceFile(Xml.Document sourceFile) {
			this.sourceFile = sourceFile;
		}

		public Xml.Document getSourceFile() {
			return sourceFile;
		}

		public String getContent() {
			return ResourceUtil.getContent(pomFileResource);
		}

		public Path getPath() {
			return ResourceUtil.getPath(pomFileResource);
		}

		public Resource getPomFileResource() {
			return pomFileResource;
		}

		// Model methods
		@Override
		public String toString() {
			return (delegate.getGroupId() == null ? delegate.getParent().getGroupId() : delegate.getGroupId()) + ":"
					+ delegate.getArtifactId();
		}

		@Override
		public String getArtifactId() {
			return delegate.getArtifactId();
		}

		@Override
		public Build getBuild() {
			return delegate.getBuild();
		}

		@Override
		public String getChildProjectUrlInheritAppendPath() {
			return delegate.getChildProjectUrlInheritAppendPath();
		}

		@Override
		public CiManagement getCiManagement() {
			return delegate.getCiManagement();
		}

		@Override
		public List<Contributor> getContributors() {
			return delegate.getContributors();
		}

		@Override
		public String getDescription() {
			return delegate.getDescription();
		}

		@Override
		public List<Developer> getDevelopers() {
			return delegate.getDevelopers();
		}

		@Override
		public String getGroupId() {
			return delegate.getGroupId();
		}

		@Override
		public String getInceptionYear() {
			return delegate.getInceptionYear();
		}

		@Override
		public IssueManagement getIssueManagement() {
			return delegate.getIssueManagement();
		}

		@Override
		public List<License> getLicenses() {
			return delegate.getLicenses();
		}

		@Override
		public List<MailingList> getMailingLists() {
			return delegate.getMailingLists();
		}

		@Override
		public String getModelEncoding() {
			return delegate.getModelEncoding();
		}

		@Override
		public String getModelVersion() {
			return delegate.getModelVersion();
		}

		@Override
		public String getName() {
			String name = delegate.getName();
			if (name == null) {
				name = delegate.getArtifactId();
			}
			return name;
		}

		@Override
		public Organization getOrganization() {
			return delegate.getOrganization();
		}

		@Override
		public String getPackaging() {
			return delegate.getPackaging();
		}

		@Override
		public Parent getParent() {
			return delegate.getParent();
		}

		@Override
		public Prerequisites getPrerequisites() {
			return delegate.getPrerequisites();
		}

		@Override
		public List<Profile> getProfiles() {
			return delegate.getProfiles();
		}

		@Override
		public Scm getScm() {
			return delegate.getScm();
		}

		@Override
		public String getUrl() {
			return delegate.getUrl();
		}

		@Override
		public String getVersion() {
			return delegate.getVersion();
		}

		@Override
		public File getPomFile() {
			return delegate.getPomFile();
		}

		@Override
		public File getProjectDirectory() {
			return delegate.getPomFile().toPath().getParent().toFile();
		}

		@Override
		public String getId() {
			return delegate.getId();
		}

		@Override
		public List<Dependency> getDependencies() {
			return delegate.getDependencies();
		}

		@Override
		public DependencyManagement getDependencyManagement() {
			return delegate.getDependencyManagement();
		}

		@Override
		public DistributionManagement getDistributionManagement() {
			return delegate.getDistributionManagement();
		}

		@Override
		public InputLocation getLocation(Object key) {
			return delegate.getLocation(key);
		}

		@Override
		public List<String> getModules() {
			return delegate.getModules();
		}

		@Override
		public List<Repository> getPluginRepositories() {
			return delegate.getPluginRepositories();
		}

		@Override
		public Properties getProperties() {
			return delegate.getProperties();
		}

		@Override
		public Reporting getReporting() {
			return delegate.getReporting();
		}

		@Override
		public Object getReports() {
			return delegate.getReports();
		}

		@Override
		public List<Repository> getRepositories() {
			return delegate.getRepositories();
		}

		public String getGroupIdAndArtifactId() {
			return getGroupId() + ":" + getArtifactId();
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			MavenBuildFile that = (MavenBuildFile) o;
			Path thisPath = ResourceUtil.getPath(this.pomFileResource);
			Path thatPath = ResourceUtil.getPath(that.pomFileResource);
			return Objects.equals(thisPath, thatPath);
		}

		@Override
		public int hashCode() {
			return Objects.hash(pomFileResource);
		}

	}

}
