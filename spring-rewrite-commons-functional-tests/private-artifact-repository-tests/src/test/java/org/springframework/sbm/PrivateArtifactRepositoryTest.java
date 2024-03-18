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
package org.springframework.sbm;

import net.lingala.zip4j.ZipFile;
import org.apache.maven.shared.invoker.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.maven.cache.LocalMavenArtifactCache;
import org.openrewrite.maven.cache.MavenArtifactCache;
import org.openrewrite.maven.tree.MavenRepository;
import org.powermock.reflect.Whitebox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.rewrite.RewriteProjectParser;
import org.springframework.rewrite.boot.autoconfigure.RewriteLauncherConfiguration;
import org.springframework.rewrite.parser.RewriteProjectParsingResult;
import org.springframework.util.FileSystemUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

/**
 * Test that dependencies from a private repository can be downloaded with provided
 * credentials.
 * <p>
 * A secured private repository is started as Docker container and a project depending on
 * a dependency only available through the repository gets parsed. Verifying the types of
 * the dependency were resolved proves that the secured access to the repository was
 * successful and thus credentials provided were decrypted and used. All resources for
 * this test live under {@code ./testcode/maven-projects/private-repository}.
 * <ul>
 * <li>{@code dependency-project} provides the code for the dependency
 * {@code com.acme.dependency:dependency-project:1.0-SNAPSHOT}.</li>
 * <li>{@code dependent-project} provides code that depends on
 * {@code dependency-project}.</li>
 * <li>{@code user.home} provides {@code .m2} and required Maven files
 * {@code settings.xml} and {@code settings-security.xml}</li>
 * <li>{@code reposilite-data} provides files used by reposilite to provide the required
 * configuration and state (auth + dependency)</li>
 * </ul>
 * <p>
 * The test starts a private repository in a Docker container, removes any cached local
 * jars and scans {@code dependent-project}. It then verifies that type information from
 * {@code com.acme.dependency:dependency-project:1.0-SNAPSHOT} was retrieved from the
 * private repository.
 * <p>
 * The credentials are provided through {@code ~/.m2/settings.xml} and
 * {@code ~/.m2/settings-security.xml}. To not interfere the local Maven installation and
 * configuration, the user home directory is redirected to
 * {@code ./testcode/maven-projects/private-repository/user.home}. Therefor the Maven
 * {@code .m2} is pointing to
 * {@code ./testcode/maven-projects/private-repository/user.home/.m2} and configuration
 * files are taken from there.
 *
 * @author Fabian Krüger
 */
@SpringBootTest(classes = { MavenArtifactCacheTestConfig.class, RewriteLauncherConfiguration.class })
@Testcontainers
public class PrivateArtifactRepositoryTest {

	// All test resources live here
	public static final String TESTCODE_DIR = "testcode/maven-projects/private-repository";

	public static final String REPOSITORIES_ZIP = Path
		.of("./testcode/maven-projects/private-repository/reposilite-data.zip")
		.toAbsolutePath()
		.toString();

	public static final String DEPENDENCY_CLASS_FQNAME = "com.example.dependency.DependencyClass";

	private static final String NEW_USER_HOME = Path.of(".")
		.resolve(TESTCODE_DIR + "/user.home")
		.toAbsolutePath()
		.normalize()
		.toString();

	private static final File LOCAL_MAVEN_REPOSITORY = Path.of(NEW_USER_HOME + "/.m2/repository").toFile();

	private static final Path DEPENDENCY_PATH_IN_LOCAL_MAVEN_REPO = Path
		.of(NEW_USER_HOME + "/.m2/repository/com/example/dependency/dependency-project")
		.toAbsolutePath()
		.normalize();

