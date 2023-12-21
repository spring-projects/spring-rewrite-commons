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

import lombok.AllArgsConstructor;
import lombok.Value;
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

	@AllArgsConstructor
	@Value
	static class FeaturePreviewImpl implements FeaturePreview, Serializable {

		String name;

		boolean active;

		boolean enabled;

	}

	@AllArgsConstructor
	@Value
	static class GradleSettingsImpl implements GradleSettings, Serializable {

		List<MavenRepository> pluginRepositories;

		List<GradlePluginDescriptor> plugins;

		Map<String, FeaturePreview> featurePreviews;

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
