package com.example.redisdemo.listener;

import com.example.redisdemo.config.RabbitConfig;
import com.example.redisdemo.event.ProductEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ProductEventListener {

    private static final Logger log = LoggerFactory.getLogger(ProductEventListener.class);

    @RabbitListener(queues = RabbitConfig.PRODUCT_QUEUE)
    public void handleProductEvent(ProductEvent event) {
        log.info("[RabbitMQ Consumer] Received event from queue '{}': {}", RabbitConfig.PRODUCT_QUEUE, event);
    }
}
