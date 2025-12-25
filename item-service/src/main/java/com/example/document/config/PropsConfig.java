package com.example.document.config;

import com.example.document.dto.MinioProps;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinioProps.class)
public class PropsConfig {
}
