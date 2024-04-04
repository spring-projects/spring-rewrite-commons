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
package org.springframework.rewrite.utils;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.nio.file.Path;

/**
 * @author Fabian Krüger
 */
public class GitHub {

	public static Git clone(String repositoryUrl, Path targetDir, String token) {
		try {
			Git git = Git.cloneRepository()
				.setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""))
				.setURI(repositoryUrl)
				.setDirectory(targetDir.toFile())
				.call();
			return git;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void checkoutCommit(Git git, String startingGitHash) {
		try {
			Ref ref = git.checkout().setName(startingGitHash).call();
		}
		catch (GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

}
