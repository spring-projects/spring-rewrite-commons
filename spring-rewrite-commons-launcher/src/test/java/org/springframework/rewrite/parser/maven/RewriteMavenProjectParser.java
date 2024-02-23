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

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusContainer;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.maven.MavenMojoProjectParser;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.tree.ParsingEventListener;
import org.openrewrite.tree.ParsingExecutionContextView;
import org.openrewrite.xml.tree.Xml;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.io.Resource;
import org.springframework.rewrite.embedder.MavenExecutor;
import org.springframework.rewrite.parser.RewriteProjectParsingResult;
import org.springframework.rewrite.scopes.ScanScope;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Parses a given {@link Path} to a Open Rewrite's AST representation
 * {@code List<}{@link SourceFile}{@code >}.
 *
 * @author Fabian Krüger
 */
public class RewriteMavenProjectParser {

	private final ParsingEventListener parsingListener;

	private final MavenMojoProjectParserFactory mavenMojoProjectParserFactory;

	private final ScanScope scanScope;

	private final ConfigurableListableBeanFactory beanFactory;

	private final ExecutionContext executionContext;

	public RewriteMavenProjectParser(ParsingEventListener parsingListener,
			MavenMojoProjectParserFactory mavenMojoProjectParserFactory, ScanScope scanScope,
			ConfigurableListableBeanFactory beanFactory, ExecutionContext executionContext) {
		this.parsingListener = parsingListener;
		this.mavenMojoProjectParserFactory = mavenMojoProjectParserFactory;
		this.scanScope = scanScope;
		this.beanFactory = beanFactory;
		this.executionContext = executionContext;
	}

	/**
	 * Parses a list of {@link Resource}s in given {@code baseDir} to OpenRewrite AST. It
	 * uses default settings for configuration.
	 */
	public RewriteProjectParsingResult parse(Path baseDir) {
		ParsingExecutionContextView.view(executionContext).setParsingListener(parsingListener);
		return parse(baseDir, executionContext);
	}

	@NotNull
	public RewriteProjectParsingResult parse(Path baseDir, ExecutionContext executionContext) {
		final Path absoluteBaseDir = getAbsolutePath(baseDir);

		RewriteProjectParsingResult parsingResult = parseInternal(absoluteBaseDir, executionContext);
		return parsingResult;
	}

	private RewriteProjectParsingResult parseInternal(Path baseDir, ExecutionContext executionContext) {
		clearScanScopedBeans();

		AtomicReference<RewriteProjectParsingResult> parsingResult = new AtomicReference<>();

		new MavenExecutor(LoggerFactory.getLogger(RewriteMavenProjectParser.class), event -> {
			MavenSession session = event.getSession();
			List<MavenProject> mavenProjects = session.getAllProjects();
			PlexusContainer plexusContainer = session.getContainer();
			MavenMojoProjectParser rewriteProjectParser = mavenMojoProjectParserFactory.create(baseDir, mavenProjects,
					plexusContainer, session);
			List<NamedStyles> styles = List.of();
			List<SourceFile> sourceFiles = parseSourceFiles(session.getTopLevelProject(), rewriteProjectParser,
					mavenProjects, styles, executionContext);
			parsingResult.set(new RewriteProjectParsingResult(sourceFiles, executionContext));
		}).execute(List.of("clean", "package", "--fail-at-end"), baseDir);

		return parsingResult.get();
	}

	private void clearScanScopedBeans() {
		scanScope.clear(beanFactory);
	}

	private List<SourceFile> parseSourceFiles(MavenProject rootMavenProject,
			MavenMojoProjectParser rewriteProjectParser, List<MavenProject> mavenProjects, List<NamedStyles> styles,
			ExecutionContext executionContext) {
		try {
			Stream<SourceFile> sourceFileStream = rewriteProjectParser.listSourceFiles(rootMavenProject,
					// access to root
					// module
					styles, executionContext);
			return sourcesWithAutoDetectedStyles(sourceFileStream);
		}
		catch (DependencyResolutionRequiredException | MojoExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	@NotNull
	private static Path getAbsolutePath(Path baseDir) {
		if (!baseDir.isAbsolute()) {
			baseDir = baseDir.toAbsolutePath().normalize();
		}
		return baseDir;
	}

	// copied from OpenRewrite for now, TODO: remove and reuse
	List<SourceFile> sourcesWithAutoDetectedStyles(Stream<SourceFile> sourceFiles) {
		org.openrewrite.java.style.Autodetect.Detector javaDetector = org.openrewrite.java.style.Autodetect.detector();
		org.openrewrite.xml.style.Autodetect.Detector xmlDetector = org.openrewrite.xml.style.Autodetect.detector();
		List<SourceFile> sourceFileList = sourceFiles.peek(javaDetector::sample)
			.peek(xmlDetector::sample)
			.collect(toList());

		Map<Class<? extends Tree>, NamedStyles> stylesByType = new HashMap<>();
		stylesByType.put(JavaSourceFile.class, javaDetector.build());
		stylesByType.put(Xml.Document.class, xmlDetector.build());

		return ListUtils.map(sourceFileList, applyAutodetectedStyle(stylesByType));
	}

	// copied from OpenRewrite for now, TODO: remove and reuse
	UnaryOperator<SourceFile> applyAutodetectedStyle(Map<Class<? extends Tree>, NamedStyles> stylesByType) {
		return before -> {
			for (Map.Entry<Class<? extends Tree>, NamedStyles> styleTypeEntry : stylesByType.entrySet()) {
				if (styleTypeEntry.getKey().isAssignableFrom(before.getClass())) {
					before = before.withMarkers(before.getMarkers().add(styleTypeEntry.getValue()));
				}
			}
			return before;
		};
	}

}
