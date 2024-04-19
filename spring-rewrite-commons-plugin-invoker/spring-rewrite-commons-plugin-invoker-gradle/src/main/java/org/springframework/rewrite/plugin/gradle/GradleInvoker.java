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

import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Fabian Krüger
 */
public class GradleInvoker {

	public static GradleInvocationResult runTasks(Path baseDir, String[] args, String... tasks) {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		ByteArrayOutputStream es = new ByteArrayOutputStream();
		DefaultGradleConnector connector = (DefaultGradleConnector) GradleConnector.newConnector();
		if (Files.exists(baseDir.resolve("gradle/wrapper/gradle-wrapper.properties"))) {
			connector.useBuildDistribution();
		}
		else {
			connector.useGradleVersion("8.4");
		}
		try (ProjectConnection connection = connector.forProjectDirectory(baseDir.toFile()).connect()) {
			BuildLauncher buildLauncher = connection.newBuild().setStandardOutput(os).forTasks(tasks);
			buildLauncher.addArguments(args);
			buildLauncher.setStandardError(es);
			buildLauncher.run();
			connection.close();
			return new GradleInvocationResult(new String(os.toByteArray()), new String(es.toByteArray()));
		}
	}

}
