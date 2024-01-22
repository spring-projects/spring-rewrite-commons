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
package org.springframework.rewrite.parsers.maven;

import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.FileAttributes;
import org.openrewrite.Parser;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.internal.JavaTypeCache;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.Generated;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.Markers;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.tree.ParsingExecutionContextView;
import org.openrewrite.xml.tree.Xml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.rewrite.parsers.*;
import org.springframework.rewrite.utils.LinuxWindowsPathUnifier;
import org.springframework.rewrite.utils.ResourceUtil;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.openrewrite.Tree.randomId;

/**
 * @author Fabian Krüger
 */
public class MavenModuleParser {

	private static final Logger LOGGER = LoggerFactory.getLogger(MavenModuleParser.class);

	private final SpringRewriteProperties springRewriteProperties;

	public MavenModuleParser(SpringRewriteProperties springRewriteProperties) {
		this.springRewriteProperties = springRewriteProperties;
	}

	public ModuleParsingResult parseModule(Path baseDir, List<Resource> resources, MavenProject currentProject,
			Xml.Document moduleBuildFile, List<Marker> provenanceMarkers, List<NamedStyles> styles,
			ExecutionContext executionContext, Map<MavenProject, ModuleParsingResult> parsingResultsMap) {

		List<SourceFile> sourceFiles = new ArrayList<>();
		// 146:149: get source encoding from maven
		// TDOD:
		// String s =
		// moduleBuildFile.getMarkers().findFirst(MavenResolutionResult.class).get().getPom().getProperties().get("project.build.sourceEncoding");
		// if (mavenSourceEncoding != null) {
		// ParsingExecutionContextView.view(ctx).setCharset(Charset.forName(mavenSourceEncoding.toString()));
		// }
		Object mavenSourceEncoding = currentProject.getProjectEncoding();
		if (mavenSourceEncoding != null) {
			ParsingExecutionContextView.view(executionContext)
				.setCharset(Charset.forName(mavenSourceEncoding.toString()));
		}

		boolean logCompilationWarningsAndErrors = springRewriteProperties.isLogCompilationWarningsAndErrors();
		JavaParser.Builder<? extends JavaParser, ?> javaParserBuilder = JavaParser.fromJavaVersion()
			.styles(styles)
			.logCompilationWarningsAndErrors(logCompilationWarningsAndErrors);

		Path buildFilePath = currentProject.getBasedir().resolve(moduleBuildFile.getSourcePath());
		LOGGER.info("Parsing module " + buildFilePath);
		// these paths will be ignored by ResourceParser
		Set<Path> skipResourceScanDirs = pathsToOtherMavenProjects(currentProject, buildFilePath);
		RewriteResourceParser rp = new RewriteResourceParser(baseDir, springRewriteProperties.getIgnoredPathPatterns(),
				springRewriteProperties.getPlainTextMasks(), springRewriteProperties.getSizeThresholdMb(),
				skipResourceScanDirs, javaParserBuilder.clone(), executionContext);

		Set<Path> alreadyParsed = new HashSet<>();
		Path moduleBuildFilePath = baseDir.resolve(moduleBuildFile.getSourcePath());
		alreadyParsed.add(moduleBuildFilePath);
		alreadyParsed.addAll(skipResourceScanDirs);

		SourceSetParsingResult mainSourcesParsingResult = parseMainSourceSet(baseDir, currentProject, javaParserBuilder,
				parsingResultsMap, executionContext, alreadyParsed, provenanceMarkers, resources, rp);

		SourceSetParsingResult testSourcesParsingResult = parseTestSourceSet(baseDir, currentProject, javaParserBuilder,
				parsingResultsMap, executionContext, alreadyParsed, provenanceMarkers, resources, rp,
				mainSourcesParsingResult);
		// Collect the dirs of modules parsed in previous steps

		// parse other project resources
		Stream<SourceFile> parsedResourceFiles = rp.parse(moduleBuildFilePath.getParent(), resources, alreadyParsed)
			// FIXME: handle generated sources
			.map(addProvenance(baseDir, provenanceMarkers, null));

		List<SourceFile> mainAndTestSources = mergeAndFilterExcluded(baseDir,
				springRewriteProperties.getIgnoredPathPatterns(), mainSourcesParsingResult.sourceFiles(),
				testSourcesParsingResult.sourceFiles());
		List<SourceFile> resourceFilesList = parsedResourceFiles.toList();
		sourceFiles.addAll(mainAndTestSources);
		sourceFiles.addAll(resourceFilesList);

		ModuleParsingResult moduleParsingResult = new ModuleParsingResult(currentProject, mainSourcesParsingResult,
				testSourcesParsingResult, resourceFilesList);
		return moduleParsingResult;
	}

