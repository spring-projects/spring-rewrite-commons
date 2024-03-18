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
package org.springframework.rewrite.plugin.gradle;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Fabian Krüger
 */
public class TempGradleInitFile implements Closeable {

	private static final String template = """
			initscript {
			    repositories {
			        maven { url "https://plugins.gradle.org/m2" }
			    }
			    dependencies {
			        classpath("org.openrewrite:plugin:%s")
			    }
			}

			rootProject {
			    plugins.apply(org.openrewrite.gradle.RewritePlugin)
			    dependencies {
			%s
			    }

			    afterEvaluate {
			        if (repositories.isEmpty()) {
			            repositories {
			                mavenCentral()
			            }
			        }
			    }
			}
			""";

	private final Path gradleFile;

	private final String initFileContent;

	public TempGradleInitFile(Path baseDir, List<String> dependencies, String pluginVersion) {
		String dependenciesStr = dependencies.stream()
			.map(d -> "        rewrite(\"" + d + "\")")
			.collect(Collectors.joining("\n"));
		initFileContent = template.formatted(pluginVersion, dependenciesStr);
		try {
			gradleFile = baseDir.resolve("recipes.init.gradle").toAbsolutePath().normalize();
			gradleFile.toFile().getParentFile().mkdirs();
			Files.writeString(gradleFile, initFileContent);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void clear(Path baseDir) {
		Path gradleFile = baseDir.resolve("recipes.init.gradle");
		if (Files.exists(gradleFile)) {
			try {
				Files.delete(gradleFile);
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public String getContent() {
		return initFileContent;
	}

	public Path getPath() {
		return gradleFile;
	}

	@Override
	public void close() {
		if (Files.exists(gradleFile)) {
			try {
				Files.delete(gradleFile);
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

}
