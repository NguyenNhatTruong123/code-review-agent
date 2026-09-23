package com.codereviewagent.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot entry point for the review API. */
@SpringBootApplication
public class CodeReviewApiApplication {

    /** Starts the Spring Boot application with the supplied process arguments.
     * @param args process arguments forwarded to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(CodeReviewApiApplication.class, args);
    }
}
