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

/**
 * @author Fabian Krüger
 */
public class DebugConfig {

	private int port = 5005;

	private boolean suspend = true;

	private boolean isDebugEnabled = true;

	public static DebugConfig from(int port, boolean suspend) {
		return new DebugConfig(port, suspend, true);
	}

	public static DebugConfig fromDefault() {
		return new DebugConfig(5005, false, true);
	}

	public static DebugConfig disabled() {
		return new DebugConfig(5005, false, false);
	}

	private DebugConfig(int port, boolean suspend, boolean isDebugEnabled) {
		this.port = port;
		this.suspend = suspend;
		this.isDebugEnabled = isDebugEnabled;
	}

	public int getPort() {
		return port;
	}

	public char isSuspend() {
		return suspend ? 'y' : 'n';
	}

	public boolean isDebugEnabled() {
		return isDebugEnabled;
	}

}
