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

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.springframework.core.io.Resource;
import org.springframework.rewrite.utils.ResourceUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Helper to set up test projects for OpenRewrite migrations.
 *
 * @author Fabian Krüger
 */
public class TestProjectHelper {

	public static final String TESTCODE_MAVEN_PROJECTS = "./testcode/maven-projects/";

	private static final String TESTCODE_GRADLE_PROJECTS = "./testcode/gradle-projects/";

	private final Path targetDir;

	private List<Resource> resources = new ArrayList<>();

	private boolean initializeGitRepo;

	private String gitUrl;

	private String gitTag;

	private boolean deleteDirIfExists = false;

	private String gitHash = null;

	public TestProjectHelper(Path targetDir) {
		this.targetDir = targetDir;
	}

	/**
	 * Returns the absolute path to {@code ./testcode/maven-projects/<givenProjectDir>}
	 * @param givenProjectDir
	 */
	public static Path getMavenProject(String givenProjectDir) {
		return Path.of(TESTCODE_MAVEN_PROJECTS).resolve(givenProjectDir).toAbsolutePath().normalize();
	}

	/**
	 * Returns the absolute path to {@code ./testcode/gradle-projects/<givenProjectDir>}
	 * @param givenProjectDir
	 */
	public static Path getGradleProject(String givenProjectDir) {
		return Path.of(TESTCODE_GRADLE_PROJECTS).resolve(givenProjectDir).toAbsolutePath().normalize();
	}

	public static TestProjectHelper createTestProject(Path targetDir) {
		return new TestProjectHelper(targetDir);
	}

	public static TestProjectHelper createTestProject(String targetDir) {
		return new TestProjectHelper(Path.of(targetDir).toAbsolutePath().normalize());
	}

	public TestProjectHelper withResources(Resource... resources) {
		this.resources.addAll(Arrays.asList(resources));
		return this;
	}

	public TestProjectHelper initializeGitRepo() {
		this.initializeGitRepo = true;
		return this;
	}

	public TestProjectHelper cloneGitProject(String url) {
		this.gitUrl = url;
		return this;
	}

	public TestProjectHelper checkoutCommit(String gitHash) {
		this.gitHash = gitHash;
		return this;
	}

	public TestProjectHelper checkoutTag(String tag) {
		this.gitTag = tag;
		return this;
	}

	public TestProjectHelper deleteDirIfExists() {
		this.deleteDirIfExists = true;
		return this;
	}

	public void writeToFilesystem() {
		if (deleteDirIfExists) {
			try {
				FileUtils.deleteDirectory(targetDir.toFile());
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		if (initializeGitRepo) {
			try {
				Git.init().setDirectory(targetDir.toFile()).call();
			}
			catch (GitAPIException e) {
				throw new RuntimeException(e);
			}
		}
		else if (gitUrl != null) {
			try {
				File directory = targetDir.toFile();
				Git git = Git.cloneRepository().setDirectory(directory).setURI(this.gitUrl).call();

				if (gitTag != null) {
					git.checkout().setName("refs/tags/" + gitTag).setCreateBranch(false).call();
				}
				else if (gitHash != null) {
					checkoutCommit(git, gitHash);
				}
			}
			catch (GitAPIException e) {
				throw new RuntimeException(e);
			}
		}
		ResourceUtil.write(targetDir, resources);
	}

	private void checkoutCommit(Git git, String startingGitHash) {
		try {
			Ref ref = git.checkout().setName(startingGitHash).call();
		}
		catch (GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	private void resetRepo(Git git, String startingGitHash) {
		try {
			git.reset().setRef(startingGitHash).setMode(ResetCommand.ResetType.HARD).call();
		}
		catch (GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	public TestProjectHelper addResource(String relativePath, String content) {
		DummyResource dummyResource = new DummyResource(targetDir.resolve(relativePath), content);
		this.resources.add(dummyResource);
		return this;
	}

}
