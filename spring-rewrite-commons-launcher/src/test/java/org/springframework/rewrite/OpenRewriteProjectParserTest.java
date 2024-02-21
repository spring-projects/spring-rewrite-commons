package org.springframework.rewrite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.rewrite.boot.autoconfigure.RewriteLauncherConfiguration;
import org.springframework.rewrite.test.util.TestProjectHelper;

import java.nio.file.Path;

/**
 * @author Fabian Krüger
 */
@SpringBootTest(classes = RewriteLauncherConfiguration.class)
class OpenRewriteProjectParserTest {

    @Autowired
    private OpenRewriteProjectParser sut;
    
    @Test
    @DisplayName("test")
    void test() {
        Path mavenProject = TestProjectHelper.getMavenProject("test1");
        sut.parse(mavenProject);
    }

}