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
package org.springframework.rewrite.plugin.maven;

import org.apache.maven.shared.invoker.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.rewrite.plugin.shared.BuildConfig;
import org.springframework.rewrite.plugin.shared.DebugConfig;
import org.springframework.rewrite.plugin.shared.MemorySettings;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Fabian Krüger
 */
public class MavenInvoker {

	private static final Invoker invoker = new DefaultInvoker();

	public static MavenInvocationResult runGoals(Path baseDir, DebugConfig debugConfig, boolean debug,
			BuildConfig buildConfig, List<String> goals) {
		try {
			StringBuilder sb = new StringBuilder();
			InvocationRequest request = createInvocationRequest(baseDir, debugConfig, debug, buildConfig, goals, sb);
			InvocationResult result = invoker.execute(request);
			return new MavenInvocationResult(result.getExitCode(), sb.toString());
		}
		catch (MavenInvocationException e) {
			throw new RuntimeException(e);
		}
	}

	@NotNull
	private static InvocationRequest createInvocationRequest(Path baseDir, DebugConfig debugConfig, boolean debug,
			BuildConfig buildConfig, List<String> goals, StringBuilder sb) {
		return new MavenInvocationRequestFactory().createMavenInvocationRequest(baseDir, debugConfig, debug,
				buildConfig, goals, line -> {
					sb.append(line).append("\n");
				});
	}

}
