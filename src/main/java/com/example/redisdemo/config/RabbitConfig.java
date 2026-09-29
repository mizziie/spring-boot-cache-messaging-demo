package com.example.redisdemo.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String PRODUCT_QUEUE = "product.queue";
    public static final String PRODUCT_EXCHANGE = "product.exchange";
    public static final String PRODUCT_ROUTING_KEY = "product.event";

    @Bean
    public Queue productQueue() {
        return new Queue(PRODUCT_QUEUE, true);
    }

    @Bean
    public DirectExchange productExchange() {
        return new DirectExchange(PRODUCT_EXCHANGE);
    }

    @Bean
    public Binding productBinding(Queue productQueue, DirectExchange productExchange) {
        return BindingBuilder.bind(productQueue)
                .to(productExchange)
                .with(PRODUCT_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
