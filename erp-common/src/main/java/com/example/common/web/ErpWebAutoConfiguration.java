package com.example.common.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ErpWebAutoConfiguration {

    @Bean
    ApiExceptionHandler apiExceptionHandler() {
        return new ApiExceptionHandler();
    }
}
