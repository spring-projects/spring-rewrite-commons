package org.springframework.rewrite.maveninvokerplayground;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;

import java.util.function.Consumer;

@Component(role = AbstractEventSpy.class, hint = "customExecutionListener")
public class CustomExecutionListener extends AbstractEventSpy implements ExecutionListener {

    private final Consumer<Object> eventConsumer;

    public CustomExecutionListener(Consumer<Object> eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    @Override
    public void init(Context context) throws Exception {
        // Initialization code here
    }

    @Override
    public void onEvent(Object event) throws Exception {
        eventConsumer.accept(event);
        if (event instanceof ExecutionEvent) {
            // Handle the execution event
            eventConsumer.accept(event);
        }
    }

    @Override
    public void projectDiscoveryStarted(ExecutionEvent executionEvent) {
        eventConsumer.accept("Project discovery started: " + getGav(executionEvent));
    }

    @Override
    public void sessionStarted(ExecutionEvent executionEvent) {
        eventConsumer.accept("Session started: " + getGav(executionEvent));
    }

    @Override
    public void sessionEnded(ExecutionEvent executionEvent) {
        eventConsumer.accept("Session ended: " + getGav(executionEvent));
    }

    @Override
    public void projectSkipped(ExecutionEvent executionEvent) {
        eventConsumer.accept("Project was skipped: " + getGav(executionEvent));
    }

    @Override
    public void projectStarted(ExecutionEvent executionEvent) {
        eventConsumer.accept("Project started: " + getGav(executionEvent));
    }

    @Override
    public void projectSucceeded(ExecutionEvent executionEvent) {
        eventConsumer.accept("Project succeeded: " + getGav(executionEvent));
        
    }

    @Override
    public void projectFailed(ExecutionEvent executionEvent) {
        eventConsumer.accept("Project failed: " + getGav(executionEvent));
        
    }

    @Override
    public void mojoSkipped(ExecutionEvent executionEvent) {
        eventConsumer.accept("Mojo skipped: " + getGav(executionEvent));
        
    }

    @Override
    public void mojoStarted(ExecutionEvent executionEvent) {
        eventConsumer.accept("Mojo started: " + getGav(executionEvent));
        
    }

    @Override
    public void mojoSucceeded(ExecutionEvent executionEvent) {
        eventConsumer.accept("Mojo succeeded: " + getGav(executionEvent));
        
    }

    @Override
    public void mojoFailed(ExecutionEvent executionEvent) {
        eventConsumer.accept("Mojo failed: " + getGav(executionEvent));
        
    }

    @Override
    public void forkStarted(ExecutionEvent executionEvent) {
        eventConsumer.accept("fork started: " + getGav(executionEvent));
        
    }

    @Override
    public void forkSucceeded(ExecutionEvent executionEvent) {
        eventConsumer.accept("fork suvveeded: " + getGav(executionEvent));
    }

    @Override
    public void forkFailed(ExecutionEvent executionEvent) {
        eventConsumer.accept("fork failed: " + getGav(executionEvent));
    }

    @Override
    public void forkedProjectStarted(ExecutionEvent executionEvent) {
        eventConsumer.accept("forked project started: " + getGav(executionEvent));
    }

    @Override
    public void forkedProjectSucceeded(ExecutionEvent executionEvent) {
        eventConsumer.accept("forked project succeeded: " + getGav(executionEvent));
    }

    @Override
    public void forkedProjectFailed(ExecutionEvent executionEvent) {
        eventConsumer.accept("forkedProjectFailed: " + getGav(executionEvent));
    }


    // Implement other methods (mojoFailed, mojoSucceeded, etc.) as needed
    private static String getGav(ExecutionEvent executionEvent) {
        MavenProject currentProject = executionEvent.getSession().getCurrentProject();
        return currentProject == null ? "unknown" : currentProject.getGroupId() + ":" + currentProject.getArtifactId();
    }
}
