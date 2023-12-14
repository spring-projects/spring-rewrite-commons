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

import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.nio.file.Path;

/**
 * Utility class helping with different OS {@link Path}s.
 *
 * @author fkrueger
 */
public class LinuxWindowsPathUnifier {

	public static Path relativize(Path subpath, Path path) {
		LinuxWindowsPathUnifier linuxWindowsPathUnifier = new LinuxWindowsPathUnifier();
		String unifiedAbsoluteRootPath = linuxWindowsPathUnifier.unifiedPathString(subpath);
		String pathUnified = linuxWindowsPathUnifier.unifiedPathString(path);
		return Path.of(unifiedAbsoluteRootPath).relativize(Path.of(pathUnified));
	}

	public static String unifiedPathString(Path path) {
		return unifiedPathString(path.toString());
	}

	public static Path unifiedPath(Path path) {
		return Path.of(unifiedPathString(path));
	}

	public static String unifiedPathString(Resource r) {
		return unifiedPathString(ResourceUtil.getPath(r));
	}

	public static String unifiedPathString(String path) {
		path = StringUtils.cleanPath(path);
		if (isWindows()) {
			path = transformToLinuxPath(path);
		}
		return path;
	}

	public static Path unifiedPath(String path) {
		return Path.of(unifiedPathString(path));
	}

	static boolean isWindows() {
		return System.getProperty("os.name").contains("Windows");
	}

	private static String transformToLinuxPath(String path) {
		return path.replaceAll("^[\\w]+:\\/?", "/");
	}

	public static boolean pathEquals(Resource r, Path path) {
		return unifiedPathString(ResourceUtil.getPath(r)).equals(unifiedPathString(path.normalize()));
	}

	public static boolean pathEquals(Path basedir, String parentPomPath) {
		return unifiedPathString(basedir).equals(parentPomPath);
	}

	public static boolean pathEquals(Path path1, Path path2) {
		return unifiedPathString(path1).equals(unifiedPathString(path2));
	}

	public static boolean pathStartsWith(Resource r, Path path) {
		return ResourceUtil.getPath(r).toString().startsWith(unifiedPathString(path));
	}

}
