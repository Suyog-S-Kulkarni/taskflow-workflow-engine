package com.suyog.taskflow.config;

import com.suyog.taskflow.workflow.domain.WorkflowValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class WorkflowConfiguration {

    @Bean
    public WorkflowValidator workflowValidator() {
        return new WorkflowValidator();
    }
}