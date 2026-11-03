package com.triobank;

import org.springframework.boot.SpringApplication;

public class TestBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(TrioBankApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
