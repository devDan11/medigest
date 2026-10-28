package com.medigestsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MediGestApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediGestApplication.class, args);
    }
}
