package com.codereviewagent.api.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GreetingController {

    @GetMapping("/greeting")
    public Map<String, String> greeting() {
        return Map.of("message", "Hello World!! I am an agent to review your code!");
    }
}