	/**
	 * Parse main source set {@code src/main} from current module. The classpath for Java
	 * sources is created from jars and compilation units of dependency project previously
	 * parsed. The parsed java sources are collected and provided with the result and can
	 * be used by subsequent parse to build tha classpath.
	 */
	SourceSetParsingResult parseMainSourceSet(@Nullable Path baseDir, MavenProject currentProject,
			JavaParser.Builder<? extends JavaParser, ?> javaParserBuilder,
			Map<MavenProject, ModuleParsingResult> parsingResultsMap, ExecutionContext executionContext,
			Set<Path> alreadyParsed, List<Marker> provenanceMarkers, List<Resource> resources,
			RewriteResourceParser rp) {
		// collect and prepare all types for classpath and TypeCache
		// java sources in current source set
		List<Resource> javaSourcesInSrc = currentProject.getMainJavaSources();
		// jars from dependencies
		List<Path> classpathJars = currentProject.getCompileClasspathElements();

		LOGGER.debug("Dependencies on main classpath: %s".formatted(classpathJars));
		javaParserBuilder.classpath(classpathJars);

		// sources from other dependency modules
		List<SourceFile> sourceFilesFromOtherModules = currentProject.getDependencyProjects()
			.stream()
			// get their parsing result
			.map(project -> parsingResultsMap.get(project))
			.flatMap(result -> result.mainSourcesParsingResult().sourceFiles().stream())
			.toList();

		String[] dependsOnSources = sourceFilesFromOtherModules.stream()
			.map(SourceFile::printAll)
			.toArray(String[]::new);
		javaParserBuilder.dependsOn(dependsOnSources);

		JavaTypeCache typeCache = getJavaTypeCache(currentProject, parsingResultsMap, sourceFilesFromOtherModules);
		javaParserBuilder.typeCache(typeCache);

		Set<JavaType.FullyQualified> sourceSetClassesCp = new HashSet<>();

		// Add test sources from dependency projects to classpath
		sourceFilesFromOtherModules.stream()
			.filter(J.CompilationUnit.class::isInstance)
			.map(J.CompilationUnit.class::cast)
			.flatMap(s -> s.getClasses().stream())
			.map(J.ClassDeclaration::getType)
			.forEach(sourceSetClassesCp::add);

		return parseSourceSet(baseDir, currentProject, javaSourcesInSrc, javaParserBuilder, sourceSetClassesCp,
				executionContext, alreadyParsed, classpathJars, typeCache, provenanceMarkers, "main", resources, rp,
				"src/main");
	}

	/**
	 * Parse test source set {@code src/test} from current module. The classpath for Java
	 * sources is created from jars and compilation units of dependency project previously
	 * parsed. The parsed java sources are collected and provided with the result and can
	 * be used by subsequent parse to build tha classpath.
	 */
	SourceSetParsingResult parseTestSourceSet(@Nullable Path baseDir, MavenProject currentProject,
			JavaParser.Builder<? extends JavaParser, ?> javaParserBuilder,
			Map<MavenProject, ModuleParsingResult> parsingResultsMap, ExecutionContext executionContext,
			Set<Path> alreadyParsed, List<Marker> provenanceMarkers, List<Resource> resources, RewriteResourceParser rp,
			SourceSetParsingResult mainSourcesParsingResult) {
		// collect and prepare all types for classpath and TypeCache
		// java sources in current source set
		List<Resource> javaSourcesInSrc = currentProject.getTestJavaSources();
		// jars from dependencies
		List<Path> classpathJars = currentProject.getTestClasspathElements();

		LOGGER.debug("Dependencies on main classpath: %s".formatted(classpathJars));
		javaParserBuilder.classpath(classpathJars);

		// sources from other dependency modules
		List<SourceFile> sourceFilesFromOtherModules = currentProject.getDependencyProjects()
			.stream()
			// get their parsing result
			.map(project -> parsingResultsMap.get(project))
			.flatMap(result -> {
				return Stream.concat(result.mainSourcesParsingResult().sourceFiles().stream(),
						result.testSourcesParsingResult().sourceFiles().stream());
			})
			.toList();

		List<SourceFile> sourceFilesFromMain = mainSourcesParsingResult.sourceFiles();
		String[] dependsOnSources = Stream.concat(sourceFilesFromMain.stream(), sourceFilesFromOtherModules.stream())
			.map(SourceFile::printAll)
			.toArray(String[]::new);
		javaParserBuilder.dependsOn(dependsOnSources);

		JavaTypeCache typeCache = getJavaTypeCache(currentProject, parsingResultsMap, sourceFilesFromOtherModules);
		javaParserBuilder.typeCache(typeCache);

		Set<JavaType.FullyQualified> sourceSetClassesCp = new HashSet<>();

		// Add test sources from dependency projects to classpath
		Stream.concat(sourceFilesFromMain.stream(), sourceFilesFromOtherModules.stream())
			.filter(J.CompilationUnit.class::isInstance)
			.map(J.CompilationUnit.class::cast)
			.flatMap(s -> s.getClasses().stream())
			.map(J.ClassDeclaration::getType)
			.forEach(sourceSetClassesCp::add);

		return parseSourceSet(baseDir, currentProject, javaSourcesInSrc, javaParserBuilder, sourceSetClassesCp,
				executionContext, alreadyParsed, classpathJars, typeCache, provenanceMarkers, "test", resources, rp,
				"src/test");
	}

