package org.springframework.rewrite.maveninvokerplayground;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.maven.cli.CliRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.codehaus.plexus.classworlds.ClassWorld;

/**
 * DelegatingCliRequest
 */
public class DelegatingCliRequest extends org.apache.maven.cli.CliRequest {
    private final CliRequest delegate1;

    public DelegatingCliRequest(CliRequest delegate){
        delegate1 = delegate;
    }


    @Override
    public String[] getArgs() {
        return delegate.getArgs();
    }

    @Override
    public CommandLine getCommandLine() {
        return delegate.getCommandLine();
    }

    @Override
    public ClassWorld getClassWorld() {
        return delegate.getClassWorld();
    }

    @Override
    public String getWorkingDirectory() {
        return delegate.getWorkingDirectory();
    }

    @Override
    public File getMultiModuleProjectDirectory() {
        return delegate.getMultiModuleProjectDirectory();
    }

    @Override
    public boolean isDebug() {
        return delegate.isDebug();
    }

    @Override
    public boolean isQuiet() {
        return delegate.isQuiet();
    }

    @Override
    public boolean isShowErrors() {
        return delegate.isShowErrors();
    }

    @Override
    public Properties getUserProperties() {
        return delegate.getUserProperties();
    }

    @Override
    public Properties getSystemProperties() {
        return delegate.getSystemProperties();
    }

    @Override
    public MavenExecutionRequest getRequest() {
        return delegate.getRequest();
    }

    @Override
    public void setUserProperties(Properties properties) {
        delegate.setUserProperties(properties);
    }

    private final org.apache.maven.cli.CliRequest delegate;


}