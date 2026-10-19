package com.example.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Adds a title and the bearer-JWT security scheme so Swagger UI's "Authorize" button works out of the box. */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
public class ErpOpenApiConfiguration {

    static final String SCHEME = "bearer-jwt";

    @Bean
    @ConditionalOnMissingBean
    OpenAPI erpOpenApi(@Value("${spring.application.name:erp-service}") String name,
            @Value("${erp.openapi.description:ERP microservice}") String description) {
        return new OpenAPI()
                .info(new Info().title(name).version("v1").description(description
                        + ". Authenticate with POST /auth/login on user-service and paste the access token "
                        + "into Authorize."))
                .components(new Components().addSecuritySchemes(SCHEME,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME));
    }
}
