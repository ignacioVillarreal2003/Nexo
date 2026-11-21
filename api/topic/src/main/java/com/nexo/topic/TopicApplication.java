package com.nexo.topic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TopicApplication {

    public static void main(String[] args) {
        SpringApplication.run(TopicApplication.class, args);
    }
}