	SourceSetParsingResult parseSourceSet(@Nullable Path baseDir, MavenProject currentProject,
			List<Resource> javaSourcesInSrc, JavaParser.Builder<? extends JavaParser, ?> javaParserBuilder,
			Set<JavaType.FullyQualified> localClassesCp, ExecutionContext executionContext, Set<Path> alreadyParsed,
			List<Path> classpathJars, JavaTypeCache typeCache, List<Marker> provenanceMarkers, String sourceSetName,
			List<Resource> resources, RewriteResourceParser rp, String sourceDir) {
		// collect source files from module src dir
		List<Resource> javaSources = new ArrayList<>();
		List<Resource> javaSourcesInTarget = currentProject.getJavaSourcesInTarget();
		javaSources.addAll(javaSourcesInTarget);
		javaSources.addAll(javaSourcesInSrc);

		Iterable<Parser.Input> inputs = javaSources.stream().map(r -> {
			FileAttributes fileAttributes = null;
			Path path = ResourceUtil.getPath(r);
			boolean isSynthetic = Files.exists(path);
			Supplier<InputStream> inputStreamSupplier = () -> ResourceUtil.getInputStream(r);
			Parser.Input input = new Parser.Input(path, fileAttributes, inputStreamSupplier, isSynthetic);
			return input;
		}).toList();

		// collecting parsed compilation units to the classpath (localClassesCp).
		List<? extends SourceFile> cus = javaParserBuilder.build()
			.parseInputs(inputs, baseDir, executionContext)
			.peek(s -> {
				((J.CompilationUnit) s).getClasses()
					.stream()
					.map(J.ClassDeclaration::getType)
					.forEach(localClassesCp::add);

				alreadyParsed.add(baseDir.resolve(s.getSourcePath()));
			})
			.toList();

		JavaSourceSet javaSourceSet = sourceSet(sourceSetName, classpathJars, typeCache);
		List<Marker> markers = new ArrayList<>(provenanceMarkers);

		javaSourceSet = appendToClasspath(localClassesCp, javaSourceSet);
		ClasspathDependencies classpathDependencies = new ClasspathDependencies(classpathJars);

		markers.add(javaSourceSet);
		markers.add(classpathDependencies);

		List<Path> parsedJavaPaths = javaSourcesInTarget.stream().map(ResourceUtil::getPath).toList();
		Stream<SourceFile> parsedJavaSources = cus.stream().map(addProvenance(baseDir, markers, parsedJavaPaths));
		LOGGER.debug("[%s] Scanned %d java source files in main scope.".formatted(currentProject, javaSources.size()));

		// Filter out any generated source files from the returned list, as we do not want
		// to apply the recipe to the
		// generated files.
		Path buildDirectory = LinuxWindowsPathUnifier.unifiedPath(Paths.get(currentProject.getBuildDirectory()));
		List<SourceFile> filteredJavaSources = filterOutResourcesInDir(parsedJavaSources, buildDirectory);

		int sourcesParsedBefore = alreadyParsed.size();
		alreadyParsed.addAll(parsedJavaPaths);

		List<Resource> resourcesLeft = resources.stream()
			.filter(r -> alreadyParsed.stream().noneMatch(path -> LinuxWindowsPathUnifier.pathStartsWith(r, path)))
			.toList();

		LOGGER.info("Parsing test resources");
		Path searchDir = currentProject.getModulePath().resolve(sourceDir).resolve("resources");
		List<SourceFile> parsedResourceFiles = rp
			.parseSourceFiles(searchDir, resourcesLeft, alreadyParsed, executionContext)
			.map(addProvenance(baseDir, markers, null))
			.toList();

		LOGGER.info("Parsed %d main resources".formatted(parsedResourceFiles.size()));

		// TODO: Remove
		// List<SourceFile> parsedResourceFiles = rp
		// .parse(currentProject.getModulePath().resolve("src/main/resources"), resources,
		// alreadyParsed)
		// .map(addProvenance(baseDir, mainProjectProvenance, null))
		// .toList();

		LOGGER.debug("[%s] Scanned %d resource files in main scope.".formatted(currentProject,
				(alreadyParsed.size() - sourcesParsedBefore)));
		// Any resources parsed from "main/resources" should also have the main source set
		// added to them.
		filteredJavaSources.addAll(parsedResourceFiles);
		return new SourceSetParsingResult(filteredJavaSources, javaSourceSet.getClasspath(), typeCache);

	}

