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

import org.springframework.rewrite.plugin.shared.DebugConfig;
import org.springframework.rewrite.plugin.shared.PluginInvocationResult;

import java.nio.file.Path;

/**
 * @author Fabian Krüger
 */
public interface OpenRewriteGradlePluginBuilder {

	public interface Recipes {

		PluginVersion recipes(String... recipeNames);

	}

	public interface FinalizingBuilder {

		FinalizingBuilder withDebugger(int port, boolean suspend);

		FinalizingBuilder withDependencies(String... dependencies);

		FinalizingBuilder withDebug();

		FinalizingBuilder withMemory(String minMemory, String maxMemory);

		FinalizingBuilder withDebugConfig(DebugConfig debugConfig);

		PluginInvocationResult onDir(Path baseDir);

	}

	public interface PluginVersion {

		FinalizingBuilder usingPluginVersion(String version);

	}

}
