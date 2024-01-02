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
package org.springframework.rewrite.gradle;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.rewrite.gradle.model.GradleProjectData;
import org.springframework.rewrite.gradle.model.SpringRewriteModelBuilder;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ProjectParserTest {

	private static final Logger log = LoggerFactory.getLogger(ProjectParserTest.class);

	private static final Options OPTIONS = new Options(Collections.emptyList(), false, Collections.emptyList(),
			Integer.MAX_VALUE, Collections.emptyList());

	private static Path petclincPath;

	private static Path rewriteGradleModelPath;

	@BeforeAll
	static void setupAll(@TempDir Path dir) throws Exception {
		petclincPath = downloadPetclinic(dir.resolve("petclinic"));
		rewriteGradleModelPath = downloadRewriteGradleToolingModel(dir.resolve("rewrite-gradle-tooling-model"));
	}

	private static Path downloadPetclinic(Path dir) throws Exception {
		Utils.unzip(new URL(
				"https://github.com/spring-projects/spring-petclinic/archive/0aa3adb56f500c41564411c32cd301affe284ecc.zip"),
				dir);
		return Files.list(dir)
			.filter(Files::isDirectory)
			.filter(p -> p.getFileName().toString().startsWith("spring-petclinic-"))
			.findFirst()
			.orElseThrow();
	}

	private static Path downloadRewriteGradleToolingModel(Path dir) throws Exception {
		Utils.unzip(new URL(
				"https://github.com/openrewrite/rewrite-gradle-tooling-model/archive/5907af9f3861a16361a7fc7d91b2ab57dcfea697.zip"),
				dir);
		return Files.list(dir)
			.filter(Files::isDirectory)
			.filter(p -> p.getFileName().toString().startsWith("rewrite-gradle-tooling-model-"))
			.findFirst()
			.orElseThrow();
	}

	@Test
	void sanity() throws Exception {
		GradleProjectData gp = SpringRewriteModelBuilder.forProjectDirectory(GradleProjectData.class,
				petclincPath.toFile(), petclincPath.resolve("build.gradle").toFile());
		List<SourceFile> sources = new ProjectParser(gp, OPTIONS, log)
			.parse(new InMemoryExecutionContext(t -> Assertions.fail("Parser Error", t)))
			.toList();
		assertThat(sources.size()).isEqualTo(114);
	}

	@Test
	void multiProjectRootModel() {
		GradleProjectData gp = SpringRewriteModelBuilder.forProjectDirectory(GradleProjectData.class,
				rewriteGradleModelPath.toFile(), rewriteGradleModelPath.resolve("build.gradle.kts").toFile());

		assertThat(gp.isRootProject()).isTrue();
		assertThat(gp.getGroup()).isEqualTo("org.openrewrite.gradle.tooling");
		assertThat(gp.getName()).isEqualTo("rewrite-gradle-tooling-model");
		assertThat(gp.getVersion()).isEqualTo("0.1.0-dev.0.uncommitted");
		assertThat(gp.getPlugins().size()).isEqualTo(9);
		assertThat(gp.getMavenRepositories().isEmpty()).isTrue();

		assertThat(gp.getSubprojects().size()).isEqualTo(2);

		Iterator<GradleProjectData> itr = gp.getSubprojects().iterator();

		GradleProjectData modelProject = itr.next();
		assertThat(modelProject.isRootProject()).isFalse();
		assertThat(modelProject.getGroup()).isEqualTo("org.openrewrite.gradle.tooling");
		assertThat(modelProject.getName()).isEqualTo("model");
		assertThat(modelProject.getVersion()).isEqualTo("unspecified");
		assertThat(modelProject.getMavenRepositories().size()).isEqualTo(3);
		assertThat(modelProject.getRootProjectDir().toPath().relativize(modelProject.getProjectDir().toPath()).toString()).isEqualTo("model");

		GradleProjectData pluginProject = itr.next();
		assertThat(pluginProject.isRootProject()).isFalse();
		assertThat(pluginProject.getGroup()).isEqualTo("org.openrewrite.gradle.tooling");
		assertThat(pluginProject.getName()).isEqualTo("plugin");
		assertThat(pluginProject.getVersion()).isEqualTo("unspecified");
		assertThat(pluginProject.getMavenRepositories().size()).isEqualTo(3);
		assertThat(modelProject.getRootProjectDir().toPath().relativize(pluginProject.getProjectDir().toPath()).toString()).isEqualTo("plugin");
	}

	@Test
	void multiProjectSubProjectModel() {
		GradleProjectData gp = SpringRewriteModelBuilder.forProjectDirectory(GradleProjectData.class,
				rewriteGradleModelPath.toFile(), rewriteGradleModelPath.resolve("model/build.gradle.kts").toFile());

		assertThat(gp.isRootProject()).isFalse();
		assertThat(gp.getGroup()).isEqualTo("org.openrewrite.gradle.tooling");
		assertThat(gp.getName()).isEqualTo("model");
		assertThat(gp.getVersion()).isEqualTo("unspecified");
		assertThat(gp.getMavenRepositories().size()).isEqualTo(3);
		assertThat(gp.getSubprojects().isEmpty()).isTrue();
		assertThat(gp.getRootProjectDir().toPath().relativize(gp.getProjectDir().toPath()).toString()).isEqualTo("model");
	}

	@Test
	void parseMultiProject() throws Exception {
		GradleProjectData gp = SpringRewriteModelBuilder.forProjectDirectory(GradleProjectData.class,
				rewriteGradleModelPath.toFile(), rewriteGradleModelPath.resolve("build.gradle").toFile());
		List<SourceFile> sources = new ProjectParser(gp, OPTIONS, log)
			.parse(new InMemoryExecutionContext(t -> Assertions.fail("Parser Error", t)))
			.toList();
		assertThat(sources.size()).isEqualTo(48);
	}

}
