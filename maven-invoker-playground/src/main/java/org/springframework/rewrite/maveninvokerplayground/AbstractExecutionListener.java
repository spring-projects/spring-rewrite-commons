package org.springframework.rewrite.maveninvokerplayground;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;

import java.util.function.Consumer;

@Component(role = AbstractEventSpy.class, hint = "customExecutionListener")
public abstract class AbstractExecutionListener extends AbstractEventSpy implements ExecutionListener {


    public AbstractExecutionListener() {
    }

    @Override
    public void init(Context context) throws Exception {
    }

    @Override
    public void onEvent(Object event) throws Exception {
    }

    @Override
    public void projectDiscoveryStarted(ExecutionEvent executionEvent) {
    }

    @Override
    public void sessionStarted(ExecutionEvent executionEvent) {
    }

    @Override
    public void sessionEnded(ExecutionEvent executionEvent) {
    }

    @Override
    public void projectSkipped(ExecutionEvent executionEvent) {
    }

    @Override
    public void projectStarted(ExecutionEvent executionEvent) {
    }

    @Override
    public void projectSucceeded(ExecutionEvent executionEvent) {
    }

    @Override
    public void projectFailed(ExecutionEvent executionEvent) {
    }

    @Override
    public void mojoSkipped(ExecutionEvent executionEvent) {
    }

    @Override
    public void mojoStarted(ExecutionEvent executionEvent) {
    }

    @Override
    public void mojoSucceeded(ExecutionEvent executionEvent) {
    }

    @Override
    public void mojoFailed(ExecutionEvent executionEvent) {
    }

    @Override
    public void forkStarted(ExecutionEvent executionEvent) {
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
}

