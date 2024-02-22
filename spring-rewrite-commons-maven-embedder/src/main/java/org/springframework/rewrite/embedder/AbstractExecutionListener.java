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
package org.springframework.rewrite.embedder;

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
