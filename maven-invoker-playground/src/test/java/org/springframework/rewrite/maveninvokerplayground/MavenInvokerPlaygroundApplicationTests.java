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
package org.springframework.rewrite.maveninvokerplayground;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.cli.CliRequest;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.shared.invoker.*;
import org.codehaus.plexus.*;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.BuiltProject;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.EmbeddedMaven;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.invoker.equipped.MavenInvokerEquippedEmbeddedMaven;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.maven.MavenMojoProjectParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.apache.maven.artifact.ArtifactScopeEnum.runtime;
import static org.assertj.core.api.Assertions.assertThat;

//@SpringBootTest
class MavenInvokerPlaygroundApplicationTests {

    Logger logger = LoggerFactory.getLogger(MavenInvokerPlaygroundApplicationTests.class);

    private Path projectDir = Path.of("./../spring-rewrite-commons-launcher/testcode/maven-projects/simple-spring-boot").toAbsolutePath().normalize();
    private Path pomPath = projectDir.resolve("pom.xml");

    @Test
    @DisplayName("custom MavenExecutor")
    void customMavenCli() {

        List<MavenProject> allProjects = new ArrayList<>();
        List<String> compileClasspathElements = new ArrayList<>();
        AtomicReference<MavenSession> sessionHolder = new AtomicReference<>();
        AtomicReference<RuntimeInformation> runtimeInformationHolder = new AtomicReference<>();

        MavenExecutor mavenExecutor = new MavenExecutor(new AbstractExecutionListener() {
            @Override
            public void projectSucceeded(ExecutionEvent executionEvent) {
                // The list of projects building the reactor
                MavenSession mavenSession = executionEvent.getSession();
                sessionHolder.set(mavenSession);
                PlexusContainer plexusContainer = mavenSession.getContainer();
                RuntimeInformation runtimeInformation = null;
                try {
                    runtimeInformation = plexusContainer.lookup(RuntimeInformation.class);
                } catch (ComponentLookupException e) {
                    throw new RuntimeException(e);
                }
                runtimeInformationHolder.set(runtimeInformation);
                allProjects.addAll(mavenSession.getAllProjects());
                try {
                    // Compile classpath elements (target/classes)
                    compileClasspathElements.addAll(allProjects.get(0).getCompileClasspathElements());
                } catch (DependencyResolutionRequiredException e) {
                    throw new RuntimeException(e);
                }
            }
        }, logger);



        // TODO: remove requirement to set path through properties
        System.setProperty("maven.multiModuleProjectDirectory", projectDir.toString());
        int result = mavenExecutor.execute(new String[]{"clean", "install"}, projectDir.toString(), System.out, System.err);

        boolean pomCacheEnabled = true;
        @Nullable String pomCacheDirectory = Path.of(System.getProperty("user.home")).resolve(".rewrite/cache").toString();
        boolean skipMavenParsing = false;
        Collection<String> exclusions = new ArrayList<>();
        Collection<String> plainTextMasks = Set.of("*.txt");
        int sizeThresholdMb = 10;
        MavenSession mavenSession = sessionHolder.get();
        SettingsDecrypter settingsDecrypter = null;
        boolean runPerSubmodule = false;
        RuntimeInformation runtimeInformation = runtimeInformationHolder.get();
        MavenMojoProjectParser mojoProjectParser = new MavenMojoProjectParser(new Slf4jToMavenLoggerAdapter(logger), projectDir, pomCacheEnabled, pomCacheDirectory, runtimeInformation, skipMavenParsing, exclusions, plainTextMasks, sizeThresholdMb, mavenSession, settingsDecrypter, runPerSubmodule);

        assertThat(allProjects).hasSize(1);
        assertThat(compileClasspathElements).hasSize(1);
        Path targetClasses = projectDir.resolve("target/classes");
        assertThat(compileClasspathElements.get(0)).isEqualTo(targetClasses.toString());
    }


