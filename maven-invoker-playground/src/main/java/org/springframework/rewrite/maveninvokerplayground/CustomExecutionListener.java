package org.springframework.rewrite.maveninvokerplayground;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.plugin.MojoExecutionException;
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

    }

    @Override
    public void sessionStarted(ExecutionEvent executionEvent) {
        eventConsumer.accept(executionEvent);
    }

    @Override
    public void sessionEnded(ExecutionEvent executionEvent) {
        eventConsumer.accept(executionEvent);
    }

    @Override
    public void projectSkipped(ExecutionEvent executionEvent) {
        eventConsumer.accept(executionEvent);
    }

    @Override
    public void projectStarted(ExecutionEvent executionEvent) {
        eventConsumer.accept(executionEvent);
    }

    @Override
    public void projectSucceeded(ExecutionEvent executionEvent) {
        eventConsumer.accept(executionEvent);
    }

    @Override
    public void projectFailed(ExecutionEvent executionEvent) {
        eventConsumer.accept(executionEvent);
    }

    @Override
    public void mojoSkipped(ExecutionEvent executionEvent) {
        eventConsumer.accept(executionEvent);
    }

    @Override
    public void mojoStarted(ExecutionEvent executionEvent) {
        eventConsumer.accept(executionEvent);
    }

    @Override
    public void mojoSucceeded(ExecutionEvent executionEvent) {
        eventConsumer.accept(executionEvent);
    }

    @Override
    public void mojoFailed(ExecutionEvent executionEvent) {
        eventConsumer.accept(executionEvent);
    }

    @Override
    public void forkStarted(ExecutionEvent executionEvent) {
        eventConsumer.accept(executionEvent);
    }

    @Override
    public void forkSucceeded(ExecutionEvent executionEvent) {

    }

    @Override
    public void forkFailed(ExecutionEvent executionEvent) {

    }

    @Override
    public void forkedProjectStarted(ExecutionEvent executionEvent) {

    }

    @Override
    public void forkedProjectSucceeded(ExecutionEvent executionEvent) {

    }

    @Override
    public void forkedProjectFailed(ExecutionEvent executionEvent) {

    }

    // Implement other methods (mojoFailed, mojoSucceeded, etc.) as needed
}
