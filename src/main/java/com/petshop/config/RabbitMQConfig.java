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

/**
 * RabbitMQ 核心配置类
 * 主要负责声明交换机（Exchange）、队列（Queue）以及它们之间的绑定关系（Binding）。
 * 
 * 本项目中使用 RabbitMQ 实现了两大核心业务场景：
 * 1. 用户行为埋点异步收集（供推荐系统使用）
 * 2. 订单超时未支付自动取消（利用死信队列 DLX + TTL 实现延迟队列功能）
 */
@Configuration
public class RabbitMQConfig {

    // =====================================================================
    // 1. 用户行为埋点相关常量（供推荐系统使用）
    // =====================================================================
    public static final String RECOMMEND_EXCHANGE = "recommend.exchange";
    public static final String BEHAVIOR_QUEUE = "user.behavior.queue";
    public static final String BEHAVIOR_ROUTING_KEY = "behavior.tag";

    // =====================================================================
    // 2. 订单超时取消相关常量（死信队列模式）
    // =====================================================================
    public static final String ORDER_DELAY_EXCHANGE = "order.delay.exchange";
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    public static final String ORDER_DELAY_ROUTING_KEY = "order.delay";
    
    public static final String ORDER_CANCEL_EXCHANGE = "order.cancel.exchange";
    public static final String ORDER_CANCEL_QUEUE = "order.cancel.queue";
    public static final String ORDER_CANCEL_ROUTING_KEY = "order.cancel";

    /** 
     * 从配置文件 application.yml 中读取订单超时时间（分钟），默认 30 分钟。
     * 注意：RabbitMQ 不允许修改已存在队列的参数。如果修改了该时间，必须先在 RabbitMQ 控制台删除旧的 order.delay.queue 队列。
     */
    @Value("${order.pay-timeout-minutes:30}")
    private int payTimeoutMinutes;

    /**
     * 消息转换器：将发往 MQ 的 Java 对象自动序列化为 JSON 格式，方便跨语言和可视化查看。
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 自定义 RabbitTemplate，为其设置 JSON 消息转换器。
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }

    // =====================================================================
    // 【场景一】推荐系统：用户行为消息队列拓扑声明
    // =====================================================================

    /**
     * 推荐业务交换机 (直连模式)
     */
    @Bean
    public DirectExchange recommendExchange() {
        return new DirectExchange(RECOMMEND_EXCHANGE);
    }

    /**
     * 存储用户行为的队列
     */
    @Bean
    public Queue behaviorQueue() {
        return new Queue(BEHAVIOR_QUEUE, true);
    }

    /**
     * 将行为队列绑定到推荐交换机，并指定路由键
     */
    @Bean
    public Binding behaviorBinding(Queue behaviorQueue, DirectExchange recommendExchange) {
        return BindingBuilder.bind(behaviorQueue).to(recommendExchange).with(BEHAVIOR_ROUTING_KEY);
    }

    // =====================================================================
    // 【场景二】订单超时取消：延迟队列拓扑声明 (TTL + DLX)
    // 核心原理：
    // 1. 下单后，将订单号发送到“延迟队列”(orderDelayQueue)。
    // 2. 延迟队列没有消费者！消息会在里面一直存活，直到超过设定的存活时间（TTL，即超时时间）。
    // 3. 消息过期“死亡”后，变成“死信”，被 RabbitMQ 自动转发到绑定的“死信交换机”(orderCancelExchange)。
    // 4. 死信交换机将消息路由到“取消队列”(orderCancelQueue)。
    // 5. 订单服务监听“取消队列”，收到消息后去数据库检查该订单，如果还没付款，就执行取消操作。
    // =====================================================================

    /**
     * 延迟交换机（接收刚刚下好的订单）
     */
    @Bean
    public DirectExchange orderDelayExchange() {
        return new DirectExchange(ORDER_DELAY_EXCHANGE);
    }

    /**
     * 延迟队列：没有消费者。核心配置在于设置 TTL 和死信去向。
     */
    @Bean
    public Queue orderDelayQueue() {
        Map<String, Object> args = new HashMap<>();
        // 1. 设置队列里消息的存活时间 (Time To Live)，单位：毫秒
        args.put("x-message-ttl", payTimeoutMinutes * 60_000L);
        // 2. 消息死掉后，转交给大家（死信交换机 Dead Letter Exchange）
        args.put("x-dead-letter-exchange", ORDER_CANCEL_EXCHANGE);
        // 3. 消息死掉后，携带的新的路由键
        args.put("x-dead-letter-routing-key", ORDER_CANCEL_ROUTING_KEY);
        
        return new Queue(ORDER_DELAY_QUEUE, true, false, false, args);
    }

    /**
     * 将延迟队列绑定到延迟交换机
     */
    @Bean
    public Binding orderDelayBinding(Queue orderDelayQueue, DirectExchange orderDelayExchange) {
        return BindingBuilder.bind(orderDelayQueue).to(orderDelayExchange).with(ORDER_DELAY_ROUTING_KEY);
    }

    /**
     * 死信交换机（实际处理取消逻辑的交换机）
     */
    @Bean
    public DirectExchange orderCancelExchange() {
        return new DirectExchange(ORDER_CANCEL_EXCHANGE);
    }

    /**
     * 取消队列：监听此队列即可收到过期（超时）的订单消息
     */
    @Bean
    public Queue orderCancelQueue() {
        return new Queue(ORDER_CANCEL_QUEUE, true);
    }

    /**
     * 将取消队列绑定到死信交换机
     */
    @Bean
    public Binding orderCancelBinding(Queue orderCancelQueue, DirectExchange orderCancelExchange) {
        return BindingBuilder.bind(orderCancelQueue).to(orderCancelExchange).with(ORDER_CANCEL_ROUTING_KEY);
    }
}
