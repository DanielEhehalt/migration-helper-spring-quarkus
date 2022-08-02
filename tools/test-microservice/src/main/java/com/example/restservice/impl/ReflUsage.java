package com.example.restservice.impl;

import com.example.restservice.Greeting;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;

@Service
public class ReflUsage {

    public ReflUsage() {
        Greeting greeting = new Greeting(1, "Hi");
        Field[] fields = greeting.getClass().getDeclaredFields();
        JacksonAnnotationIntrospector annotationIntrospector;
    }
}
