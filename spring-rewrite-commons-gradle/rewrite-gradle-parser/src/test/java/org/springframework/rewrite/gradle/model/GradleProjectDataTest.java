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
package org.springframework.rewrite.gradle.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test requires spring rewrite gradle plugin available maven. Hence once model and plugin
 * are built and installed parser is built and its tests are ran
 */
public class GradleProjectDataTest {

	@Test
	void serializable(@TempDir Path dir) throws Exception {
		try (InputStream is = GradleProjectDataTest.class.getResourceAsStream("/build.gradle")) {
			Files.write(dir.resolve("build.gradle"), Objects.requireNonNull(is).readAllBytes());
		}
		try (InputStream is = GradleProjectDataTest.class.getResourceAsStream("/settings.gradle")) {
			Files.write(dir.resolve("settings.gradle"), Objects.requireNonNull(is).readAllBytes());
		}

		GradleProjectData gp = SpringRewriteModelBuilder.forProjectDirectory(GradleProjectData.class, dir.toFile(),
				dir.resolve("build.gradle").toFile());

		assertThat(gp.getGroup()).isEqualTo("");
		assertThat(gp.getName()).isEqualTo("sample");
		assertThat(gp.getVersion()).isEqualTo("unspecified");
		assertThat(gp.getPlugins().size()).isEqualTo(13);
		assertThat(gp.getMavenRepositories().get(0).getUri()).isEqualTo("https://repo.maven.apache.org/maven2/");
	}

}
