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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.ConsoleAppender;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.cli.logging.Slf4jStdoutLogger;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MavenInvokerTestTests {

	Logger logger = LoggerFactory.getLogger(MavenInvokerTestTests.class);

	private Path projectDir = Path.of("./testcode/maven-projects/simple-spring-boot").toAbsolutePath().normalize();

	@Test
	@DisplayName("simple project")
	void simpleProject() {
		Path baseDir = TestProjectHelper.getMavenProject("simple-maven-project");
		new MavenExecutor(logger, successEvent -> {
			System.out.println("Success!");
		}).execute(List.of("clean", "package"), baseDir);
	}

	@Test
	@DisplayName("Problem with Spring Data Flow using original Maven Embedder")
	@Disabled("Fails, there's a problem in Maven Embedder")
	void scdf() {
		Path baseDir = TestProjectHelper.getMavenProject("scdf").resolve("spring-cloud-dataflow-single-step-batch-job");

		MavenCli cli = new MavenCli();
		System.setProperty("maven.multiModuleProjectDirectory", baseDir.toString());
		int i = cli
			.doMain(List.of("org.springframework.cloud:spring-cloud-dataflow-apps-metadata-plugin:aggregate-metadata",
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

}
