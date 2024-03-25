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
package org.springframework.rewrite.embedder;

import lombok.extern.slf4j.Slf4j;
import org.apache.maven.execution.ExecutionEvent;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Path;
import java.util.List;

@Slf4j
@SpringBootApplication
public class MavenInvokerApplicationRunner implements ApplicationRunner {

	public static void main(String[] args) {
		SpringApplication.run(MavenInvokerApplicationRunner.class, args);
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {

		Path projectDir = Path.of(args.getNonOptionArgs().get(0));

		System.setProperty(MavenExecutor.MULTIMODULE_PROJECT_DIRECTORY, projectDir.toString());

		MavenExecutor mavenExecutor = new MavenExecutor(log, new AbstractExecutionListener() {
			@Override
			public void projectSucceeded(ExecutionEvent executionEvent) {
				System.out.println("Project succeeded");
			}
		});

		mavenExecutor.execute(List.of("clean", "install"), projectDir.toString(), System.out, System.err);
	}

}
