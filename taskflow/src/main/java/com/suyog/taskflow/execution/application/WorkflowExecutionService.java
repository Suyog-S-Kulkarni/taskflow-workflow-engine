package com.suyog.taskflow.execution.application;

import com.suyog.taskflow.execution.domain.WorkflowExecutionReport;
import com.suyog.taskflow.workflow.domain.WorkflowDefinition;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class WorkflowExecutionService {

    private final WorkflowExecutionEngine engine;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public WorkflowExecutionService(WorkflowExecutionEngine engine) {
        this.engine = engine;
    }

    public WorkflowExecutionReport executePreview(
            WorkflowDefinition workflow
    ) throws InterruptedException {

        if (!running.compareAndSet(false, true)) {
            throw new WorkflowBusyException();
        }

        try {
            return engine.execute(workflow);
        } finally {
            running.set(false);
        }
    }
}