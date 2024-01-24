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

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Fabian Krüger
 */
public class MavenProjectSorter {

	private static final String POM_XML = "pom.xml";

	private final MavenProjectGraph mavenProjectGraph;

	public MavenProjectSorter(MavenProjectGraph mavenProjectGraph) {
		this.mavenProjectGraph = mavenProjectGraph;
	}

	/**
	 * Filter {@link MavenProject}s that belong to a reactor build. Create ADG of the
	 * relevant projects and their dependencies to other {@link MavenProject}s. Sort the
	 * ADG and return the ordered list of {@link MavenProject}s.
	 */
	public List<MavenProject> sort(Path baseDir, List<MavenProject> allPomFiles) {
		Map<MavenProject, Set<MavenProject>> graph = mavenProjectGraph.from(baseDir, allPomFiles);
		List<MavenProject> buildOrder = new ArrayList<>();

		Map<MavenProject, Set<MavenProject>> dependingProjects = new HashMap<>();
		graph.keySet().forEach(mavenProject -> {
			graph.entrySet()
				.stream()
				.peek(e -> dependingProjects.computeIfAbsent(mavenProject, __ -> new HashSet<>()))
				.filter(e -> e.getValue().contains(mavenProject))
				.forEach(e -> {
					dependingProjects.get(mavenProject).add(e.getKey());
				});
		});

		Map<MavenProject, Integer> inDegree = dependingProjects.keySet()
			.stream()
			.collect(Collectors.toMap(k -> k, __ -> 0));
		dependingProjects.entrySet()
			.stream()
			.flatMap(e -> e.getValue().stream())
			.forEach(d -> inDegree.put(d, inDegree.get(d) + 1));

		Queue<MavenProject> sources = new PriorityQueue<>(Comparator.comparing(p -> p.getBuildFile().getArtifactId()));
		for (Map.Entry<MavenProject, Integer> entry : inDegree.entrySet()) {
			if (entry.getValue() == 0) {
				sources.add(entry.getKey());
			}
		}

		while (!sources.isEmpty()) {
			MavenProject project = sources.poll();
			buildOrder.add(project);
			List<MavenProject> mavenProjects1 = new ArrayList<>(dependingProjects.get(project));
			for (MavenProject child : mavenProjects1) {
				inDegree.put(child, inDegree.get(child) - 1);
				if (inDegree.get(child) == 0) {
					sources.add(child);
				}
			}
		}

		if (buildOrder.size() != inDegree.size()) {
			throw new RuntimeException("Cycle detected Maven projects");
		}

		return buildOrder;
	}

}
