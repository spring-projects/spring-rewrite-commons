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
package org.springframework.rewrite.gradle.model;

import org.openrewrite.gradle.toolingapi.GradleProject;
import org.openrewrite.gradle.toolingapi.GradleSettings;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface GradleProjectData extends GradleProject {

	String getGroup();

	String getVersion();

	GradleSettings getGradleSettings();

	String getGradleVersion();

	boolean isRootProject();

	File getRootProjectDir();

	Collection<GradleProjectData> getSubprojects();

	File getProjectDir();

	File getBuildDir();

	File getBuildscriptFile();

	Map<String, ?> getProperties();

	List<JavaSourceSetData> getJavaSourceSets();

	boolean isMultiPlatformKotlinProject();

	List<KotlinSourceSetData> getKotlinSourceSets();

	Collection<File> getBuildscriptClasspath();

	Collection<File> getSettingsClasspath();

}
