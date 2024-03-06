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
package org.springframework.rewrite.plugin.shared;

/**
 * @author Fabian Krüger
 */
public class BuildConfig {

	private boolean skipTests = false;

	private MemorySettings memorySettings;

	BuildConfig(boolean skipTests, MemorySettings memorySettings) {
		this.skipTests = skipTests;
		this.memorySettings = memorySettings;
	}

	private BuildConfig(boolean skipTests) {
		this.skipTests = skipTests;
	}

	public static BuildConfig skipTests() {
		BuildConfig buildConfig = new BuildConfig(true);
		return buildConfig;
	}

	public static BuildConfig defaultConfig() {
		return new BuildConfig(false);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static BuildConfig fromDefault() {
		return new BuildConfig(true, MemorySettings.noop());
	}

	public boolean isSkipTests() {
		return skipTests;
	}

	public MemorySettings getMemorySettings() {
		return memorySettings;
	}

	public boolean hasMemorySettings() {
		return memorySettings != null && memorySettings.getMin() != null;
	}

	public static class Builder {

		private boolean skipTests;

		private MemorySettings memorySettings = MemorySettings.of("256M", "1024M");

		public Builder skipTests(boolean b) {
			this.skipTests = b;
			return this;
		}

		public BuildConfig build() {
			return new BuildConfig(skipTests, memorySettings);
		}

		public Builder withMemory(String min, String max) {
			this.memorySettings = MemorySettings.of(min, max);
			return this;
		}

	}

}
