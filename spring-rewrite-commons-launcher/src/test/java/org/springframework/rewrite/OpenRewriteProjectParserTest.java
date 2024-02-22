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
package org.springframework.rewrite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.rewrite.boot.autoconfigure.RewriteLauncherConfiguration;
import org.springframework.rewrite.test.util.TestProjectHelper;

import java.nio.file.Path;

/**
 * @author Fabian Krüger
 */
@SpringBootTest(classes = RewriteLauncherConfiguration.class)
class OpenRewriteProjectParserTest {

	@Autowired
	private OpenRewriteProjectParser sut;

	@Test
	@DisplayName("test")
	void test() {
		Path mavenProject = TestProjectHelper.getMavenProject("test1");
		sut.parse(mavenProject);
	}

}