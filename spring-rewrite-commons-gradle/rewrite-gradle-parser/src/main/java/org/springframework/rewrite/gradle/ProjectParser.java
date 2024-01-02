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

import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.gradle.GradleParser;
import org.openrewrite.gradle.toolingapi.GradleSettings;
import org.openrewrite.groovy.GroovyParser;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.internal.JavaTypeCache;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.marker.*;
import org.openrewrite.marker.ci.BuildEnvironment;
import org.openrewrite.polyglot.*;
import org.openrewrite.quark.QuarkParser;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.text.PlainTextParser;
import org.openrewrite.tree.ParseError;
import org.slf4j.Logger;
import org.springframework.rewrite.gradle.model.GradleProjectData;
import org.springframework.rewrite.gradle.model.JavaSourceSetData;
import org.springframework.rewrite.gradle.model.KotlinSourceSetData;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.PathUtils.separatorsToUnix;
import static org.openrewrite.Tree.randomId;

@SuppressWarnings("unused")
public class ProjectParser {

	private static final String LOG_INDENT_INCREMENT = "    ";

	private static final String GROOVY_PLUGIN = "org.gradle.api.plugins.GroovyPlugin";

	private final Options options;

	private final Logger logger;

	private final AtomicBoolean firstWarningLogged = new AtomicBoolean(false);

	protected final Path baseDir;

	protected final GradleProjectData project;

	private final List<Marker> sharedProvenance;

	public ProjectParser(GradleProjectData project, Options options, Logger logger) {
		this.baseDir = repositoryRoot(project);
		this.options = options;
		this.project = project;
		this.logger = logger;

		BuildEnvironment buildEnvironment = BuildEnvironment.build(System::getenv);
		sharedProvenance = Stream
			.of(buildEnvironment, gitProvenance(baseDir, buildEnvironment), OperatingSystemProvenance.current(),
					new BuildTool(randomId(), BuildTool.Type.Gradle, project.getGradleVersion()))
			.filter(Objects::nonNull)
			.collect(toList());
	}

	/**
	 * Attempt to determine the root of the git repository for the given project. Many
	 * Gradle builds co-locate the build root with the git repository root, but that is
	 * not required. If no git repository can be located in any folder containing the
	 * build, the build root will be returned.
	 */
	static Path repositoryRoot(GradleProjectData project) {
		Path buildRoot = project.getProjectDir().toPath();
		Path maybeBaseDir = buildRoot;
		while (maybeBaseDir != null && !Files.exists(maybeBaseDir.resolve(".git"))) {
			maybeBaseDir = maybeBaseDir.getParent();
		}
		if (maybeBaseDir == null) {
			return buildRoot;
		}
		return maybeBaseDir;
	}

	@Nullable
	private GitProvenance gitProvenance(Path baseDir, @Nullable BuildEnvironment buildEnvironment) {
		try {
			return GitProvenance.fromProjectDirectory(baseDir, buildEnvironment);
		}
		catch (Exception e) {
			// Logging at a low level as this is unlikely to happen except in non-git
			// projects, where it is expected
			logger.debug("Unable to determine git provenance", e);
		}
		return null;
	}

	// By accident, we were inconsistent with the names of these properties between this
	// and the maven plugin
	// Check all variants of the name, preferring more-fully-qualified names
	@Nullable
	private String getPropertyWithVariantNames(String property) {
		String maybeProp = System.getProperty("rewrite." + property + "s");
		if (maybeProp == null) {
			maybeProp = System.getProperty("rewrite." + property);
		}
		if (maybeProp == null) {
			maybeProp = System.getProperty(property + "s");
		}
		if (maybeProp == null) {
			maybeProp = System.getProperty(property);
		}
		return maybeProp;
	}

	public Collection<Path> listSources() {
		// Use a sorted collection so that gradle input detection isn't thrown off by
		// ordering
		Set<Path> result = new TreeSet<>(
				omniParser(emptySet()).acceptedPaths(baseDir, project.getProjectDir().toPath()));
		for (JavaSourceSetData sourceSet : project.getJavaSourceSets()) {
			sourceSet.getSources()
				.stream()
				.map(File::toPath)
				.map(Path::toAbsolutePath)
				.map(Path::normalize)
				.forEach(result::add);
		}
		return result;
	}

