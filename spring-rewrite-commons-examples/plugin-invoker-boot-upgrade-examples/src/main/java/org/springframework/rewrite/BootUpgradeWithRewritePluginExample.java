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

import org.eclipse.jgit.api.Git;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.rewrite.plugin.polyglot.RewritePlugin;
import org.springframework.rewrite.plugin.shared.PluginInvocationResult;
import org.springframework.rewrite.utils.GitHub;

import java.nio.file.Path;

@SpringBootApplication
public class BootUpgradeWithRewritePluginExample implements ApplicationRunner {

	public static void main(String[] args) {
		SpringApplication.run(BootUpgradeWithRewritePluginExample.class, args);
	}

	@Override
	public void run(ApplicationArguments args) {
		String repo = args.getNonOptionArgs().get(0);
		String gitHash = args.getNonOptionArgs().get(1);
		String token = args.getNonOptionArgs().get(2);
		Path projectDir = Path.of(args.getNonOptionArgs().get(3));

		Git clone = GitHub.clone(repo, projectDir, token);
		GitHub.checkoutCommit(clone, gitHash);

		PluginInvocationResult pluginInvocationResult = RewritePlugin.run()
			.gradlePluginVersion("latest.release")
			.recipes("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_2")
			.dependencies("org.openrewrite.recipe:rewrite-migrate-java:LATEST",
					"org.openrewrite.recipe:rewrite-hibernate:LATEST",
					"org.openrewrite.recipe:rewrite-java-dependencies:LATEST",
					"org.openrewrite.recipe:rewrite-testing-frameworks:LATEST",
					"org.openrewrite.recipe:rewrite-static-analysis:LATEST", "org.openrewrite:rewrite-kotlin:LATEST",
					"org.openrewrite.recipe:rewrite-spring:LATEST")
			.onDir(projectDir);

		System.out.println(pluginInvocationResult.capturedOutput());
	}

}