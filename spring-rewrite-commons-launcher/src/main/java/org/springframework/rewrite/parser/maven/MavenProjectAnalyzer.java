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

import org.springframework.core.io.Resource;
import org.springframework.rewrite.parser.MavenProject;
import org.springframework.rewrite.utils.LinuxWindowsPathUnifier;

import java.nio.file.Path;
import java.util.*;

/**
 * Implements the ordering of Maven (reactor) build projects. See <a href=
 * "https://maven.apache.org/guides/mini/guide-multiple-modules.html#reactor-sorting">Reactor
 * Sorting</a>
 *
 * @author Fabian Krüger
 */
public class MavenProjectAnalyzer {

	private final MavenProjectSorter mavenProjectSorter;

	private final MavenProjectFactory mavenProjectFactory;

	public MavenProjectAnalyzer(MavenProjectSorter mavenProjectSorter, MavenProjectFactory mavenProjectFactory) {
		this.mavenProjectSorter = mavenProjectSorter;
		this.mavenProjectFactory = mavenProjectFactory;
	}

	public List<MavenProject> getBuildProjects(Path baseDir, List<Resource> resources) {
		List<MavenProject> allMavenProjects = mavenProjectFactory.create(baseDir, resources);
		List<MavenProject> mavenProjects = mavenProjectSorter.sort(baseDir, allMavenProjects);
		return map(baseDir, resources, mavenProjects);
	}

	private List<MavenProject> map(Path baseDir, List<Resource> resources, List<MavenProject> sortedModels) {

		List<MavenProject> mavenProjects = new ArrayList<>();
		sortedModels.stream().filter(Objects::nonNull).forEach(mavenProject -> {
			String projectDir = LinuxWindowsPathUnifier
				.unifiedPathString(baseDir.resolve(mavenProject.getModuleDir()).normalize());
			List<Resource> filteredResources = resources.stream()
				.filter(r -> LinuxWindowsPathUnifier.unifiedPathString(r).startsWith(projectDir))
				.toList();
			mavenProjects.add(mavenProject);
		});
		// set all non parent poms as collected projects for root parent p
		List<MavenProject> collected = new ArrayList<>(mavenProjects);
		collected.remove(0);
		mavenProjects.get(0).setReactorProjects(collected);
		return mavenProjects;
	}

}
