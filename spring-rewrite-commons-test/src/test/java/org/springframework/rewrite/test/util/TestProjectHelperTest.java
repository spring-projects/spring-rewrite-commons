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
package org.springframework.rewrite.test.util;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Fabian Krüger
 */
class TestProjectHelperTest {

	@TempDir
	Path baseDir;

	@Test
	@DisplayName("getMavenProject")
	void getMavenProject() {
		Path project = TestProjectHelper.getMavenProject("maven-project");
		Path expected = Path.of("./testcode/maven-projects/maven-project").toAbsolutePath().normalize();
		assertThat(project).isEqualTo(expected);
	}

	@Test
	@DisplayName("getGradleProject")
	void getGradleProject() {
		Path project = TestProjectHelper.getGradleProject("maven-project");
		Path expected = Path.of("./testcode/gradle-projects/maven-project").toAbsolutePath().normalize();
		assertThat(project).isEqualTo(expected);
	}

	@Test
	@DisplayName("writeToFileSystem should create dirs")
	void writeToFileSystem() {
		Path baseDir = this.baseDir.resolve("sub-dir");
		TestProjectHelper.createTestProject(baseDir).writeToFilesystem();
		assertThat(baseDir).exists();
	}

	@Test
	@DisplayName("addResource should write file with content")
	void addResource(@TempDir Path tempDir) {
		Path baseDir = tempDir.resolve("sub-dir");
		TestProjectHelper.createTestProject(baseDir)
			.addResource("src/main/resources/some.txt", "content...")
			.writeToFilesystem();
		assertThat(baseDir.resolve("src/main/resources/some.txt")).exists().hasContent("content...");
	}

	@Test
	@DisplayName("initializeGitRepo should init a git repo")
	void initializeGitRepo() throws IOException, GitAPIException {
		Path baseDir = this.baseDir.resolve("sub-dir");
		TestProjectHelper.createTestProject(baseDir).initializeGitRepo().writeToFilesystem();
		assertThat(baseDir.resolve(".git")).exists();
		assertThat(Git.open(baseDir.toFile()).status().call()).isNotNull();
	}

	@Test
	@DisplayName("deleteDirIfExists")
	void deleteDirIfExists() throws IOException {
		Files.writeString(baseDir.resolve("some.txt"), "...");
		assertThat(baseDir.resolve("some.txt")).exists();

		TestProjectHelper.createTestProject(baseDir)
			.deleteDirIfExists()
			.addResource("other.txt", "...")
			.writeToFilesystem();
		assertThat(baseDir.resolve("some.txt")).doesNotExist();
		assertThat(baseDir.resolve("other.txt")).exists();
	}

	@Nested
	@DisabledOnOs(value = OS.WINDOWS, disabledReason = "https://github.com/junit-team/junit5/issues/2811")
	class TextProjectHelper_ExistingGitRepoSupport {

		@TempDir
		Path tempDir;

		@Test
		@DisplayName("cloneGitProject should clone a given GitHub project")
		void cloneGitProject() throws IOException, GitAPIException {
			Path baseDir = tempDir.resolve("sub-dir");
			String url = "https://github.com/spring-guides/gs-rest-service-cors";
			TestProjectHelper.createTestProject(baseDir).cloneGitProject(url).writeToFilesystem();
			assertThat(baseDir.resolve(".git")).exists();
			Repository repository = Git.open(baseDir.toFile()).getRepository();
			assertThat(repository.getBranch().toString()).isEqualTo("main");
			assertThat(repository.getRemoteNames()).containsExactly("origin");
		}

		@Test
		@DisplayName("checkoutTag should checkout given tag name")
		void checkoutTag() throws IOException, GitAPIException {
			Path baseDir = tempDir.resolve("sub-dir");
			String url = "https://github.com/spring-guides/gs-rest-service-cors";
			TestProjectHelper.createTestProject(baseDir)
				.cloneGitProject(url)
				.checkoutTag("2.1.3.RELEASE")
				.writeToFilesystem();
			assertThat(baseDir.resolve(".git")).exists();
			Repository repository = Git.open(baseDir.toFile()).getRepository();
			assertThat(repository.getBranch().toString()).isEqualTo("f8681fa5e4eb35665107d0adac95f79b1b92df47");
		}

		@Test
		@DisplayName("checkoutCommit should checkout given commit hash")
		void checkoutCommit() throws IOException, GitAPIException {
			Path baseDir = tempDir.resolve("sub-dir");
			String url = "https://github.com/spring-guides/gs-rest-service-cors";
			TestProjectHelper.createTestProject(baseDir)
				.cloneGitProject(url)
				.checkoutCommit("f8681fa")
				.writeToFilesystem();
			assertThat(baseDir.resolve(".git")).exists();
			Repository repository = Git.open(baseDir.toFile()).getRepository();
			assertThat(repository.getBranch().toString()).isEqualTo("f8681fa5e4eb35665107d0adac95f79b1b92df47");
		}

	}

}