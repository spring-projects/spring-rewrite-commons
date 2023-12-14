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
package org.springframework.rewrite.parsers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

class SpringRewritePropertiesTest {

	@Nested
	@SpringBootTest(classes = { RewriteParserConfiguration.class })
	public class GivenDefaultProperties {

		@Autowired
		private SpringRewriteProperties springRewriteProperties;

		@Test
		@DisplayName("spring.rewrite.pomCacheEnabled")
		void defaultPomCacheEnabled() {
			assertThat(springRewriteProperties.isPomCacheEnabled()).isFalse();
		}

		@Test
		@DisplayName("spring.rewrite.pomCacheDirectory")
		void defaultPomCacheDirectory() {
			assertThat(springRewriteProperties.getPomCacheDirectory())
				.isEqualTo(System.getProperty("user.home") + "/.rewrite-cache");
		}

		@Test
		@DisplayName("spring.rewrite.skipMavenParsing")
		void defaultSkipMavenParsing() {
			assertThat(springRewriteProperties.isSkipMavenParsing()).isFalse();
		}

		@Test
		@DisplayName("spring.rewrite.plainTextMasks")
		void defaultPlainTextMasks() {
			assertThat(springRewriteProperties.getPlainTextMasks()).containsExactlyInAnyOrder("*.txt");
		}

		@Test
		@DisplayName("spring.rewrite.sizeThresholdMb")
		void defaultSizeThresholdMb() {
			assertThat(springRewriteProperties.getSizeThresholdMb()).isEqualTo(10);
		}

		@Test
		@DisplayName("spring.rewrite.runPerSubmodule")
		void defaultRunPerSubmodule() {
			assertThat(springRewriteProperties.isRunPerSubmodule()).isFalse();
		}

		@Test
		@DisplayName("discovery.failOnInvalidActiveRecipes")
		void defaultFailOnInvalidActiveRecipes() {
			assertThat(springRewriteProperties.isFailOnInvalidActiveRecipes()).isTrue();
		}

		@Test
		@DisplayName("spring.rewrite.activeProfiles")
		void defaultActiveProfiles() {
			assertThat(springRewriteProperties.getActiveProfiles()).containsExactlyInAnyOrder("default");
		}

		@Test
		@DisplayName("spring.rewrite.ignoredPathPatterns")
		void defaultIgnoredPathPatterns() {
			assertThat(springRewriteProperties.getIgnoredPathPatterns()).containsExactlyInAnyOrder(".idea/**",
					"**/.idea/**", ".mvn/**", "**/.mvn/**", "**/target/**", "**.git/**", "target/**");
		}

	}

}