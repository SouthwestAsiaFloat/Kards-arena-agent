package com.southwestasiafloat.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableRabbit
@Configuration
public class OcrRabbitConfig {

    @Bean
    public DirectExchange ocrExchange(ArenaOcrAsyncProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    @Bean
    public Queue ocrRequestQueue(ArenaOcrAsyncProperties properties) {
        return new Queue(properties.getRequestQueue(), true);
    }

    @Bean
    public Queue ocrResultQueue(ArenaOcrAsyncProperties properties) {
        return new Queue(properties.getResultQueue(), true);
    }

    @Bean
    public Binding ocrRequestBinding(Queue ocrRequestQueue,
                                     DirectExchange ocrExchange,
                                     ArenaOcrAsyncProperties properties) {
        return BindingBuilder.bind(ocrRequestQueue)
                .to(ocrExchange)
                .with(properties.getRequestRoutingKey());
    }

    @Bean
    public Binding ocrResultBinding(Queue ocrResultQueue,
                                    DirectExchange ocrExchange,
                                    ArenaOcrAsyncProperties properties) {
        return BindingBuilder.bind(ocrResultQueue)
                .to(ocrExchange)
                .with(properties.getResultRoutingKey());
    }

    @Bean
    public Jackson2JsonMessageConverter jacksonJsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
