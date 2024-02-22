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
package org.springframework.rewrite;

import org.apache.maven.execution.ExecutionEvent;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.marker.Marker;
import org.openrewrite.maven.utilities.MavenArtifactDownloader;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.tree.ParsingEventListener;
import org.openrewrite.tree.ParsingExecutionContextView;
import org.openrewrite.xml.tree.Xml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.rewrite.maveninvokerplayground.MavenExecutor;
import org.springframework.rewrite.parser.*;
import org.springframework.rewrite.parser.events.StartedParsingProjectEvent;
import org.springframework.rewrite.parser.events.SuccessfullyParsedProjectEvent;
import org.springframework.rewrite.parser.maven.MavenBuildFileParser;
import org.springframework.rewrite.parser.maven.MavenProject;
import org.springframework.rewrite.parser.maven.MavenProjectAnalyzer;
import org.springframework.rewrite.parser.maven.ProvenanceMarkerFactory;
import org.springframework.rewrite.scopes.ScanScope;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Project parser parsing resources under a given {@link Path} to OpenRewrite Lossless
 * Semantic Tree (LST). The implementation aims to produce the exact same result as the
 * build tool plugins provided by OpenRewrite. The LST is provided as
 * {@code List<}{@link SourceFile}{@code >}.
 *
 * <p>
 * This dummy code shows how the AST can be used to run OpenRewrite recipes:
 *
 * <pre>{@code
 *  Path projectBaseDir = ...
 *  RewriteProjectParsingResult parsingResult = parser.parse(projectBaseDir);
 *  List<SourceFile> ast = parsingResult.sourceFiles();
 *  List<Recipe> recipes = discovery.discoverRecipes();
 *  RecipeRun recipeRun = recipes.get(0).run(ast, ctx);
 *  }
 * </pre>
 *
 * @author Fabian Krüger
 * @see RewriteRecipeDiscovery
 * @see <a href=
 * "https://docs.openrewrite.org/concepts-explanations/lossless-semantic-trees">LST</a>
 */
public class RewriteProjectParser {

	private static final Logger LOGGER = LoggerFactory.getLogger(RewriteProjectParser.class);

	private final ProvenanceMarkerFactory provenanceMarkerFactory;

	private final MavenBuildFileParser buildFileParser;

	private final SourceFileParser sourceFileParser;

	private final StyleDetector styleDetector;

	private final SpringRewriteProperties springRewriteProperties;

	private final ParsingEventListener parsingEventListener;

	private final ApplicationEventPublisher eventPublisher;

	private final ScanScope scanScope;

	private final ConfigurableListableBeanFactory beanFactory;

	private final ProjectScanner scanner;

	private final ExecutionContext executionContext;

	private final MavenProjectAnalyzer mavenProjectAnalyzer;

	private MavenArtifactDownloader artifactDownloader;

	public RewriteProjectParser(ProvenanceMarkerFactory provenanceMarkerFactory, MavenBuildFileParser buildFileParser,
			SourceFileParser sourceFileParser, StyleDetector styleDetector,
			SpringRewriteProperties springRewriteProperties, ParsingEventListener parsingEventListener,
			ApplicationEventPublisher eventPublisher, ScanScope scanScope, ConfigurableListableBeanFactory beanFactory,
			ProjectScanner scanner, ExecutionContext executionContext, MavenProjectAnalyzer mavenProjectAnalyzer) {
		this.provenanceMarkerFactory = provenanceMarkerFactory;
		this.buildFileParser = buildFileParser;
		this.sourceFileParser = sourceFileParser;
		this.styleDetector = styleDetector;
		this.springRewriteProperties = springRewriteProperties;
		this.parsingEventListener = parsingEventListener;
		this.eventPublisher = eventPublisher;
		this.scanScope = scanScope;
		this.beanFactory = beanFactory;
		this.scanner = scanner;
		this.executionContext = executionContext;
		this.mavenProjectAnalyzer = mavenProjectAnalyzer;
	}

	/**
	 * Parse the given {@code baseDir} to OpenRewrite AST.
	 */
	public RewriteProjectParsingResult parse(Path baseDir) {
		List<Resource> resources = scanner.scan(baseDir);
		return this.parse(baseDir, resources);
	}

