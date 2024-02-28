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

import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.initialization.DefaultSettings;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.UnknownServiceException;
import org.gradle.util.GradleVersion;
import org.openrewrite.gradle.toolingapi.FeaturePreview;
import org.openrewrite.gradle.toolingapi.GradlePluginDescriptor;
import org.openrewrite.gradle.toolingapi.GradleSettings;
import org.openrewrite.gradle.toolingapi.MavenRepository;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class GradleToolingApiSettingsBuilder {

	static final class FeaturePreviewImpl implements FeaturePreview, Serializable {

		private final String name;

		private final boolean active;

		private final boolean enabled;

		public FeaturePreviewImpl(String name, boolean active, boolean enabled) {
			this.name = name;
			this.active = active;
			this.enabled = enabled;
		}

		public String getName() {
			return this.name;
		}

		public boolean isActive() {
			return this.active;
		}

		public boolean isEnabled() {
			return this.enabled;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			FeaturePreviewImpl that = (FeaturePreviewImpl) o;
			return active == that.active && enabled == that.enabled && Objects.equals(name, that.name);
		}

		@Override
		public int hashCode() {
			return Objects.hash(name, active, enabled);
		}

		public String toString() {
			return "GradleToolingApiSettingsBuilder.FeaturePreviewImpl(name=" + this.getName() + ", active="
					+ this.isActive() + ", enabled=" + this.isEnabled() + ")";
		}

	}

	static final class GradleSettingsImpl implements GradleSettings, Serializable {

		private final List<MavenRepository> pluginRepositories;

		private final List<GradlePluginDescriptor> plugins;

		private final Map<String, FeaturePreview> featurePreviews;

		public GradleSettingsImpl(List<MavenRepository> pluginRepositories, List<GradlePluginDescriptor> plugins,
				Map<String, FeaturePreview> featurePreviews) {
			this.pluginRepositories = pluginRepositories;
			this.plugins = plugins;
			this.featurePreviews = featurePreviews;
		}

		public List<MavenRepository> getPluginRepositories() {
			return this.pluginRepositories;
		}

		public List<GradlePluginDescriptor> getPlugins() {
			return this.plugins;
		}

		public Map<String, FeaturePreview> getFeaturePreviews() {
			return this.featurePreviews;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			GradleSettingsImpl that = (GradleSettingsImpl) o;
			return Objects.equals(pluginRepositories, that.pluginRepositories) && Objects.equals(plugins, that.plugins)
					&& Objects.equals(featurePreviews, that.featurePreviews);
		}

		@Override
		public int hashCode() {
			return Objects.hash(pluginRepositories, plugins, featurePreviews);
		}

		public String toString() {
			return "GradleToolingApiSettingsBuilder.GradleSettingsImpl(pluginRepositories="
					+ this.getPluginRepositories() + ", plugins=" + this.getPlugins() + ", featurePreviews="
					+ this.getFeaturePreviews() + ")";
		}

	}

	public static GradleSettings gradleSettings(Settings settings) {
		if (settings == null) {
			return null;
		}
		Set<MavenRepository> pluginRepositories = new HashSet<>();
		pluginRepositories
			.addAll(GradleToolingApiProjectBuilder.mapRepositories(settings.getPluginManagement().getRepositories()));
		pluginRepositories
			.addAll(GradleToolingApiProjectBuilder.mapRepositories(settings.getBuildscript().getRepositories()));
		if (pluginRepositories.isEmpty()) {
			pluginRepositories.add(GradleToolingApiProjectBuilder.GRADLE_PLUGIN_PORTAL);
		}

		return new GradleSettingsImpl(new ArrayList<>(pluginRepositories),
				GradleToolingApiProjectBuilder.pluginDescriptors(settings.getPluginManager()),
				featurePreviews((DefaultSettings) settings));
	}

	private static Map<String, FeaturePreview> featurePreviews(DefaultSettings settings) {
		if (GradleVersion.current().compareTo(GradleVersion.version("4.6")) < 0) {
			return Collections.emptyMap();
		}

		Map<String, FeaturePreview> featurePreviews = new HashMap<>();
		FeaturePreviews gradleFeaturePreviews = getService(settings, FeaturePreviews.class);
		if (gradleFeaturePreviews != null) {
			FeaturePreviews.Feature[] gradleFeatures = FeaturePreviews.Feature.values();
			for (FeaturePreviews.Feature feature : gradleFeatures) {
				// Unclear how enabled status can be determined in latest gradle APIs
				featurePreviews.put(feature.name(), new FeaturePreviewImpl(feature.name(), feature.isActive(), false));
			}
		}
		return featurePreviews;
	}

	private static <T> T getService(DefaultSettings settings,
			@SuppressWarnings("SameParameterValue") Class<T> serviceType) {
		try {
			Method services = settings.getClass().getDeclaredMethod("getServices");
			services.setAccessible(true);
			ServiceRegistry serviceRegistry = (ServiceRegistry) services.invoke(settings);
			return serviceRegistry.get(serviceType);
		}
		catch (UnknownServiceException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
			return null;
		}
	}

}
