package com.groovsta.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.groovsta")
public class GroovstaApplication {

    public static void main(String[] args) {
        SpringApplication.run(GroovstaApplication.class, args);
    }
}