    @Test
    @DisplayName("getting crazy")
    void gettingCrazy() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException, PlexusContainerException {

        String[] mavenArgs = {"clean", "install"};
        String[] combinedArgs = new String[mavenArgs.length + 1];
        System.arraycopy(mavenArgs, 0, combinedArgs, 1, mavenArgs.length);
        combinedArgs[0] = ""; // First argument is the program name, which is not used
        System.setProperty("maven.multiModuleProjectDirectory", projectDir.toString());

        // Use reflection to access the DelegatingCliRequest constructor
        DefaultPlexusContainer defaultPlexusContainer = null; //createPlexusContainer();
        ClassWorld classWorld = defaultPlexusContainer.getClassWorld();
        Constructor<CliRequest> constructor = CliRequest.class.getDeclaredConstructor(String[].class, classWorld.getClass());
        constructor.setAccessible(true); // Make the constructor accessible

        // Create a DelegatingCliRequest instance
        CliRequest cliRequest = constructor.newInstance((Object) combinedArgs, classWorld);
        MavenExecutionRequest request = cliRequest.getRequest();
        request.setBaseDirectory(projectDir.toFile());
        Properties properties = new Properties();
        request.setUserProperties(properties);
        request.setGoals(List.of("clean", "package"));
        request.addRemoteRepository(new MavenArtifactRepository("central", "https://repo.maven.apache.org/maven2/", new DefaultRepositoryLayout(), new ArtifactRepositoryPolicy(true, null, null), new ArtifactRepositoryPolicy(true, null, null)));
        cliRequest.setUserProperties(System.getProperties());
        // Instantiate MavenExecutor and call doMain with the DelegatingCliRequest
        MavenCli mavenCli = new MavenCli();
        int result = mavenCli.doMain(cliRequest);

        System.out.println("Maven build finished with exit code " + result);

    }

    @Test
    @DisplayName("Maven MavenEmbedder")
    void mavenMavenEmbedder() {
        MavenCli cli = new MavenCli();

    }


    @Test
    @DisplayName("shrinkwrap Embedded Maven")
    void shrinkwrapEmbeddedMaven() {
        Invoker invoker = new DefaultInvoker();
        InvocationRequest request = new DefaultInvocationRequest();
        request.setBaseDirectory(projectDir.toFile());
        request.setPomFile(pomPath.toFile());
        request.setGoals(List.of("clean", "install"));
        request.setMavenOpts("-DskipTests");
        request.setOutputHandler(s -> System.out.println(s));
        MavenInvokerEquippedEmbeddedMaven embeddedMaven = EmbeddedMaven.withMavenInvokerSet(request, invoker);
        BuiltProject build = embeddedMaven.build();
        List<BuiltProject> modules = build.getModules();
        List<Dependency> dependencies = build.getModel().getDependencies();
        System.out.println(dependencies);
    }

    @Test
    @DisplayName("nasty tricks")
    void nastyTricks() {
        MavenCli cli = new MavenCli();
        System.setProperty("maven.multiModuleProjectDirectory", projectDir.toString());
        int result = cli.doMain(new String[]{"clean", "install"}, projectDir.toString(), System.out, System.err);

        if (result != 0) {
            System.out.println("Build encountered exceptions.");
        } else {
            System.out.println("Build finished successfully.");
        }
    }

    @Test
    @Disabled("Not feasable")
    void mavenInvoker() {
        InvocationRequest request = new DefaultInvocationRequest();
        request.setOutputHandler(new InvocationOutputHandler() {
            @Override
            public void consumeLine(String s) throws IOException {
                System.out.println(s);
            }
        });
        request.setPomFile(pomPath.toFile());
        request.setGoals(Collections.singletonList("clean install")); // Specify Maven goals
        Properties properties = new Properties();
        request.setProperties(properties);
        Invoker invoker = new DefaultInvoker();

        try {
            InvocationResult result = invoker.execute(request);

            if (result.getExitCode() != 0) {
                System.err.println("Build failed.");
                // Handle build failure
            }
        } catch (MavenInvocationException e) {
            e.printStackTrace();
        }
    }


    private void handleEvent(Object e) {
    }

}
