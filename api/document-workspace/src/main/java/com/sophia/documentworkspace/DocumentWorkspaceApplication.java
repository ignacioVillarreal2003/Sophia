package com.sophia.documentworkspace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DocumentWorkspaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentWorkspaceApplication.class, args);
    }
}
