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

import org.jetbrains.annotations.NotNull;
import org.openrewrite.InMemoryExecutionContext;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.rewrite.OpenRewriteProjectParser;
import org.springframework.rewrite.parser.RewriteExecutionContext;
import org.springframework.rewrite.parser.SpringRewriteProperties;
import org.springframework.rewrite.parser.events.RewriteParsingEventListenerAdapter;
import org.springframework.rewrite.scopes.ScanScope;

import static org.mockito.Mockito.mock;

/**
 * @author Fabian Krüger
 */
public class ComparingParserFactory {

	@NotNull
	public OpenRewriteProjectParser createComparingParser() {
		return createComparingParser(new SpringRewriteProperties());
	}

	public OpenRewriteProjectParser createComparingParser(SpringRewriteProperties springRewriteProperties) {
		OpenRewriteProjectParser mavenProjectParser1 = new OpenRewriteProjectParser(springRewriteProperties,
				new RewriteExecutionContext());
		return mavenProjectParser1;
	}

}
