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
package org.springframework.rewrite.test.util;

import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.rewrite.RewriteProjectParser;
import org.springframework.rewrite.boot.autoconfigure.RewriteLauncherConfiguration;
import org.springframework.rewrite.parser.RewriteProjectParsingResult;
import org.springframework.rewrite.parser.SpringRewriteProperties;
import org.springframework.rewrite.parser.maven.ComparingParserFactory;
import org.springframework.rewrite.parser.maven.RewriteMavenProjectParser;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Fabian Krüger
 */
public class ParserExecutionHelper {

	private static final Logger LOGGER = LoggerFactory.getLogger(ParserExecutionHelper.class);

	public ParallelParsingResult parseParallel(Path baseDir) {
		return parseParallel(baseDir, new SpringRewriteProperties(), new InMemoryExecutionContext(t -> {
			throw new RuntimeException(t);
		}));
	}

	public ParallelParsingResult parseParallel(Path baseDir, SpringRewriteProperties springRewriteProperties) {
		return parseParallel(baseDir, springRewriteProperties, new InMemoryExecutionContext(t -> {
			throw new RuntimeException(t);
		}));
	}

	public ParallelParsingResult parseParallel(Path baseDir, ExecutionContext executionContext) {
		return parseParallel(baseDir, new SpringRewriteProperties(), executionContext);
	}

	public ParallelParsingResult parseParallel(Path baseDir, SpringRewriteProperties springRewriteProperties,
			ExecutionContext executionContext) {
		try {
			CountDownLatch latch = new CountDownLatch(2);

			ExecutorService threadPool = Executors.newFixedThreadPool(2);

			AtomicReference<RewriteProjectParsingResult> actualParsingResultRef = new AtomicReference<>();
			AtomicReference<RewriteProjectParsingResult> expectedParsingResultRef = new AtomicReference<>();

			threadPool.submit(() -> {
				RewriteProjectParsingResult parsingResult = parseWithRewriteProjectParser(baseDir,
						springRewriteProperties);
				;
				actualParsingResultRef.set(parsingResult);
				latch.countDown();
			});

			threadPool.submit(() -> {
				RewriteProjectParsingResult parsingResult = parseWithComparingParser(baseDir, springRewriteProperties,
						executionContext);
				expectedParsingResultRef.set(parsingResult);
				latch.countDown();
			});
			latch.await();
			return new ParallelParsingResult(expectedParsingResultRef.get(), actualParsingResultRef.get());
		}
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public RewriteProjectParsingResult parseWithComparingParser(Path baseDir,
			SpringRewriteProperties springRewriteProperties, ExecutionContext executionContext) {
		RewriteMavenProjectParser comparingParser = new ComparingParserFactory()
			.createComparingParser(springRewriteProperties);
		try {
			if (executionContext != null) {
				return comparingParser.parse(baseDir, executionContext);
			}
			else {
				return comparingParser.parse(baseDir);
			}
		}
		catch (Exception e) {
			LOGGER.error("Failure while parsing with %s".formatted(RewriteMavenProjectParser.class.getName()), e);
			throw new RuntimeException(e);
		}
	}

	public RewriteProjectParsingResult parseWithRewriteProjectParser(Path baseDir,
			SpringRewriteProperties springRewriteProperties) {
		AtomicReference<RewriteProjectParsingResult> atomicRef = new AtomicReference<>();
		new ApplicationContextRunner().withUserConfiguration(RewriteLauncherConfiguration.class)
			.withBean("spring.rewrite-" + SpringRewriteProperties.class.getName(), SpringRewriteProperties.class,
					() -> springRewriteProperties)
			.run(appCtx -> {
				try {
					RewriteProjectParser sut = appCtx.getBean(RewriteProjectParser.class);
					RewriteProjectParsingResult testedParserResult = sut.parse(baseDir);
					atomicRef.set(testedParserResult);
				}
				catch (Exception e) {
					LOGGER.error("Failure while parsing with %s".formatted(RewriteProjectParser.class.getName()), e);
					throw new RuntimeException(e);
				}
			});
		return atomicRef.get();
	}

}
