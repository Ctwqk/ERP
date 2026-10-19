package com.example.item;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.example.item", "com.example.document", "com.example.order"})
@EnableJpaRepositories(basePackages = {"com.example.item", "com.example.document", "com.example.order"})
@EntityScan(basePackages = {"com.example.item", "com.example.document", "com.example.order"})
@ConfigurationPropertiesScan("com.example.document")
public class ItemServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItemServiceApplication.class, args);
    }
}