	public Stream<SourceFile> parse(ExecutionContext ctx) {
		Stream<SourceFile> builder = Stream.of();
		Set<Path> alreadyParsed = new HashSet<>();
		if (project.isRootProject()) {
			for (GradleProjectData subProject : project.getSubprojects()) {
				builder = Stream.concat(builder, parse(subProject, alreadyParsed, ctx));
			}
		}
		builder = Stream.concat(builder, parse(project, alreadyParsed, ctx));

		// log parse errors here at the end, so that we don't log parse errors for files
		// that were excluded
		return builder.map(this::logParseErrors);
	}

	public Stream<SourceFile> parse(GradleProjectData subproject, Set<Path> alreadyParsed, ExecutionContext ctx) {
		String cliPort = System.getenv("MODERNE_CLI_PORT");
		try (ProgressBar progressBar = StringUtils.isBlank(cliPort) ? new NoopProgressBar()
				: new RemoteProgressBarSender(Integer.parseInt(cliPort))) {
			SourceFileStream sourceFileStream = SourceFileStream.build(subproject.getPath(),
					projectName -> progressBar.intermediateResult(":" + projectName));

			Collection<PathMatcher> exclusions = options.exclusions()
				.stream()
				.map(pattern -> subproject.getProjectDir().toPath().getFileSystem().getPathMatcher("glob:" + pattern))
				.collect(toList());
			if (isExcluded(exclusions, baseDir.relativize(subproject.getProjectDir().toPath()))) {
				logger.info("Skipping project {} because it is excluded", subproject.getPath());
				return Stream.empty();
			}

			logger.info("Scanning sources in project {}", subproject.getPath());
			List<NamedStyles> styles = options.styles();
			logger.info("Using active styles {}", styles.stream().map(NamedStyles::getName).collect(toList()));
			List<JavaSourceSetData> sourceSets = subproject.getJavaSourceSets()
				.stream()
				.sorted(Comparator.comparingInt(sourceSet -> {
					if ("main".equals(sourceSet.getName())) {
						return 0;
					}
					else if ("test".equals(sourceSet.getName())) {
						return 1;
					}
					else {
						return 2;
					}
				}))
				.toList();
			List<Marker> projectProvenance;
			if (sourceSets.isEmpty()) {
				projectProvenance = sharedProvenance;
			}
			else {
				projectProvenance = new ArrayList<>(sharedProvenance);
				projectProvenance.add(new JavaProject(randomId(), subproject.getName(), new JavaProject.Publication(
						subproject.getGroup(), subproject.getName(), subproject.getVersion())));
			}

			if (subproject.isMultiPlatformKotlinProject()) {
				sourceFileStream = sourceFileStream
					.concat(parseMultiplatformKotlinProject(subproject, exclusions, alreadyParsed, ctx));
			}

			Set<String> sourceDirs = new HashSet<>();
			for (JavaSourceSetData sourceSet : sourceSets) {
				Stream<SourceFile> sourceSetSourceFiles = Stream.of();
				int sourceSetSize = 0;

				JavaTypeCache javaTypeCache = new JavaTypeCache();
				JavaVersion javaVersion = new JavaVersion(randomId(), sourceSet.getJavaVersionData().getCreatedBy(),
						sourceSet.getJavaVersionData().getVmVendor(),
						sourceSet.getJavaVersionData().getSourceCompatibility(),
						sourceSet.getJavaVersionData().getTargetCompatibility());

				List<Path> unparsedSources = sourceSet.getSources()
					.stream()
					.filter(it -> it.exists() && !alreadyParsed.contains(it.toPath()))
					.flatMap(sourceDir -> {
						try {
							return Files.walk(sourceDir.toPath());
						}
						catch (IOException e) {
							throw new UncheckedIOException(e);
						}
					})
					.filter(Files::isRegularFile)
					.map(Path::toAbsolutePath)
					.map(Path::normalize)
					.distinct()
					.toList();
				List<Path> javaPaths = unparsedSources.stream()
					.filter(it -> it.toString().endsWith(".java") && !alreadyParsed.contains(it))
					.collect(toList());

				Collection<File> implementationClasspath = sourceSet.getImplementationClasspath();
				// The implementation configuration doesn't include build/source
				// directories from project dependencies
				// So mash it and our rewriteImplementation together to get everything
				List<Path> dependencyPaths = Stream
					.concat(implementationClasspath.stream(), sourceSet.getCompileClasspath().stream())
					.map(File::toPath)
					.map(Path::toAbsolutePath)
					.map(Path::normalize)
					.distinct()
					.collect(toList());

				if (!javaPaths.isEmpty()) {
					alreadyParsed.addAll(javaPaths);
					Stream<SourceFile> cus = Stream
						.of((Supplier<JavaParser>) () -> JavaParser.fromJavaVersion()
							.classpath(dependencyPaths)
							.styles(options.styles())
							.typeCache(javaTypeCache)
							.logCompilationWarningsAndErrors(options.logCompilationWarningsAndErrors())
							.build())
						.map(Supplier::get)
						.flatMap(jp -> jp.parse(javaPaths, baseDir, ctx))
						.map(cu -> {
							if (isExcluded(exclusions, cu.getSourcePath()) || cu.getSourcePath()
								.startsWith(baseDir.relativize(subproject.getBuildDir().toPath()))) {
								return null;
							}
							return cu;
						})
						.filter(Objects::nonNull)
						.map(it -> it.withMarkers(it.getMarkers().add(javaVersion)));
					sourceSetSourceFiles = Stream.concat(sourceSetSourceFiles, cus);
					sourceSetSize += javaPaths.size();
					logger.info("Scanned {} Java sources in {}/{}", javaPaths.size(), subproject.getPath(),
							sourceSet.getName());
				}

				if (subproject.getPlugins().stream().anyMatch(gpd -> "org.jetbrains.kotlin.jvm".equals(gpd.getId()))) {
					String excludedProtosPath = subproject.getProjectDir().getPath() + "/protos/build/generated";
					List<Path> kotlinPaths = unparsedSources.stream()
						.filter(it -> !it.toString().startsWith(excludedProtosPath))
						.filter(it -> it.toString().endsWith(".kt"))
						.collect(toList());

					if (!kotlinPaths.isEmpty()) {
						alreadyParsed.addAll(kotlinPaths);
						Stream<SourceFile> cus = Stream
							.of((Supplier<KotlinParser>) () -> KotlinParser.builder()
								.classpath(dependencyPaths)
								.styles(options.styles())
								.typeCache(javaTypeCache)
								.logCompilationWarningsAndErrors(options.logCompilationWarningsAndErrors())
								.build())
							.map(Supplier::get)
							.flatMap(kp -> kp.parse(kotlinPaths, baseDir, ctx))
							.map(cu -> {
								if (isExcluded(exclusions, cu.getSourcePath())) {
									return null;
								}
								return cu;
							})
							.filter(Objects::nonNull)
							.map(it -> it.withMarkers(it.getMarkers().add(javaVersion)));
						sourceSetSourceFiles = Stream.concat(sourceSetSourceFiles, cus);
						sourceSetSize += kotlinPaths.size();
						logger.info("Scanned {} Kotlin sources in {}/{}", kotlinPaths.size(), subproject.getPath(),
								sourceSet.getName());
					}
				}
				if (subproject.getPlugins()
					.stream()
					.anyMatch(gpd -> gpd.getFullyQualifiedClassName().startsWith(GROOVY_PLUGIN))) {
					List<Path> groovyPaths = unparsedSources.stream()
						.filter(it -> it.toString().endsWith(".groovy"))
						.collect(toList());

					if (!groovyPaths.isEmpty()) {
						// Groovy sources are aware of java types that are intermixed in
						// the same directory/sourceSet
						// Include the build directory containing class files so these
						// definitions are available
						List<Path> dependenciesWithBuildDirs = Stream
							.concat(dependencyPaths.stream(), sourceSet.getClassesDirs().stream().map(File::toPath))
							.collect(toList());

						alreadyParsed.addAll(groovyPaths);

						Stream<SourceFile> cus = Stream
							.of((Supplier<GroovyParser>) () -> GroovyParser.builder()
								.classpath(dependenciesWithBuildDirs)
								.styles(options.styles())
								.typeCache(javaTypeCache)
								.logCompilationWarningsAndErrors(false)
								.build())
							.map(Supplier::get)
							.flatMap(gp -> gp.parse(groovyPaths, baseDir, ctx))
							.map(cu -> {
								if (isExcluded(exclusions, cu.getSourcePath())) {
									return null;
								}
								return cu;
							})
							.filter(Objects::nonNull)
							.map(it -> it.withMarkers(it.getMarkers().add(javaVersion)));
						sourceSetSourceFiles = Stream.concat(sourceSetSourceFiles, cus);
						sourceSetSize += groovyPaths.size();
						logger.info("Scanned {} Groovy sources in {}/{}", groovyPaths.size(), subproject.getPath(),
								sourceSet.getName());
					}
				}

				for (File resourcesDir : sourceSet.getSourceDirectories()) {
					if (resourcesDir.exists() && !alreadyParsed.contains(resourcesDir.toPath())) {
						OmniParser omniParser = omniParser(alreadyParsed);
						List<Path> accepted = omniParser.acceptedPaths(baseDir, resourcesDir.toPath());
						sourceSetSourceFiles = Stream.concat(sourceSetSourceFiles,
								omniParser.parse(accepted, baseDir, new InMemoryExecutionContext()));
						alreadyParsed.addAll(accepted);
						sourceSetSize += accepted.size();
					}
				}

				JavaSourceSet sourceSetProvenance = JavaSourceSet.build(sourceSet.getName(), dependencyPaths,
						javaTypeCache, false);
				sourceFileStream = sourceFileStream.concat(sourceSetSourceFiles.map(addProvenance(sourceSetProvenance)),
						sourceSetSize);
				// Some source sets get misconfigured to have the same directories as
				// other source sets
				// This causes duplicate source files to be parsed, so once a source set
				// has been parsed exclude it from future parsing
				for (File file : sourceSet.getSourceDirectories()) {
					alreadyParsed.add(file.toPath());
				}
			}
			SourceFileStream gradleFiles = parseGradleFiles(exclusions, alreadyParsed, ctx);
			sourceFileStream = sourceFileStream.concat(gradleFiles, gradleFiles.size());

			SourceFileStream nonProjectResources = parseNonProjectResources(subproject, alreadyParsed, ctx,
					projectProvenance, sourceFileStream);
			sourceFileStream = sourceFileStream.concat(nonProjectResources, nonProjectResources.size());

			progressBar.setMax(sourceFileStream.size());
			return sourceFileStream.map(addProvenance(projectProvenance)).peek(it -> progressBar.step());
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private GradleParser gradleParser() {
		List<Path> settingsClasspath = project.getSettingsClasspath().stream().map(File::toPath).collect(toList());
		List<Path> buildscriptClasspath = project.getBuildscriptClasspath()
			.stream()
			.map(File::toPath)
			.collect(toList());

		return GradleParser.builder()
			.groovyParser(GroovyParser.builder()
				.typeCache(new JavaTypeCache())
				.styles(options.styles())
				.logCompilationWarningsAndErrors(false))
			.buildscriptClasspath(buildscriptClasspath)
			.settingsClasspath(settingsClasspath)
			.build();
	}

	private SourceFileStream parseGradleFiles(Collection<PathMatcher> exclusions, Set<Path> alreadyParsed,
			ExecutionContext ctx) {
		Stream<SourceFile> sourceFiles = Stream.empty();
		int gradleFileCount = 0;

		GradleParser gradleParser = null;
		if (project.getBuildscriptFile() != null) {
			File buildGradleFile = project.getBuildscriptFile();
			Path buildScriptPath = baseDir.relativize(buildGradleFile.toPath());
			if (!isExcluded(exclusions, buildScriptPath) && buildGradleFile.exists()) {
				alreadyParsed.add(buildScriptPath);
				if (buildScriptPath.toString().endsWith(".gradle")) {
					gradleParser = gradleParser();
					sourceFiles = gradleParser.parse(singleton(buildGradleFile.toPath()), baseDir, ctx);
				}
				else {
					sourceFiles = PlainTextParser.builder()
						.build()
						.parse(singleton(buildGradleFile.toPath()), baseDir, ctx);
				}
				gradleFileCount++;
				sourceFiles = sourceFiles.map(sourceFile -> sourceFile.withMarkers(sourceFile.getMarkers()
					.add(org.openrewrite.gradle.marker.GradleProject.fromToolingModel(project))));
				alreadyParsed.add(project.getBuildscriptFile().toPath());
			}
		}

		if (project.isRootProject()) {
			File settingsGradleFile = new File(project.getProjectDir(), "settings.gradle");
			File settingsGradleKtsFile = new File(project.getProjectDir(), "settings.gradle.kts");
			GradleSettings gs = project.getGradleSettings();
			if (settingsGradleFile.exists()) {
				Path settingsPath = baseDir.relativize(settingsGradleFile.toPath());
				if (gradleParser == null) {
					gradleParser = gradleParser();
				}
				if (!isExcluded(exclusions, settingsPath)) {
					sourceFiles = Stream.concat(sourceFiles,
							gradleParser.parse(singleton(settingsGradleFile.toPath()), baseDir, ctx).map(sourceFile -> {
								if (gs == null) {
									return sourceFile;
								}
								return sourceFile.withMarkers(sourceFile.getMarkers()
									.add(org.openrewrite.gradle.marker.GradleSettings.fromToolingModel(gs)));
							}));
					gradleFileCount++;
				}
				alreadyParsed.add(settingsGradleFile.toPath());
			}
			else if (settingsGradleKtsFile.exists()) {
				Path settingsPath = baseDir.relativize(settingsGradleKtsFile.toPath());
				if (!isExcluded(exclusions, settingsPath)) {
					sourceFiles = Stream.concat(sourceFiles,
							PlainTextParser.builder()
								.build()
								.parse(singleton(settingsGradleKtsFile.toPath()), baseDir, ctx)
								.map(sourceFile -> {
									if (gs == null) {
										return sourceFile;
									}
									return sourceFile.withMarkers(sourceFile.getMarkers()
										.add(org.openrewrite.gradle.marker.GradleSettings.fromToolingModel(gs)));
								}));
					gradleFileCount++;
				}
				alreadyParsed.add(settingsGradleKtsFile.toPath());
			}
		}

		return SourceFileStream.build("", s -> {
		}).concat(sourceFiles, gradleFileCount);
	}

	protected SourceFileStream parseNonProjectResources(GradleProjectData subproject, Set<Path> alreadyParsed,
			ExecutionContext ctx, List<Marker> projectProvenance, Stream<SourceFile> sourceFiles) {
		// Collect any additional yaml/properties/xml files that are NOT already in a
		// source set.
		OmniParser omniParser = omniParser(alreadyParsed);
		List<Path> accepted = omniParser.acceptedPaths(baseDir, subproject.getProjectDir().toPath());
		return SourceFileStream.build("", s -> {
		}).concat(omniParser.parse(accepted, baseDir, ctx), accepted.size());
	}

	private OmniParser omniParser(Set<Path> alreadyParsed) {
		return OmniParser
			.builder(OmniParser.defaultResourceParsers(),
					PlainTextParser.builder().plainTextMasks(baseDir, options.plainTextMasks()).build(),
					QuarkParser.builder().build())
			.exclusionMatchers(pathMatchers(baseDir, mergeExclusions(project, baseDir, options)))
			.exclusions(alreadyParsed)
			.sizeThresholdMb(options.sizeThresholdMb())
			.build();
	}

	private static Collection<String> mergeExclusions(GradleProjectData project, Path baseDir, Options options) {
		return Stream
			.concat(project.getSubprojects()
				.stream()
				.map(subproject -> separatorsToUnix(
						baseDir.relativize(subproject.getProjectDir().toPath()).toString())),
					options.exclusions().stream())
			.collect(toList());
	}

	private Collection<PathMatcher> pathMatchers(Path basePath, Collection<String> pathExpressions) {
		return pathExpressions.stream()
			.map(o -> basePath.getFileSystem().getPathMatcher("glob:" + o))
			.collect(toList());
	}

	private SourceFileStream parseMultiplatformKotlinProject(GradleProjectData subproject,
			Collection<PathMatcher> exclusions, Set<Path> alreadyParsed, ExecutionContext ctx) {
		SourceFileStream sourceFileStream = SourceFileStream.build(subproject.getPath(), s -> {
		});
		for (KotlinSourceSetData sourceSet : project.getKotlinSourceSets()) {
			List<Path> kotlinPaths = sourceSet.getKotlin()
				.stream()
				.filter(it -> it.isFile() && it.getName().endsWith(".kt"))
				.map(File::toPath)
				.map(Path::toAbsolutePath)
				.map(Path::normalize)
				.collect(toList());

			// The implementation configuration doesn't include build/source directories
			// from project dependencies
			// So mash it and our rewriteImplementation together to get everything
			List<Path> dependencyPaths = Stream
				.concat(sourceSet.getImplementationClasspath().stream(), sourceSet.getCompileClasspath().stream())
				.map(File::toPath)
				.map(Path::toAbsolutePath)
				.map(Path::normalize)
				.distinct()
				.collect(toList());

			if (!kotlinPaths.isEmpty()) {
				JavaTypeCache javaTypeCache = new JavaTypeCache();
				KotlinParser kp = KotlinParser.builder()
					.classpath(dependencyPaths)
					.styles(options.styles())
					.typeCache(javaTypeCache)
					.logCompilationWarningsAndErrors(options.logCompilationWarningsAndErrors())
					.build();

				Stream<SourceFile> cus = kp.parse(kotlinPaths, baseDir, ctx);
				alreadyParsed.addAll(kotlinPaths);
				cus = cus.map(cu -> {
					if (isExcluded(exclusions, cu.getSourcePath())) {
						return null;
					}
					return cu;
				}).filter(Objects::nonNull);
				JavaSourceSet sourceSetProvenance = JavaSourceSet.build(sourceSet.getName(), dependencyPaths,
						javaTypeCache, false);

				sourceFileStream = sourceFileStream.concat(cus.map(addProvenance(sourceSetProvenance)),
						kotlinPaths.size());
				logger.info("Scanned {} Kotlin sources in {}/{}", kotlinPaths.size(), subproject.getPath(),
						sourceSet.getName());
			}
		}

		return sourceFileStream;
	}

	private SourceFile logParseErrors(SourceFile source) {
		if (source instanceof ParseError) {
			if (firstWarningLogged.compareAndSet(false, true)) {
				logger.warn("There were problems parsing some source files, run with --info to see full stack traces");
			}
			logger.warn("There were problems parsing " + source.getSourcePath());
		}
		return source;
	}

	private boolean isExcluded(Collection<PathMatcher> exclusions, Path path) {
		for (PathMatcher excluded : exclusions) {
			if (excluded.matches(path)) {
				return true;
			}
		}
		// PathMather will not evaluate the path "build.gradle" to be matched by the
		// pattern "**/build.gradle"
		// This is counter-intuitive for most users and would otherwise require separate
		// exclusions for files at the root and files in subdirectories
		if (!path.isAbsolute() && !path.startsWith(File.separator)) {
			return isExcluded(exclusions, Paths.get("/" + path));
		}
		return false;
	}

	private <T extends SourceFile> UnaryOperator<T> addProvenance(List<Marker> projectProvenance) {
		return s -> {
			Markers m = s.getMarkers();
			for (Marker marker : projectProvenance) {
				m = m.addIfAbsent(marker);
			}
			return s.withMarkers(m);
		};
	}

	private <T extends SourceFile> UnaryOperator<T> addProvenance(Marker sourceSet) {
		return s -> {
			Markers m = s.getMarkers();
			m = m.addIfAbsent(sourceSet);
			return s.withMarkers(m);
		};
	}

	public Path getBaseDir() {
		return baseDir;
	}

}
