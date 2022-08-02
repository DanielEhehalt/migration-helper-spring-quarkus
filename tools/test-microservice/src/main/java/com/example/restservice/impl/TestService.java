package com.example.restservice.impl;

import com.thoughtworks.qdox.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.dsl.IntegrationFlowAdapter;
import org.springframework.integration.dsl.IntegrationFlowDefinition;
import org.springframework.stereotype.Service;
import org.springframework.integration.dsl.IntegrationFlow;

@Service
public class TestService {

    @Value("dad")
    String abc;

    public static void test() {
        JavaProjectBuilder builder = new JavaProjectBuilder();
        IntegrationFlow integrationFlow = new IntegrationFlowAdapter() {
            @Override
            protected IntegrationFlowDefinition<?> buildFlow() {
                return null;
            }
        };
    }


}
