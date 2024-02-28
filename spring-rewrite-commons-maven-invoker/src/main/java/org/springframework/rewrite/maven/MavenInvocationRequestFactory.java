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
package org.springframework.rewrite.maven;

import org.apache.maven.shared.invoker.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Fabian Krüger
 */
public class MavenInvocationRequestFactory {

	@NotNull
	public InvocationRequest createMavenInvocationRequest(Path baseDir, DebugConfig debugConfig,
			BuildConfig buildConfig, List<String> givenGoals, Consumer<String> lineConsumer) {
		List<String> goals = new ArrayList<>(givenGoals);
		String mavenHome = System.getenv("MAVEN_HOME");
		if (mavenHome == null) {
			mavenHome = System.getenv("M2_HOME");
			if (mavenHome == null) {
				throw new IllegalStateException(
						"MAVEN_HOME or M2_HOME must be set but System.getenv(\"MAVEN_HOME\") and System.getenv(\"M2_HOME\") returned null.");
			}
		}

		if (buildConfig.isSkipTests()) {
			goals.add("-DskipTests");
		}

		InvocationRequest request = new DefaultInvocationRequest();
		request.setGoals(goals);
		request.setBatchMode(true);
		request.setMavenHome(new File(mavenHome));
		request.setBaseDirectory(baseDir.toFile());
		request.setOutputHandler(s -> lineConsumer.accept(s));

		StringBuilder mavenOpts = new StringBuilder();
		if (buildConfig.getMemorySettings() != null) {
			mavenOpts.append("-Xms%s -Xmx%s ".formatted(buildConfig.getMemorySettings().getMin(),
					buildConfig.getMemorySettings().getMax()));
		}
		if (debugConfig != null && debugConfig.isDebugEnabled()) {
			mavenOpts.append(" -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=%s,address=%s "
				.formatted(debugConfig.isSuspend(), debugConfig.getPort()));
		}
		request.setMavenOpts(mavenOpts.toString());
		return request;
	}

}
