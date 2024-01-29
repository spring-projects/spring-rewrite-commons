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

import org.springframework.rewrite.parser.ProjectId;
import org.springframework.rewrite.utils.LinuxWindowsPathUnifier;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Create directed acyclic graph from MavenProjects.
 *
 * @author Fabian Krüger
 */
public class MavenProjectGraph {

	private static final String POM_XML = "pom.xml";

	private Map<ProjectId, MavenProject> gaToMavenProjectMap = new HashMap<>();

	/**
	 * Create a Maven module dependency graph starting from pom.xml in {@code baseDir}.
	 * The dependency and dependants for all {@link MavenProject}s are added when they are
	 * part of the build.
	 */
	public Map<MavenProject, Set<MavenProject>> from(Path baseDir, List<MavenProject> allMavenProjects) {
		// build map of projectId -> MavenProject
		// this is used to decide if dependencies come from the project
		initGaToMavenProjectMap(allMavenProjects);
		MavenProject rootProject = findRootProject(baseDir, allMavenProjects);
		// the dag with child/parent dependencies
		Map<MavenProject, Set<MavenProject>> dag = new HashMap<>();
		buildDependencyGraph(rootProject, allMavenProjects, dag);
		enrichGraphWithDependencies(dag);
		return dag;
	}

	private void enrichGraphWithDependencies(Map<MavenProject, Set<MavenProject>> dag) {
		dag.keySet().stream().forEach(curProject -> {
			List<MavenProject> dependencyProjects = new ArrayList<>();
			curProject.getBuildFile().getDependencies().forEach(d -> {
				ProjectId projectId = new ProjectId(d.getGroupId(), d.getArtifactId());
				if (gaToMavenProjectMap.containsKey(projectId)) {
					MavenProject dependencyMavenProject = gaToMavenProjectMap.get(projectId);
					dependencyProjects.add(dependencyMavenProject);
				}
			});
			curProject.setDependencyProjects(dependencyProjects);
		});

		dag.keySet().stream().forEach(curProject -> {
			Set<MavenProject> dependentProjects = dag.get(curProject);
			curProject.getBuildFile().getDependencies().stream().forEach(dependency -> {
				ProjectId projectId = new ProjectId(dependency.getGroupId(), dependency.getArtifactId());
				if (gaToMavenProjectMap.containsKey(projectId)) {
					MavenProject dependantProject = gaToMavenProjectMap.get(projectId);
					if (dag.containsKey(dependantProject)) {
						dependentProjects.add(dependantProject);
					}
				}
			});
		});
	}

	private void initGaToMavenProjectMap(List<MavenProject> allPomFiles) {
		gaToMavenProjectMap = new HashMap<>();
		allPomFiles.stream().forEach(mp -> {
			gaToMavenProjectMap.putIfAbsent(new ProjectId(mp.getGroupId(), mp.getArtifactId()), mp);
		});
	}

	private void buildDependencyGraph(MavenProject currentProject, List<MavenProject> reactorProjects,
			Map<MavenProject, Set<MavenProject>> dag) {
		if (isSingleModuleProject(reactorProjects)) {
			logDependentProject(currentProject, dag, null);
			return;
		}

		if (isMultiModuleProject(currentProject)) {
			currentProject.getBuildFile().getModules().stream().map(moduleName -> {
				Path modulePath = currentProject.getModulePath().resolve(moduleName).normalize();
				MavenProject mavenProject = reactorProjects.stream()
					.filter(p -> LinuxWindowsPathUnifier.pathEquals(p.getModulePath(), modulePath))
					.findFirst()
					.get();
				return mavenProject;
			}).forEach(childProject -> {
				// add dependent project
				logDependentProject(childProject, dag, currentProject);
				buildDependencyGraph(childProject, reactorProjects, dag);
			});
		}
		dag.computeIfAbsent(currentProject, __ -> new HashSet<>());
	}

	private static boolean isMultiModuleProject(MavenProject currentProject) {
		return hasPomPackaging(currentProject);
	}

	private static boolean isSingleModuleProject(List<MavenProject> reactorProjects) {
		return reactorProjects.size() == 1;
	}

	private static void logDependentProject(MavenProject dependingProject, Map<MavenProject, Set<MavenProject>> dag,
			MavenProject dependantProject) {
		Set<MavenProject> mavenProjects = dag.computeIfAbsent(dependingProject, __ -> new HashSet<>());
		if (dependantProject != null) {
			mavenProjects.add(dependantProject);
		}
	}

	private MavenProject findRootProject(Path baseDir, List<MavenProject> reactorProjects) {
		return reactorProjects.stream()
			.filter(p -> LinuxWindowsPathUnifier.pathEquals(p.getBuildFile().getPath(), baseDir.resolve(POM_XML)))
			.findFirst()
			.get();
	}

	private static boolean hasPomPackaging(MavenProject curMavenProject) {
		return "pom".equals(curMavenProject.getBuildFile().getPackaging());
	}

}
