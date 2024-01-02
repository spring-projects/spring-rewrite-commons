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

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.codehaus.plexus.PlexusContainer;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.maven.MavenMojoProjectParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.rewrite.parsers.SpringRewriteProperties;
import org.springframework.rewrite.parsers.Slf4jToMavenLoggerAdapter;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * @author Fabian Krüger
 */
public class MavenMojoProjectParserFactory {

	private static final Logger LOGGER = LoggerFactory.getLogger(MavenMojoProjectParserFactory.class);

	private final SpringRewriteProperties springRewriteProperties;

	public MavenMojoProjectParserFactory(SpringRewriteProperties springRewriteProperties) {
		this.springRewriteProperties = springRewriteProperties;
	}

	public MavenMojoProjectParser create(Path baseDir, List<MavenProject> mavenProjects,
			PlexusContainer plexusContainer, MavenSession session) {
		return buildMavenMojoProjectParser(baseDir, plexusContainer, session);
	}

	@NotNull
	private MavenMojoProjectParser buildMavenMojoProjectParser(Path baseDir, PlexusContainer plexusContainer,
			MavenSession session) {
		try {
			Log logger = new Slf4jToMavenLoggerAdapter(LoggerFactory.getLogger(MavenMojoProjectParser.class));
			RuntimeInformation runtimeInformation = plexusContainer.lookup(RuntimeInformation.class);
			SettingsDecrypter decrypter = plexusContainer.lookup(SettingsDecrypter.class);

			MavenMojoProjectParser sut = new MavenMojoProjectParser(logger, baseDir,
					springRewriteProperties.isPomCacheEnabled(), springRewriteProperties.getPomCacheDirectory(),
					runtimeInformation, springRewriteProperties.isSkipMavenParsing(),
					springRewriteProperties.getIgnoredPathPatterns(), springRewriteProperties.getPlainTextMasks(),
					springRewriteProperties.getSizeThresholdMb(), session, decrypter,
					springRewriteProperties.isRunPerSubmodule(), springRewriteProperties.isParseAdditionalResources());

			return sut;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public MavenMojoProjectParser create(Path baseDir, RuntimeInformation runtimeInformation,
			SettingsDecrypter settingsDecrypter) {
		return new MavenMojoProjectParser(new Slf4jToMavenLoggerAdapter(LOGGER), baseDir,
				springRewriteProperties.isPomCacheEnabled(), springRewriteProperties.getPomCacheDirectory(),
				runtimeInformation, springRewriteProperties.isSkipMavenParsing(),
				springRewriteProperties.getIgnoredPathPatterns(), springRewriteProperties.getPlainTextMasks(),
				springRewriteProperties.getSizeThresholdMb(), null, settingsDecrypter,
				springRewriteProperties.isRunPerSubmodule());
	}

}
