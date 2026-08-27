package com.alten.chat_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson configuration for chat-service.
 * Registers the JavaTimeModule to handle LocalDateTime serialization.
 */
@Configuration
public class JacksonConfig {

    /**
     * Provides a configured ObjectMapper bean.
     * Handles Java 8 date/time types (LocalDateTime) used in DTOs and entities.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}