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

import org.openrewrite.marker.Marker;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * @author Fabian Krüger
 */
public class ClasspathDependencies implements Marker {

	private List<Path> dependencies;

	private final UUID id;

	public ClasspathDependencies(List<Path> dependencies) {
		this.dependencies = dependencies;
		this.id = UUID.randomUUID();
	}

	private ClasspathDependencies(UUID id, List<Path> dependencies) {
		this.id = id;
		this.dependencies = dependencies;
	}

	public void setDependencies(List<Path> dependencies) {
		this.dependencies = dependencies;
	}

	public List<Path> getDependencies() {
		return dependencies;
	}

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public ClasspathDependencies withId(UUID id) {
		return new ClasspathDependencies(id, dependencies);
	}

}
