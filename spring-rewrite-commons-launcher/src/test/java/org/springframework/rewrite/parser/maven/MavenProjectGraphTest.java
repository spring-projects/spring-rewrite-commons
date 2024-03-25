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

import com.google.common.base.Supplier;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.SoftAssertions;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.openrewrite.maven.cache.LocalMavenArtifactCache;
import org.openrewrite.maven.utilities.MavenArtifactDownloader;
import org.springframework.core.io.Resource;
import org.springframework.rewrite.test.util.DummyResource;

import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.rewrite.parser.maven.MavenProjectGraphTest.MavenProjectBuilder.none;

/**
 * @author Fabian Krüger
 */
class MavenProjectGraphTest {

	private MavenProjectGraph sut = new MavenProjectGraph();

	private static MavenArtifactDownloader artifactDownloader = new RewriteMavenArtifactDownloader(
			new LocalMavenArtifactCache(Path.of(System.getProperty("user.home")).resolve(".m2/repository")), null,
			e -> {
				throw new RuntimeException(e);
			});

	private static MavenProjectFactory projectFactory = new MavenProjectFactory(artifactDownloader);

	@Test
	@DisplayName("single module")
	void singleModule() {
		@Language("xml")
		String pomCode = """
				<?xml version="1.0" encoding="UTF-8"?>
				<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
				    <modelVersion>4.0.0</modelVersion>
				    <groupId>com.acme</groupId>
				    <version>0.1.0-SNAPSHOT</version>
				    <artifactId>example</artifactId>
				</project>
				""";
		Path baseDir = Path.of("./target").toAbsolutePath().normalize();
		Resource pomResource = new DummyResource(baseDir.resolve("pom.xml"), pomCode);
		MavenRuntimeInformation runtimeInformation = new MavenRuntimeInformation("3.9.1");
		MavenProject mavenProject = projectFactory.create(baseDir, pomResource, List.of(pomResource),
				runtimeInformation);
		List<MavenProject> allMavenProjects = List.of(mavenProject);

		Map<MavenProject, Set<MavenProject>> mavenProjectSetMap = sut.from(baseDir, allMavenProjects);

		assertThat(mavenProjectSetMap).hasSize(1);

	}

	@Test
	@DisplayName("Dangling pom should will be collected")
	void danglingPomShouldBeIgnored() {

		@Language("xml")
		String parentPom = """
				<?xml version="1.0" encoding="UTF-8"?>
				<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
				    <modelVersion>4.0.0</modelVersion>
				    <groupId>com.acme</groupId>
				    <artifactId>parent</artifactId>
				    <version>0.1.0-SNAPSHOT</version>
				    <packaging>pom</packaging>
				    <modules>
				        <module>example</module>
				    </modules>
				</project>
				""";

		@Language("xml")
		String modulePom = """
				<?xml version="1.0" encoding="UTF-8"?>
				<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
				    <modelVersion>4.0.0</modelVersion>
				    <parent>
				        <groupId>com.acme</groupId>
				        <artifactId>parent</artifactId>
				        <version>0.1.0-SNAPSHOT</version>
				    </parent>
				    <artifactId>example</artifactId>
				    <dependencies>
				        <dependency>
				            <groupId>com.acme</groupId>
				            <artifactId>dangling</artifactId>
				            <version>0.1.0-SNAPSHOT</version>
				        </dependency>
				    </dependencies>
				</project>
				""";

		@Language("xml")
		String danglingPom = """
				<?xml version="1.0" encoding="UTF-8"?>
				<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
				    <modelVersion>4.0.0</modelVersion>
				    <groupId>com.acme</groupId>
				    <artifactId>dangling</artifactId>
				    <version>0.1.0-SNAPSHOT</version>
				</project>
				""";

		Path baseDir = Path.of(".").toAbsolutePath();
		MavenProjectBuilder.buildMavenProject(baseDir)
			.withResource("pom.xml", parentPom)
			.withResource("dangling/pom.xml", danglingPom)
			.withResource("example/pom.xml", modulePom)
			.afterSort()
			.assertDependencies(parentPom, none())
			.assertDependencies(modulePom, parentPom)
			.assertRemoved(danglingPom)
			.verify();
	}

