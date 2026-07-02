package com.petshop.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String RECOMMEND_EXCHANGE = "recommend.exchange";
    public static final String BEHAVIOR_QUEUE = "user.behavior.queue";
    public static final String BEHAVIOR_ROUTING_KEY = "behavior.tag";

    // ===== 订单超时取消：延迟队列(TTL) → 死信 → 取消队列 =====
    public static final String ORDER_DELAY_EXCHANGE = "order.delay.exchange";
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    public static final String ORDER_DELAY_ROUTING_KEY = "order.delay";
    public static final String ORDER_CANCEL_EXCHANGE = "order.cancel.exchange";
    public static final String ORDER_CANCEL_QUEUE = "order.cancel.queue";
    public static final String ORDER_CANCEL_ROUTING_KEY = "order.cancel";

    /** 与超时阈值保持一致：延迟队列 TTL = 该分钟数。改动后需删除旧队列再重建（RabbitMQ 不允许改已存在队列的参数）。 */
    @Value("${order.pay-timeout-minutes:30}")
    private int payTimeoutMinutes;

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

    // ===== 订单超时取消拓扑 =====

    @Bean
    public DirectExchange orderDelayExchange() {
        return new DirectExchange(ORDER_DELAY_EXCHANGE);
    }

    /** 延迟队列：无消费者，消息到 TTL 后死信转发到取消交换机 */
    @Bean
    public Queue orderDelayQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", payTimeoutMinutes * 60_000L);
        args.put("x-dead-letter-exchange", ORDER_CANCEL_EXCHANGE);
        args.put("x-dead-letter-routing-key", ORDER_CANCEL_ROUTING_KEY);
        return new Queue(ORDER_DELAY_QUEUE, true, false, false, args);
    }

    @Bean
    public Binding orderDelayBinding(Queue orderDelayQueue, DirectExchange orderDelayExchange) {
        return BindingBuilder.bind(orderDelayQueue).to(orderDelayExchange).with(ORDER_DELAY_ROUTING_KEY);
    }

    @Bean
    public DirectExchange orderCancelExchange() {
        return new DirectExchange(ORDER_CANCEL_EXCHANGE);
    }

    /** 取消队列：消费死信，触发"仍待支付则取消" */
    @Bean
    public Queue orderCancelQueue() {
        return new Queue(ORDER_CANCEL_QUEUE, true);
    }

    @Bean
    public Binding orderCancelBinding(Queue orderCancelQueue, DirectExchange orderCancelExchange) {
        return BindingBuilder.bind(orderCancelQueue).to(orderCancelExchange).with(ORDER_CANCEL_ROUTING_KEY);
    }
}
