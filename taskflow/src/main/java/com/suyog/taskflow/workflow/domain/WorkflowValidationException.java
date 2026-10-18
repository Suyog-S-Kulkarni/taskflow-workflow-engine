package com.suyog.taskflow.workflow.domain;

public class WorkflowValidationException extends RuntimeException {

    public WorkflowValidationException(String message) {
        super(message);
    }
}