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
package org.springframework.rewrite.gradle;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.style.NamedStyles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.rewrite.gradle.model.GradleProjectData;
import org.springframework.rewrite.gradle.model.SpringRewriteModelBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class ProjectParserTest {

	private static final Logger log = LoggerFactory.getLogger(ProjectParserTest.class);

	private static final Options OPTIONS = new Options(Collections.emptyList(), false, Collections.emptyList(),
			Integer.MAX_VALUE, Collections.emptyList());

	private Path downloadPetclinic(Path dir) throws Exception {
		unzip(new URL(
				"https://github.com/spring-projects/spring-petclinic/archive/0aa3adb56f500c41564411c32cd301affe284ecc.zip"),
				dir);
		return Files.list(dir)
			.filter(Files::isDirectory)
			.filter(p -> p.getFileName().toString().startsWith("spring-petclinic-"))
			.findFirst()
			.orElseThrow();
	}

	private static void unzip(URL url, Path destDir) throws IOException {
		File dir = destDir.toFile();
		// create output directory if it doesn't exist
		if (!dir.exists())
			dir.mkdirs();
		InputStream is;
		// buffer for read and write data to file
		byte[] buffer = new byte[1024];
		try {
			is = url.openStream();
			ZipInputStream zis = new ZipInputStream(is);
			ZipEntry ze = zis.getNextEntry();
			while (ze != null) {
				if (!ze.isDirectory()) {
					String fileName = ze.getName();
					File newFile = new File(destDir + File.separator + fileName);
					System.out.println("Unzipping to " + newFile.getAbsolutePath());
					// create directories for sub directories in zip
					new File(newFile.getParent()).mkdirs();
					FileOutputStream fos = new FileOutputStream(newFile);
					int len;
					while ((len = zis.read(buffer)) > 0) {
						fos.write(buffer, 0, len);
					}
					fos.close();
				}
				// close this ZipEntry
				zis.closeEntry();
				ze = zis.getNextEntry();
			}
			// close last ZipEntry
			zis.closeEntry();
			zis.close();
			is.close();
		}
		catch (IOException e) {
			throw e;
		}
	}

	@Test
	void sanity(@TempDir Path dir) throws Exception {
		Path petClinic = downloadPetclinic(dir.resolve("petclinic"));
		GradleProjectData gp = SpringRewriteModelBuilder.forProjectDirectory(GradleProjectData.class,
				petClinic.toFile(), petClinic.resolve("build.gradle").toFile());
		List<SourceFile> sources = new ProjectParser(gp, OPTIONS, log)
			.parse(new InMemoryExecutionContext(t -> Assertions.fail("Parser Error", t)))
			.toList();
		assertThat(sources.size()).isEqualTo(114);
	}

}