	@Test
	@DisplayName("Multi module with dependant projects")
	void multiModuleWithDependantProjects() {
		// Modules declared in order a,b,c
		@Language("xml")
		String parentPom = """
				<?xml version="1.0" encoding="UTF-8"?>
				<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
				    <modelVersion>4.0.0</modelVersion>
				    <groupId>com.acme</groupId>
				    <artifactId>parent</artifactId>
				    <version>0.1.0-SNAPSHOT</version>
				    <packaging>pom</packaging>
				    <modules>
				        <module>module-a</module>
				        <module>module-b</module>
				        <module>module-c</module>
				    </modules>
				</project>
				""";

		// Module A depends on C, so C must be built first effectively changing the order
		// in <modules>
		@Language("xml")
		String moduleAPom = """
				<?xml version="1.0" encoding="UTF-8"?>
				<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
				    <modelVersion>4.0.0</modelVersion>
				    <parent>
				        <groupId>com.acme</groupId>
				        <artifactId>parent</artifactId>
				        <version>0.1.0-SNAPSHOT</version>
				    </parent>
				    <artifactId>module-a</artifactId>
				    <dependencies>
				        <dependency>
				            <groupId>com.acme</groupId>
				            <artifactId>module-c</artifactId>
				            <version>0.1.0-SNAPSHOT</version>
				        </dependency>
				    </dependencies>
				</project>
				""";

		@Language("xml")
		String moduleBPom = """
				<?xml version="1.0" encoding="UTF-8"?>
				<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
				    <modelVersion>4.0.0</modelVersion>
				    <parent>
				        <groupId>com.acme</groupId>
				        <artifactId>parent</artifactId>
				        <version>0.1.0-SNAPSHOT</version>
				    </parent>
				    <artifactId>module-b</artifactId>
				</project>
				""";

		// C depends on B
		@Language("xml")
		String moduleCPom = """
				<?xml version="1.0" encoding="UTF-8"?>
				<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
				    <modelVersion>4.0.0</modelVersion>
				    <parent>
				        <groupId>com.acme</groupId>
				        <artifactId>parent</artifactId>
				        <version>0.1.0-SNAPSHOT</version>
				    </parent>
				    <artifactId>module-c</artifactId>
				    <dependencies>
				        <dependency>
				            <groupId>com.acme</groupId>
				            <artifactId>module-b</artifactId>
				            <version>0.1.0-SNAPSHOT</version>
				        </dependency>
				    </dependencies>
				</project>
				""";

		Path baseDir = Path.of(".").toAbsolutePath();
		MavenProjectBuilder.buildMavenProject(baseDir)
			.withResource("pom.xml", parentPom)
			.withResource("module-a/pom.xml", moduleAPom)
			.withResource("module-b/pom.xml", moduleBPom)
			.withResource("module-c/pom.xml", moduleCPom)
			.afterSort()
			.assertDependencies(parentPom, none())
			.assertDependencies(moduleAPom, parentPom, moduleCPom)
			.assertDependencies(moduleBPom, parentPom)
			.assertDependencies(moduleCPom, parentPom, moduleBPom)
			.verify();
	}

	static class MavenProjectBuilder {

		private final MavenProjectGraph sut = new MavenProjectGraph();

		private static MavenArtifactDownloader artifactDownloader = new RewriteMavenArtifactDownloader(
				new LocalMavenArtifactCache(Path.of(System.getProperty("user.home")).resolve(".m2/repository")), null,
				e -> {
					throw new RuntimeException(e);
				});

		;
		private static MavenProjectFactory projectFactory = new MavenProjectFactory(artifactDownloader);

		;

		private final Path baseDir;

		private List<Resource> resources = new ArrayList<>();

		private List<MavenProject> mavenProjects = new ArrayList<>();

		private List<AbstractAssert> assertions = new ArrayList<>();

		private Map<MavenProject, Set<MavenProject>> dependencyGraph;

		private List<Supplier<Executable>> listAssertSupplier;

		private SoftAssertions softAssertions = new SoftAssertions();

		private Set<MavenProject> assertedProjects = new HashSet<>();

		private Set<MavenProject> removedProjects = new HashSet<>();

		private Set<MavenProject> selectedProjects = new HashSet<>();

		public MavenProjectBuilder(Path baseDir) {
			this.baseDir = baseDir;
		}

		public static MavenProjectBuilder buildMavenProject(Path baseDir) {
			return new MavenProjectBuilder(baseDir);
		}

		public static List<String> none() {
			return List.of();
		}

		public MavenProjectBuilder withResource(String path, String content) {
			Resource r = new DummyResource(baseDir.resolve(path), content);
			this.resources.add(r);
			return this;
		}

		public MavenProjectBuilder afterSort() {
			mavenProjects = MavenProjectGraphTest.projectFactory.create(baseDir, resources,
					new MavenRuntimeInformation("3.9.1"));
			dependencyGraph = sut.from(baseDir, mavenProjects);
			return this;
		}

		public MavenProjectBuilder assertDependencies(String pomContent, String... dependantPomContents) {
			MavenProject moduleProject = findMavenProject(pomContent);
			this.assertedProjects.add(moduleProject);
			List<MavenProject> dependantProjects = getDependantProjects(dependantPomContents);
			softAssertions.assertThat(dependencyGraph).containsKey(moduleProject);
			MavenProject[] dependantProjectsArray = dependantProjects.toArray(MavenProject[]::new);
			Set<MavenProject> actualDependantProjects = dependencyGraph.get(moduleProject);
			softAssertions.assertThat(actualDependantProjects).containsExactlyInAnyOrder(dependantProjectsArray);
			return this;
		}

		private List<MavenProject> getDependantProjects(String... dependantPomContents) {
			List<String> contents = Arrays.asList(dependantPomContents);
			return mavenProjects.stream().filter(p -> contents.contains(p.getBuildFile().getContent())).toList();
		}

		private MavenProject findMavenProject(String pomContent) {
			return mavenProjects.stream()
				.filter(p -> pomContent.equals(p.getBuildFile().getContent()))
				.findFirst()
				.get();
		}

		public MavenProjectBuilder assertDependencies(String parentPom, List<String> empty) {
			assertDependencies(parentPom);
			return this;
		}

		public void verify() {
			softAssertions.assertThat(assertedProjects)
				.containsExactlyInAnyOrder(dependencyGraph.keySet().toArray(MavenProject[]::new));
			softAssertions.assertAll();
		}

		public MavenProjectBuilder assertRemoved(String danglingPom) {
			MavenProject mavenProject = findMavenProject(danglingPom);
			this.removedProjects.add(mavenProject);
			this.selectedProjects.remove(mavenProject);
			softAssertions.assertThat(selectedProjects).doesNotContain(mavenProject);
			return this;
		}

	}

}