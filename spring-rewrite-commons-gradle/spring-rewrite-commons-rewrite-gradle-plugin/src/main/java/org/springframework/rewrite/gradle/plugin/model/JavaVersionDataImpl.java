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

import org.springframework.rewrite.gradle.model.JavaVersionData;

import java.io.Serializable;
import java.util.Objects;

final class JavaVersionDataImpl implements JavaVersionData, Serializable {

	private final String createdBy;

	private final String vmVendor;

	private final String sourceCompatibility;

	private final String targetCompatibility;

	public JavaVersionDataImpl(String createdBy, String vmVendor, String sourceCompatibility,
			String targetCompatibility) {
		this.createdBy = createdBy;
		this.vmVendor = vmVendor;
		this.sourceCompatibility = sourceCompatibility;
		this.targetCompatibility = targetCompatibility;
	}

	public String getCreatedBy() {
		return this.createdBy;
	}

	public String getVmVendor() {
		return this.vmVendor;
	}

	public String getSourceCompatibility() {
		return this.sourceCompatibility;
	}

	public String getTargetCompatibility() {
		return this.targetCompatibility;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		JavaVersionDataImpl that = (JavaVersionDataImpl) o;
		return Objects.equals(createdBy, that.createdBy) && Objects.equals(vmVendor, that.vmVendor)
				&& Objects.equals(sourceCompatibility, that.sourceCompatibility)
				&& Objects.equals(targetCompatibility, that.targetCompatibility);
	}

	@Override
	public int hashCode() {
		return Objects.hash(createdBy, vmVendor, sourceCompatibility, targetCompatibility);
	}

	public String toString() {
		return "JavaVersionDataImpl(createdBy=" + this.getCreatedBy() + ", vmVendor=" + this.getVmVendor()
				+ ", sourceCompatibility=" + this.getSourceCompatibility() + ", targetCompatibility="
				+ this.getTargetCompatibility() + ")";
	}

}