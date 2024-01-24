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

import org.openrewrite.ExecutionContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.rewrite.project.resource.RewriteSourceFileWrapper;
import org.springframework.rewrite.project.resource.*;

/**
 * Configuration for {@link ProjectResourceSet} related beans.
 *
 * @author Fabian Krüger
 */
@AutoConfiguration
public class ProjectResourceSetConfiguration {

	@Bean
	RewriteSourceFileWrapper rewriteSourceFileWrapper() {
		return new RewriteSourceFileWrapper();
	}

	@Bean
	RewriteMigrationResultMerger rewriteMigrationResultMerger(RewriteSourceFileWrapper rewriteSourceFileWrapper) {
		return new RewriteMigrationResultMerger(rewriteSourceFileWrapper);
	}

	@Bean
	ProjectResourceSerializer projectResourceSerializer() {
		return new ProjectResourceSerializer();
	}

	@Bean
	ProjectResourceSetSerializer projectResourceSetSerializer(ProjectResourceSerializer resourceSerializer) {
		return new ProjectResourceSetSerializer(resourceSerializer);
	}

	@Bean
	ProjectResourceSetFactory projectResourceSetFactory(RewriteMigrationResultMerger rewriteMigrationResultMerger,
			RewriteSourceFileWrapper sourceFileWrapper, ExecutionContext executionContext) {
		return new ProjectResourceSetFactory(rewriteMigrationResultMerger, sourceFileWrapper, executionContext);
	}

}
