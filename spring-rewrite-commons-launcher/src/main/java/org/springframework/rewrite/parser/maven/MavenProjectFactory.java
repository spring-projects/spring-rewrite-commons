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

import org.jetbrains.annotations.NotNull;
import org.openrewrite.maven.utilities.MavenArtifactDownloader;
import org.springframework.core.io.Resource;
import org.springframework.rewrite.parser.MavenProject;

import java.nio.file.Path;
import java.util.List;

/**
 * @author Fabian Krüger
 */
public class MavenProjectFactory {

	private final MavenArtifactDownloader artifactDownloader;

	public MavenProjectFactory(MavenArtifactDownloader artifactDownloader) {
		this.artifactDownloader = artifactDownloader;
	}

	public List<MavenProject> create(Path baseDir, List<Resource> projectResources) {
		List<Resource> allPomFiles = MavenBuildFileFilter.filterBuildFiles(projectResources);
		if (allPomFiles.isEmpty()) {
			throw new IllegalArgumentException("The provided resources did not contain any 'pom.xml' file.");
		}
		return allPomFiles.stream().map(pf -> create(baseDir, pf, projectResources)).toList();
	}

	@NotNull
	public MavenProject create(Path baseDir, Resource pomFile, List<Resource> projectResources) {
		return new MavenProject(baseDir, pomFile, artifactDownloader, projectResources);
	}

}