	/**
	 * Add {@link Marker}s to {@link SourceFile}.
	 */
	public <T extends SourceFile> UnaryOperator<T> addProvenance(Path baseDir, List<Marker> provenance,
			@Nullable Collection<Path> generatedSources) {
		return s -> {
			Markers markers = s.getMarkers();
			for (Marker marker : provenance) {
				markers = markers.addIfAbsent(marker);
			}
			if (generatedSources != null && generatedSources.contains(baseDir.resolve(s.getSourcePath()))) {
				markers = markers.addIfAbsent(new Generated(randomId()));
			}
			return s.withMarkers(markers);
		};
	}

	private List<SourceFile> mergeAndFilterExcluded(Path baseDir, Set<String> exclusions, List<SourceFile> mainSources,
			List<SourceFile> testSources) {
		List<PathMatcher> pathMatchers = exclusions.stream()
			.map(pattern -> baseDir.getFileSystem().getPathMatcher("glob:" + pattern))
			.toList();
		if (pathMatchers.isEmpty()) {
			return Stream.concat(mainSources.stream(), testSources.stream()).toList();
		}
		return new ArrayList<>(Stream.concat(mainSources.stream(), testSources.stream())
			.filter(s -> isNotExcluded(baseDir, pathMatchers, s))
			.toList());
	}

	private static boolean isNotExcluded(Path baseDir, List<PathMatcher> exclusions, SourceFile s) {
		return exclusions.stream()
			.noneMatch(pm -> pm.matches(baseDir.resolve(s.getSourcePath()).toAbsolutePath().normalize()));
	}

	private Set<Path> pathsToOtherMavenProjects(MavenProject mavenProject, Path moduleBuildFile) {
		return mavenProject.getCollectedProjects()
			.stream()
			.filter(p -> !LinuxWindowsPathUnifier.pathEquals(p.getBuildFile().getPath(), moduleBuildFile))
			.map(p -> p.getFile().toPath().getParent())
			.collect(Collectors.toSet());
	}

	private static JavaTypeCache getJavaTypeCache(MavenProject currentProject,
			Map<MavenProject, ModuleParsingResult> parsingResultsMap, List<SourceFile> sourceFilesFromOtherModules) {
		JavaTypeCache typeCache;
		if (!sourceFilesFromOtherModules.isEmpty()) {
			Optional<JavaTypeCache> optJavaTypeCache = currentProject.getDependencyProjects()
				.stream()
				.map(mp -> parsingResultsMap.get(mp).mainSourcesParsingResult().typeCache())
				.max(Comparator.comparing(JavaTypeCache::size));
			typeCache = optJavaTypeCache.orElseThrow(() -> new IllegalStateException(
					"No TypeCahche from previous build found for project " + currentProject.getProjectId()));
		}
		else {
			typeCache = new JavaTypeCache();
		}
		return typeCache;
	}

	@NotNull
	private static ArrayList<SourceFile> filterOutResourcesInDir(Stream<SourceFile> parsedJava, Path buildDirectory) {
		return parsedJava.filter(s -> !s.getSourcePath().startsWith(buildDirectory))
			.collect(Collectors.toCollection(ArrayList::new));
	}

	/**
	 * Add entries that don't exist in the classpath of {@code javaSourceSet} from
	 * {@code appendingClasspath}.
	 */
	@NotNull
	private static JavaSourceSet appendToClasspath(Set<JavaType.FullyQualified> appendingClasspath,
			JavaSourceSet javaSourceSet) {
		List<JavaType.FullyQualified> curCp = javaSourceSet.getClasspath();
		appendingClasspath.forEach(f -> {
			if (!curCp.contains(f)) {
				curCp.add(f);
			}
		});
		javaSourceSet = javaSourceSet.withClasspath(new ArrayList<>(curCp));
		return javaSourceSet;
	}

	@NotNull
	private static JavaSourceSet sourceSet(String name, List<Path> dependencies, JavaTypeCache typeCache) {
		return JavaSourceSet.build(name, dependencies, typeCache, false);
	}

}
