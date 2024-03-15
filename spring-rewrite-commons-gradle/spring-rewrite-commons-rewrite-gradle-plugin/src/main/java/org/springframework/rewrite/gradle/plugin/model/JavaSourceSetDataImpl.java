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
package org.springframework.rewrite.gradle.plugin.model;

import org.springframework.rewrite.gradle.model.JavaSourceSetData;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;

final class JavaSourceSetDataImpl implements JavaSourceSetData, Serializable {

	private final String name;

	private final Collection<File> sources;

	private final Collection<File> sourceDirectories;

	private final Collection<File> java;

	private final Collection<File> classesDirs;

	private final Collection<File> compileClasspath;

	private final Collection<File> implementationClasspath;

	private final JavaVersionDataImpl javaVersionData;

	public JavaSourceSetDataImpl(String name, Collection<File> sources, Collection<File> sourceDirectories,
			Collection<File> java, Collection<File> classesDirs, Collection<File> compileClasspath,
			Collection<File> implementationClasspath, JavaVersionDataImpl javaVersionData) {
		this.name = name;
		this.sources = sources;
		this.sourceDirectories = sourceDirectories;
		this.java = java;
		this.classesDirs = classesDirs;
		this.compileClasspath = compileClasspath;
		this.implementationClasspath = implementationClasspath;
		this.javaVersionData = javaVersionData;
	}

	public String getName() {
		return this.name;
	}

	public Collection<File> getSources() {
		return this.sources;
	}

	public Collection<File> getSourceDirectories() {
		return this.sourceDirectories;
	}

	public Collection<File> getJava() {
		return this.java;
	}

	public Collection<File> getClassesDirs() {
		return this.classesDirs;
	}

	public Collection<File> getCompileClasspath() {
		return this.compileClasspath;
	}

	public Collection<File> getImplementationClasspath() {
		return this.implementationClasspath;
	}

	public JavaVersionDataImpl getJavaVersionData() {
		return this.javaVersionData;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		JavaSourceSetDataImpl that = (JavaSourceSetDataImpl) o;
		return Objects.equals(name, that.name) && Objects.equals(sources, that.sources)
				&& Objects.equals(sourceDirectories, that.sourceDirectories) && Objects.equals(java, that.java)
				&& Objects.equals(classesDirs, that.classesDirs)
				&& Objects.equals(compileClasspath, that.compileClasspath)
				&& Objects.equals(implementationClasspath, that.implementationClasspath)
				&& Objects.equals(javaVersionData, that.javaVersionData);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, sources, sourceDirectories, java, classesDirs, compileClasspath,
				implementationClasspath, javaVersionData);
	}

	public String toString() {
		return "JavaSourceSetDataImpl(name=" + this.getName() + ", sources=" + this.getSources()
				+ ", sourceDirectories=" + this.getSourceDirectories() + ", java=" + this.getJava() + ", classesDirs="
				+ this.getClassesDirs() + ", compileClasspath=" + this.getCompileClasspath()
				+ ", implementationClasspath=" + this.getImplementationClasspath() + ", javaVersionData="
				+ this.getJavaVersionData() + ")";
	}

}
