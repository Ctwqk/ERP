package com.example.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * JSON message conversion for RabbitMQ. Consumers infer the payload type from the listener's parameter instead of
 * trusting the producer's {@code __TypeId__} header, so services stay decoupled from each other's class names.
 */
@AutoConfiguration
@ConditionalOnClass(Jackson2JsonMessageConverter.class)
public class ErpMessagingConfiguration {

    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    MessageConverter erpJsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        typeMapper.setTrustedPackages("com.example.common.events");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
