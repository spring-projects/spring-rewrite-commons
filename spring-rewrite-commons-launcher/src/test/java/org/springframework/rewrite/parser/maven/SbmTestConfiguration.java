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

import org.openrewrite.ExecutionContext;
import org.openrewrite.tree.ParsingEventListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.rewrite.parser.RewriteParserConfiguration;
import org.springframework.rewrite.parser.SpringRewriteProperties;
import org.springframework.rewrite.scopes.ScanScope;

/**
 * @author Fabian Krüger
 */
@TestConfiguration
@Import(RewriteParserConfiguration.class)
public class SbmTestConfiguration {

	@Autowired
	private SpringRewriteProperties springRewriteProperties;

	@Bean
	MavenConfigFileParser configFileParser() {
		return new MavenConfigFileParser();
	}

	@Bean
	MavenMojoProjectParserFactory projectParserFactory() {
		return new MavenMojoProjectParserFactory(springRewriteProperties);
	}

	@Bean
	MavenModelReader modelReader() {
		return new MavenModelReader();
	}

	@Bean
	RewriteMavenProjectParser rewriteMavenProjectParser(ParsingEventListener parsingEventListenerAdapter,
			MavenMojoProjectParserFactory mavenMojoProjectParserFactory, ScanScope scanScope,
			ConfigurableListableBeanFactory beanFactory, ExecutionContext executionContext) {
		return new RewriteMavenProjectParser(parsingEventListenerAdapter, mavenMojoProjectParserFactory, scanScope,
				beanFactory, executionContext);
	}

}
