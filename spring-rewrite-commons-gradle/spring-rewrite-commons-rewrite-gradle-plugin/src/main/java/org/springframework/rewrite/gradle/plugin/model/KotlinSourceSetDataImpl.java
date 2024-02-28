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

import org.springframework.rewrite.gradle.model.KotlinSourceSetData;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;

final class KotlinSourceSetDataImpl implements KotlinSourceSetData, Serializable {

	private final String name;

	private final Collection<File> kotlin;

	private final Collection<File> compileClasspath;

	private final Collection<File> implementationClasspath;

	public KotlinSourceSetDataImpl(String name, Collection<File> kotlin, Collection<File> compileClasspath,
			Collection<File> implementationClasspath) {
		this.name = name;
		this.kotlin = kotlin;
		this.compileClasspath = compileClasspath;
		this.implementationClasspath = implementationClasspath;
	}

	public String getName() {
		return this.name;
	}

	public Collection<File> getKotlin() {
		return this.kotlin;
	}

	public Collection<File> getCompileClasspath() {
		return this.compileClasspath;
	}

	public Collection<File> getImplementationClasspath() {
		return this.implementationClasspath;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		KotlinSourceSetDataImpl that = (KotlinSourceSetDataImpl) o;
		return Objects.equals(name, that.name) && Objects.equals(kotlin, that.kotlin)
				&& Objects.equals(compileClasspath, that.compileClasspath)
				&& Objects.equals(implementationClasspath, that.implementationClasspath);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, kotlin, compileClasspath, implementationClasspath);
	}

	public String toString() {
		return "KotlinSourceSetDataImpl(name=" + this.getName() + ", kotlin=" + this.getKotlin() + ", compileClasspath="
				+ this.getCompileClasspath() + ", implementationClasspath=" + this.getImplementationClasspath() + ")";
	}

}