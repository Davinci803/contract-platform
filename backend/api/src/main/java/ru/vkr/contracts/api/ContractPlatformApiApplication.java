package ru.vkr.contracts.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "ru.vkr.contracts")
@EnableAsync
@EnableScheduling
public class ContractPlatformApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContractPlatformApiApplication.class, args);
    }
}