	/**
	 * Parse given {@link Resource}s in {@code baseDir} to OpenRewrite LST.
	 */
	public RewriteProjectParsingResult parse(Path givenBaseDir, List<Resource> resources) {
		scanScope.clear(beanFactory);

		final Path baseDir = normalizePath(givenBaseDir);

		eventPublisher.publishEvent(new StartedParsingProjectEvent(resources));

		ParsingExecutionContextView.view(executionContext).setParsingListener(parsingEventListener);

		// TODO: "runPerSubmodule"
		// TODO: See ConfigurableRewriteMojo#getPlainTextMasks()
		// TODO: where to retrieve styles from? --> see
		// AbstractRewriteMojo#getActiveStyles() & AbstractRewriteMojo#loadStyles()

		AtomicReference<List<SourceFile>> sourceFilesRef = new AtomicReference<>();
		new MavenExecutor(onSuccess -> {
			List<SourceFile> sourceFiles = runInMavenSession(onSuccess, baseDir, resources);
			sourceFilesRef.set(sourceFiles);
		}).execute(List.of("clean", "package", "--fail-at-end"), baseDir);

		return new RewriteProjectParsingResult(sourceFilesRef.get(), executionContext);
	}

	private List<SourceFile> runInMavenSession(ExecutionEvent onSuccess, Path baseDir, List<Resource> resources) {
		List<NamedStyles> styles = List.of();
		onSuccess.getSession()
			.getProjectDependencyGraph()
			.getSortedProjects()
			.stream()
			.map(p -> this.mavenProjectToMavenProject(p, resources))
			.toList();

		List<MavenProject> sortedProjects = mavenProjectAnalyzer.getBuildProjects(baseDir, resources);
		ParserContext parserContext = new ParserContext(baseDir, resources, sortedProjects);

		// generate provenance
		Map<Path, List<Marker>> provenanceMarkers = provenanceMarkerFactory.generateProvenanceMarkers(baseDir,
				parserContext);

		// 127: parse build files
		// TODO: 945 this map is only used to lookup module pom by path in
		// SourceFileParser. If possible provide the build file from ParserContext and
		// remove this map.
		List<Xml.Document> parsedBuildFiles = buildFileParser.parseBuildFiles(baseDir,
				parserContext.getBuildFileResources(), parserContext.getActiveProfiles(), executionContext,
				springRewriteProperties.isSkipMavenParsing(), provenanceMarkers);
		parserContext.setParsedBuildFiles(parsedBuildFiles);

		LOGGER.trace("Start to parse %d source files in %d modules"
			.formatted(resources.size() + parsedBuildFiles.size(), parsedBuildFiles.size()));
		List<SourceFile> otherSourceFiles = sourceFileParser.parseOtherSourceFiles(baseDir, parserContext, resources,
				provenanceMarkers, styles, executionContext);

		List<Xml.Document> sortedBuildFileDocuments = parserContext.getSortedBuildFileDocuments();

		List<SourceFile> resultingList = new ArrayList<>();
		resultingList.addAll(sortedBuildFileDocuments);
		resultingList.addAll(otherSourceFiles);
		List<SourceFile> sourceFiles = styleDetector.sourcesWithAutoDetectedStyles(resultingList.stream());

		eventPublisher.publishEvent(new SuccessfullyParsedProjectEvent(sourceFiles));
		return sourceFiles;
	}

	private MavenProject mavenProjectToMavenProject(org.apache.maven.project.MavenProject mavenProject,
			MavenArtifactDownloader artifactDownloader, List<Resource> resources) {
		Path baseDir = mavenProject.getBasedir().toPath();
		File file = mavenProject.getExecutionProject().getFile();
		Resource rootPom = new FileSystemResource(file);
		new MavenProject(baseDir, rootPom, artifactDownloader, resources);
		return null;
	}

	@NotNull
	private static Path normalizePath(Path givenBaseDir) {
		if (!givenBaseDir.isAbsolute()) {
			givenBaseDir = givenBaseDir.toAbsolutePath().normalize();
		}
		String cleanedPath = StringUtils.cleanPath(givenBaseDir.toString());
		return Path.of(cleanedPath);
	}

}