	// File path was too long under Windows when Maven repository was under TESTCODE_DIR +
	// "/reposilite-data"
	// To fix this the repo with jars for reposilite is extracted from a zip to its target
	// dir.
	private static final Path TEMP_DIR;
	static {
		try {
			TEMP_DIR = Files.createTempDirectory("reposilite-data");
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		if (!Files.exists(TEMP_DIR.resolve("reposilite-data"))) {
			TestHelper.unzip(TEMP_DIR);
		}
	}
	private static final Path REPOSILITE_DATA_DIR = TEMP_DIR.resolve("reposilite-data");

	// The private Artifact repository (reposilite) provides the dependency.
	@Container
	static GenericContainer reposilite = new GenericContainer(DockerImageName.parse("dzikoysk/reposilite:3.4.10"))
		.withExposedPorts(8080)
		// copy required config files and cached dependency to repository
		.withCopyFileToContainer(MountableFile.forHostPath(REPOSILITE_DATA_DIR), "/app/data")
		// Create temp user 'user' with password 'secret'
		.withEnv("REPOSILITE_OPTS", "--token user:secret --shared-config shared.configuration.json");

	private static MavenRepository originalMavenRepository;

	private static String originalUserHome;

	@Autowired
	private RewriteProjectParser parser;

	@BeforeAll
	static void beforeAll(@TempDir Path tempDir) {
		originalUserHome = System.getProperty("user.home");
		System.setProperty("user.home", NEW_USER_HOME);
		originalMavenRepository = MavenRepository.MAVEN_LOCAL_DEFAULT;
		// overwrites MavenRepository.MAVEN_LOCAL_DEFAULT which is statically initialized
		// and used previous value of
		// 'user.home'. This constant is used elsewhere to retrieve the default local
		// Maven repo URI.
		// To reflect the new user.home this constant is overwritten using Powermock
		// Whitebox class and then set back
		// in after the test.
		MavenRepository mavenRepository = new MavenRepository("local",
				new File(System.getProperty("user.home") + "/.m2/repository").toURI().toString(), "true", "true", true,
				null, null, false);
		Whitebox.setInternalState(MavenRepository.class, "MAVEN_LOCAL_DEFAULT", mavenRepository);
	}

	@BeforeEach
	void beforeEach() throws IOException {
		Integer port = reposilite.getMappedPort(8080);
		System.out.println("Reposilite: http://localhost:" + port + " login with user:secret");
		TestHelper.renderTemplates(port);
		TestHelper.clearDependencyFromLocalMavenRepo();
	}

	@AfterAll
	static void afterAll() {
		// set back to initial values
		System.setProperty("user.home", originalUserHome);
		Whitebox.setInternalState(MavenRepository.class, "MAVEN_LOCAL_DEFAULT", originalMavenRepository);
		FileSystemUtils.deleteRecursively(LOCAL_MAVEN_REPOSITORY);
	}

	@Test
	@DisplayName("Maven settings should be read from secured private repo")
	void mavenSettingsShouldBeReadFromSecuredPrivateRepo() {
		verifyDependencyDoesNotExistInLocalMavenRepo();
		RewriteProjectParsingResult parsingResult = parseDependentProject();
		verifyDependencyExistsInLocalMavenRepo();
		verifyTypesFromDependencyWereResolved(parsingResult);
	}

	private static void verifyTypesFromDependencyWereResolved(RewriteProjectParsingResult parsingResult) {
		J.CompilationUnit cu = (J.CompilationUnit) parsingResult.sourceFiles()
			.stream()
			.filter(s -> s.getSourcePath().toFile().getName().endsWith(".java"))
			.findFirst()
			.get();
		List<String> fqClassesInUse = cu.getTypesInUse()
			.getTypesInUse()
			.stream()
			.filter(JavaType.FullyQualified.class::isInstance)
			.map(JavaType.FullyQualified.class::cast)
			.map(JavaType.FullyQualified::getFullyQualifiedName)
			.toList();

		// DependencyClass must be in list of used types
		assertThat(fqClassesInUse).contains(DEPENDENCY_CLASS_FQNAME);

		// type should be on classpath
		List<String> classpathFqNames = cu.getMarkers()
			.findFirst(JavaSourceSet.class)
			.get()
			.getClasspath()
			.stream()
			.map(fqn -> fqn.getFullyQualifiedName())
			.toList();
		assertThat(classpathFqNames).contains(DEPENDENCY_CLASS_FQNAME);

		// Type of member should be resolvable
		J.ClassDeclaration classDeclaration = cu.getClasses().get(0);
		JavaType.Class type = (JavaType.Class) ((J.VariableDeclarations) classDeclaration.getBody()
			.getStatements()
			.get(0)).getType();
		assertThat(type.getFullyQualifiedName()).isEqualTo(DEPENDENCY_CLASS_FQNAME);
	}

	private static void verifyDependencyExistsInLocalMavenRepo() {
		Path snapshotDir = DEPENDENCY_PATH_IN_LOCAL_MAVEN_REPO.resolve("1.0-SNAPSHOT").toAbsolutePath().normalize();
		assertThat(snapshotDir).isDirectory();
		assertThat(Arrays.stream(snapshotDir.toFile().listFiles()).map(f -> f.getName()).findFirst().get())
			.matches("dependency-project-1.0-.*\\.jar");
	}

	private RewriteProjectParsingResult parseDependentProject() {
		Path migrateApplication = Path.of(TESTCODE_DIR + "/dependent-project");
		RewriteProjectParsingResult parsingResult = parser.parse(migrateApplication);
		return parsingResult;
	}

	private static void verifyDependencyDoesNotExistInLocalMavenRepo() {
		Path dependencyArtifactDir = DEPENDENCY_PATH_IN_LOCAL_MAVEN_REPO.getParent();
		assertThat(LOCAL_MAVEN_REPOSITORY).isDirectory();
		assertThat(LOCAL_MAVEN_REPOSITORY.listFiles()).isEmpty();
	}

	class TestHelper {

		public static final String $USER_HOME_PLACEHOLDER = "${user.home}";

		public static final String $PORT_PLACEHOLDER = "${port}";

		private static void renderTemplates(Integer port) throws IOException {
			// create pom.xml with correct port for dependency-project
			Path dependencyPomTmplPath = Path.of(TESTCODE_DIR + "/dependency-project/pom.xml.template")
				.toAbsolutePath()
				.normalize();
			Path dependencyPomPath = renderPomXml(port, dependencyPomTmplPath);

			// create pom.xml with correct port for dependent-project
			Path dependentPomTmplPath = Path.of(TESTCODE_DIR + "/dependent-project/pom.xml.template")
				.toAbsolutePath()
				.normalize();
			Path dependentPomPath = renderPomXml(port, dependentPomTmplPath);

			// adjust path in settings.xml
			Path settingsXmlTmplPath = Path.of("./")
				.resolve(NEW_USER_HOME + "/.m2/settings.xml.template")
				.toAbsolutePath()
				.normalize();
			renderSettingsXml(NEW_USER_HOME, settingsXmlTmplPath);
		}

		private static Path renderSettingsXml(String testcodeDir, Path settingsXmlTmplPath) throws IOException {
			String settingsXmlContent = Files.readString(settingsXmlTmplPath);
			String replaced = settingsXmlContent.replace($USER_HOME_PLACEHOLDER, testcodeDir);
			Path settingsXmlPath = Path.of(settingsXmlTmplPath.toString().replace(".template", ""));
			return Files.writeString(settingsXmlPath, replaced);
		}

		private static Path renderPomXml(Integer port, Path pomXmlTmplPath) throws IOException {
			String given = Files.readString(pomXmlTmplPath);
			String replaced = given.replace($PORT_PLACEHOLDER, port.toString());
			Path pomXmlPath = Path.of(pomXmlTmplPath.toString().replace(".template", ""));
			return Files.writeString(pomXmlPath, replaced);
		}

		static void clearDependencyFromLocalMavenRepo() {
			try {
				FileSystemUtils.deleteRecursively(DEPENDENCY_PATH_IN_LOCAL_MAVEN_REPO);
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		void deployDependency(Path pomXmlPath) throws MavenInvocationException {
			InvocationRequest request = new DefaultInvocationRequest();
			request.setPomFile(pomXmlPath.toFile());
			request.setShowErrors(true);
			request.setUserSettingsFile(Path.of(TESTCODE_DIR + "/user.home/.m2/settings-clear-password.xml").toFile());
			request.setGoals(List.of("deploy"));
			request.setLocalRepositoryDirectory(LOCAL_MAVEN_REPOSITORY);
			request.setBatchMode(true);
			Invoker invoker = new DefaultInvoker();
			invoker.setMavenHome(Path.of(TESTCODE_DIR + "/user.home/apache-maven-3.9.5").toFile());
			InvocationResult result = invoker.execute(request);
			if (result.getExitCode() != 0) {
				if (result.getExecutionException() != null) {
					fail("Maven deploy failed.", result.getExecutionException());
				}
				else {
					fail("Maven deploy failed. Exit code: " + result.getExitCode());
				}
			}
		}

		private void buildProject(Path dependentPomPath) throws MavenInvocationException {
			InvocationRequest request = new DefaultInvocationRequest();
			request.setPomFile(dependentPomPath.toFile());
			request.setShowErrors(true);
			request.setUserSettingsFile(Path.of(TESTCODE_DIR + "/user.home/.m2/settings.xml").toFile());
			request.setGoals(List.of("clean", "package"));
			request.setLocalRepositoryDirectory(LOCAL_MAVEN_REPOSITORY);
			request.setBatchMode(true);
			request.setGlobalChecksumPolicy(InvocationRequest.CheckSumPolicy.Warn);
			request.setOutputHandler(s -> System.out.println(s));
			Invoker invoker = new DefaultInvoker();
			invoker.setMavenHome(Path.of(TESTCODE_DIR + "/user.home/apache-maven-3.9.5").toFile());
			InvocationResult result = invoker.execute(request);
			if (result.getExitCode() != 0) {
				if (result.getExecutionException() != null) {
					fail("Maven clean package failed.", result.getExecutionException());
				}
				else {
					fail("Maven  clean package. Exit code: " + result.getExitCode());
				}
			}
		}

		static void unzip(Path repoDir) {
			try {
				;
				ZipFile zipFile = new ZipFile(REPOSITORIES_ZIP);
				zipFile.extractAll(repoDir.toString());
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}

		}

		static void mkdir(Path target) {
			boolean mkdir = target.toFile().mkdir();
			if (!mkdir) {
				throw new RuntimeException("Could not create target dir");
			}
		}

	}

}

// Overwrite MavenArtifactCache to use the modified 'user.home'.
@Configuration
class MavenArtifactCacheTestConfig {

	@Bean
	MavenArtifactCache mavenArtifactCache() {
		return new LocalMavenArtifactCache(Paths.get(System.getProperty("user.home"), ".m2", "repository"));
	}

}