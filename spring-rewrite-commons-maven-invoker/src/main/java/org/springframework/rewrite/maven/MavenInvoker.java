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
package org.springframework.rewrite.maven;

import org.apache.maven.shared.invoker.*;

/**
 * @author Fabian Krüger
 */
public class MavenInvoker {

	private Invoker invoker = new DefaultInvoker();

	public InvocationResult invoke(InvocationRequest request) {
		try {
			return invoker.execute(request);
		}
		catch (MavenInvocationException e) {
			throw new RuntimeException(e);
		}
	}

}
