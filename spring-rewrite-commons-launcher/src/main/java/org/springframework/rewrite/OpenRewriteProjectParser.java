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
package org.springframework.rewrite;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.openrewrite.ExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.config.Environment;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.maven.*;
import org.openrewrite.style.NamedStyles;
import org.slf4j.LoggerFactory;
import org.springframework.rewrite.maveninvokerplayground.MavenExecutor;
import org.springframework.rewrite.maveninvokerplayground.Slf4jToMavenLoggerAdapter;
import org.springframework.rewrite.parser.RewriteProjectParsingResult;
import org.springframework.rewrite.parser.SpringRewriteProperties;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * @author Fabian Krüger
 */
public class OpenRewriteProjectParser {

    private final SpringRewriteProperties properties;
    private final ExecutionContext executionContext;

    public OpenRewriteProjectParser(SpringRewriteProperties properties, ExecutionContext executionContext) {
        this.properties = properties;
        this.executionContext = executionContext;
    }

    public RewriteProjectParsingResult parse(Path givenBaseDir) {

        AtomicReference<List<SourceFile>> mavenSessionRef = new AtomicReference<>();
        new MavenExecutor(executionEvent -> {
            MavenSession mavenSession = executionEvent.getSession();
            Log logger = new Slf4jToMavenLoggerAdapter(LoggerFactory.getLogger("OpenRewriteProjectParser"));
            boolean pomCacheEnabled = properties.isPomCacheEnabled();
            @Nullable String pomCacheDirectory = properties.getPomCacheDirectory();
            Class<RuntimeInformation> aClass = RuntimeInformation.class;
            RuntimeInformation runtimeInformation = lookup(mavenSession.getContainer(), aClass);
            boolean skipMavenParsing = properties.isSkipMavenParsing();
            Collection<String> exclusions = properties.getIgnoredPathPatterns();
            Collection<String> plainTextMasks = properties.getPlainTextMasks();
            int sizeThresholdMb = properties.getSizeThresholdMb();
            SettingsDecrypter settingsDecrypter = lookup(mavenSession.getContainer(), SettingsDecrypter.class);
            boolean runPerSubmodule = properties.isRunPerSubmodule();
            boolean parseAdditionalResources = properties.isParseAdditionalResources();

            MavenMojoProjectParser mojoProjectParser = new MavenMojoProjectParser(logger, givenBaseDir, pomCacheEnabled, pomCacheDirectory, runtimeInformation, skipMavenParsing, exclusions, plainTextMasks, sizeThresholdMb, mavenSession, settingsDecrypter, runPerSubmodule, parseAdditionalResources);
            FakedRewriteRunMojo fakedRewriteRunMojo = FakedRewriteRunMojo.from(mavenSession);
            Environment env = Environment.builder().build();
            try {
                // LargeSoruceSet hides access to source files
//                LargeSourceSet largeSourceSet = fakedRewriteRunMojo.loadSourceSet(givenBaseDir, env, executionContext);
                List<SourceFile> sourceFiles = fakedRewriteRunMojo.loadSources(givenBaseDir, env, executionContext);
                mavenSessionRef.set(sourceFiles);
            } catch (DependencyResolutionRequiredException e) {
                throw new RuntimeException(e);
            } catch (MojoExecutionException e) {
                throw new RuntimeException(e);
            }
        }).execute(List.of("clean", "package", "--fail-at-end"), givenBaseDir);

        RewriteProjectParsingResult parsingResult = new RewriteProjectParsingResult(mavenSessionRef.get(), executionContext);

        return parsingResult;
    }

    private  static <T> T lookup(PlexusContainer plexusContainer, Class<T> aClass) {
        try {
            return plexusContainer.lookup(aClass);
        } catch (ComponentLookupException e) {
            throw new RuntimeException(e);
        }
    }

    private static class FakedRewriteRunMojo extends AbstractRewriteDryRunMojo {
        private final MavenSession mavenSession1;

        public FakedRewriteRunMojo(MavenSession mavenSession) {
            mavenSession1 = mavenSession;
        }

        public static FakedRewriteRunMojo from(MavenSession mavenSession) {
            FakedRewriteRunMojo fakedRewriteRunMojo = new FakedRewriteRunMojo(mavenSession);
            // project
            setField(fakedRewriteRunMojo, "project", mavenSession.getCurrentProject());
            // runtime
            PlexusContainer plexusContainer = mavenSession.getContainer();
            RuntimeInformation runtimeInformation = lookup(plexusContainer, RuntimeInformation.class);
            setField(fakedRewriteRunMojo, "runtime", runtimeInformation);
            setField(fakedRewriteRunMojo, "mavenSession", mavenSession);
            setField(fakedRewriteRunMojo, "settingsDecrypter", lookup(plexusContainer, SettingsDecrypter.class));
            return fakedRewriteRunMojo;
        }

        private static void setField(FakedRewriteRunMojo fakedRewriteRunMojo, String fieldName, Object value) {
            Field project = ReflectionUtils.findField(FakedRewriteRunMojo.class, fieldName);
            ReflectionUtils.makeAccessible(project);
            ReflectionUtils.setField(project, fakedRewriteRunMojo, value);
        }

        @Override
        public ResultsContainer listResults(ExecutionContext ctx) {
            try {
                super.project = mavenSession.getCurrentProject();
                return super.listResults(ctx);
            } catch (MojoExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        public List<SourceFile> loadSources(Path repositoryRoot, Environment env, ExecutionContext ctx) throws DependencyResolutionRequiredException, MojoExecutionException {
            List<NamedStyles> styles = loadStyles(project, env);

            //Parse and collect source files from each project in the maven session.
            MavenMojoProjectParser projectParser = new MavenMojoProjectParser(getLog(), repositoryRoot, pomCacheEnabled, pomCacheDirectory, runtime, skipMavenParsing, getExclusions(), getPlainTextMasks(), sizeThresholdMb, mavenSession, settingsDecrypter, runPerSubmodule, true);

            Stream<SourceFile> sourceFiles = projectParser.listSourceFiles(project, styles, ctx);
            Method m = ReflectionUtils.findMethod(AbstractRewriteMojo.class, "sourcesWithAutoDetectedStyles", Stream.class);
            ReflectionUtils.makeAccessible(m);
            List<SourceFile> sourceFileList = (List<SourceFile>) ReflectionUtils.invokeMethod(m, this, sourceFiles);
            return sourceFileList;
        }
    }

    /**
     * Container for gathered Maven runtime information required for parsing.
     */
    private record MavenRuntimeInformation(RuntimeInformation runtimeInformation) {

        public static MavenRuntimeInformation gathering(MavenSession mavenSession) {

            RuntimeInformation runtimeInformation = lookup(mavenSession.getContainer(), RuntimeInformation.class);


            MavenRuntimeInformation mavenRuntimeInformation = new MavenRuntimeInformation(runtimeInformation);
            return mavenRuntimeInformation;
        }

        private  static <T> T lookup(PlexusContainer plexusContainer, Class<T> aClass) {
            try {
                return plexusContainer.lookup(aClass);
            } catch (ComponentLookupException e) {
                throw new RuntimeException(e);
            }
        }

    }
}
