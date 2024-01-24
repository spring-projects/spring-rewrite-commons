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
package org.springframework.rewrite.boot.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.rewrite.parsers.RewriteParserConfiguration;

/**
 * Configuration for all components.
 * @deprecated
 * Use {@link RewriteLauncherConfiguration} instead
 *
 * @author Fabian Krüger
 */
@Deprecated(forRemoval = true)
@AutoConfiguration
@Import({ RecipeDiscoveryConfiguration.class, RewriteParserConfiguration.class, ProjectResourceSetConfiguration.class,
		RewriteLauncherConfiguration.class })
public class SpringRewriteCommonsConfiguration {

}
