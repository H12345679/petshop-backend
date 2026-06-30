package com.petshop.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String RECOMMEND_EXCHANGE = "recommend.exchange";
    public static final String BEHAVIOR_QUEUE = "user.behavior.queue";
    public static final String BEHAVIOR_ROUTING_KEY = "behavior.tag";

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }

    @Bean
    public DirectExchange recommendExchange() {
        return new DirectExchange(RECOMMEND_EXCHANGE);
    }

    @Bean
    public Queue behaviorQueue() {
        return new Queue(BEHAVIOR_QUEUE, true);
    }

    @Bean
    public Binding behaviorBinding(Queue behaviorQueue, DirectExchange recommendExchange) {
        return BindingBuilder.bind(behaviorQueue).to(recommendExchange).with(BEHAVIOR_ROUTING_KEY);
    }
}
