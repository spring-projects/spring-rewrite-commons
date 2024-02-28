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
package org.springframework.rewrite.embedder;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.shared.invoker.*;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.maven.MavenMojoProjectParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.rewrite.test.util.TestProjectHelper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MavenExecutorTests {

	Logger logger = LoggerFactory.getLogger(MavenExecutorTests.class);

	private Path projectDir = Path.of("./testcode/maven-projects/simple-spring-boot").toAbsolutePath().normalize();

	@Test
	@DisplayName("simple project")
	void simpleProject() throws InterruptedException {
		Path baseDir = TestProjectHelper.getMavenProject("simple-maven-project");
		CountDownLatch latch = new CountDownLatch(1);
		new MavenExecutor(logger, successEvent -> {
			latch.countDown();
		}).execute(List.of("clean", "package"), baseDir);
		latch.await(10, TimeUnit.SECONDS);
		assertThat(latch.getCount()).isEqualTo(0);
	}

	@Test
	@DisplayName("Problem with Spring Data Flow using original Maven Embedder")
	@Disabled("Fails, there's a problem in Maven Embedder")
	void scdf() {
		Path baseDir = TestProjectHelper.getMavenProject("scdf").resolve("spring-cloud-dataflow-single-step-batch-job");

		MavenCli cli = new MavenCli();
		System.setProperty("maven.multiModuleProjectDirectory", baseDir.toString());
		int i = cli.doMain(List.of("org.springframework.cloud:spring-cloud-dataflow-apps-metadata-plugin:aggregate-metadata",
					"-DskipTests", "-e")
				.toArray(new String[] {}), baseDir.toString(), System.out, System.err);
		System.out.println(i);
	}

	@Test
	@DisplayName("Spring Cloud Data Flow")
	@Disabled("Fails, there's a problem in Maven Embedder")
	void springCloudDataFlow(@TempDir Path tempDir) throws InterruptedException {
		String githubUrl = "https://github.com/spring-cloud/spring-cloud-dataflow.git";
		String gitTag = "v2.10.2";
		TestProjectHelper.createTestProject(tempDir)
			.deleteDirIfExists()
			.cloneGitProject(githubUrl)
			.checkoutTag(gitTag)
			.writeToFilesystem();

		CountDownLatch latch = new CountDownLatch(1);
		new MavenExecutor(logger, successEvent -> {
			System.out.println("Success!");
			latch.countDown();
		}).execute(List.of("clean", "package", "-DskipTests"), tempDir);
		latch.await(3, TimeUnit.MINUTES);
		assertThat(latch.getCount()).isEqualTo(0);
	}

	@Test
	@DisplayName("custom MavenExecutor")
	void customMavenCli() {

		List<MavenProject> allProjects = new ArrayList<>();
		List<String> compileClasspathElements = new ArrayList<>();
		AtomicReference<MavenSession> sessionHolder = new AtomicReference<>();
		AtomicReference<RuntimeInformation> runtimeInformationHolder = new AtomicReference<>();

		MavenExecutor mavenExecutor = new MavenExecutor(logger, new AbstractExecutionListener() {
			@Override
			public void projectSucceeded(ExecutionEvent executionEvent) {
				MavenSession mavenSession = executionEvent.getSession();
				sessionHolder.set(mavenSession);
				PlexusContainer plexusContainer = mavenSession.getContainer();
				RuntimeInformation runtimeInformation = null;
				try {
					runtimeInformation = plexusContainer.lookup(RuntimeInformation.class);
				}
				catch (ComponentLookupException e) {
					throw new RuntimeException(e);
				}
				runtimeInformationHolder.set(runtimeInformation);
				allProjects.addAll(mavenSession.getAllProjects());
				try {
					// Compile classpath elements (target/classes)
					compileClasspathElements.addAll(allProjects.get(0).getCompileClasspathElements());
				}
				catch (DependencyResolutionRequiredException e) {
					throw new RuntimeException(e);
				}
			}
		});

		// TODO: remove requirement to set path through properties
		int result = mavenExecutor.execute(List.of("clean", "install"), projectDir.toString(), System.out, System.err);

		boolean pomCacheEnabled = true;
		@Nullable
		String pomCacheDirectory = Path.of(System.getProperty("user.home")).resolve(".rewrite/cache").toString();
		boolean skipMavenParsing = false;
		Collection<String> exclusions = new ArrayList<>();
		Collection<String> plainTextMasks = Set.of("*.txt");
		int sizeThresholdMb = 10;
		MavenSession mavenSession = sessionHolder.get();
		SettingsDecrypter settingsDecrypter = null;
		boolean runPerSubmodule = false;
		RuntimeInformation runtimeInformation = runtimeInformationHolder.get();
		MavenMojoProjectParser mojoProjectParser = new MavenMojoProjectParser(new Slf4jToMavenLoggerAdapter(logger),
				projectDir, pomCacheEnabled, pomCacheDirectory, runtimeInformation, skipMavenParsing, exclusions,
				plainTextMasks, sizeThresholdMb, mavenSession, settingsDecrypter, runPerSubmodule);

		assertThat(allProjects).hasSize(1);
		assertThat(compileClasspathElements).hasSize(1);
		Path targetClasses = projectDir.resolve("target/classes");
		assertThat(compileClasspathElements.get(0)).isEqualTo(targetClasses.toString());
	}

	@Test
	@DisplayName("Maven Invoker")
	void mavenInvoker() throws MavenInvocationException {
		List defaultGoals = List.of("clean", "install", "-U", "-B", "--fail-at-end");

		StringBuilder sb = new StringBuilder();
		defaultGoals.forEach(g -> sb.append(g).append(" "));
		String defaultGoalsPart = sb.toString();

		StringBuilder orCommand = new StringBuilder();
		orCommand.append("org.openrewrite.maven:rewrite-maven-plugin:run ");
		List<String> activeRecipes = List.of("org.openrewrite.java.RemoveUnusedImports");
		String activeRecipesParam = "-Drewrite.activeRecipes="
				+ activeRecipes.stream().collect(Collectors.joining(","));
		orCommand.append(activeRecipesParam);
		List<String> recipeArtifactCoordinates = List.of();
		if (!recipeArtifactCoordinates.isEmpty()) {
			orCommand.append("\\n");
			orCommand.append("-Drewrite.recipeArtifactCoordinates=");
			recipeArtifactCoordinates.stream().collect(Collectors.joining(","));
		}
		String orCommandPart = orCommand.toString();
		System.out.println(orCommandPart);

		System.out.println("mvn " + defaultGoalsPart + " " + orCommandPart);

		InvocationRequest request = new DefaultInvocationRequest();
		request.addShellEnvironment("MAVEN_OPTS",
				"-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005");
		request.setMavenHome(new File(System.getenv("MAVEN_HOME")));
		request.setShowErrors(true);
		request.setBatchMode(true);
		request.setDebug(true);
		request.setErrorHandler(new InvocationOutputHandler() {
			@Override
			public void consumeLine(String s) throws IOException {
				System.out.println(s);
			}
		});
		request.setPomFile(new File(
				"/Users/fkrueger/projects/forks/spring-rewrite-commons-fork/spring-rewrite-commons-launcher/testcode/maven-projects/resources/pom.xml"));
		// org.openrewrite.maven:rewrite-maven-plugin:5.20.0:run
		// -Drewrite.recipeArtifactCoordinates=org.openrewrite.recipe:rewrite-migrate-java:LATEST,org.openrewrite.recipe:rewrite-hibernate:LATEST,org.openrewrite.recipe:rewrite-java-dependencies:LATEST,org.openrewrite.recipe:rewrite-testing-frameworks:LATEST,org.openrewrite.recipe:rewrite-static-analysis:LATEST,org.openrewrite.recipe:rewrite-spring:LATEST
		// -Drewrite.activeRecipes=org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_7
		request.setGoals(List.of("clean", "package", "-U", "-B", "--fail-at-end",
				"org.openrewrite.maven:rewrite-maven-plugin:run -Drewrite.activeRecipes=org.openrewrite.java.RemoveUnusedImports"
		));

		Invoker invoker = new DefaultInvoker();
		InvocationResult result = invoker.execute(request);
		if (result.getExitCode() != 0) {
			throw new IllegalStateException("Build failed.");
		}
	}

}
