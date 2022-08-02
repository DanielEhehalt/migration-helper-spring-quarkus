package com.example.restservice.impl;

import com.example.restservice.Greeting;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties
public class ReflUsageFqn {

    private void test() {
        Greeting greeting = new Greeting(1, "Hi");
        java.lang.reflect.Field[] fields = greeting.getClass().getDeclaredFields();
    }
}
