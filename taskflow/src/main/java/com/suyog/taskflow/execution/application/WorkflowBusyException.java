package com.suyog.taskflow.execution.application;

public class WorkflowBusyException extends RuntimeException {

    public WorkflowBusyException() {
        super("Another workflow preview is currently running");
    }
}