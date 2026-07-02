package com.petshop.order.mq;

import com.petshop.config.RabbitMQConfig;
import com.petshop.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 订单超时取消——延迟队列死信消费者（主触发）。
 * <p>
 * 下单时往 {@code order.delay.queue}(TTL=30min) 投一条订单号；到期后消息死信转发到
 * {@code order.cancel.queue}，本监听器收到即"仍待支付则取消"。定时扫描仅作兜底。
 * 消费失败(异常)会让消息重回队列重试，故取消逻辑本身用 CAS 幂等。
 */
@Slf4j
@Component
public class OrderCancelListener {

    @Autowired
    private OrderService orderService;

    @RabbitListener(queues = RabbitMQConfig.ORDER_CANCEL_QUEUE)
    public void handleTimeout(String orderIdStr) {
        if (orderIdStr == null || orderIdStr.isEmpty()) {
            return;
        }
        Long orderId;
        try {
            orderId = Long.valueOf(orderIdStr.trim());
        } catch (NumberFormatException e) {
            log.warn("订单超时取消消息订单号非法：{}", orderIdStr);
            return; // 脏消息直接丢弃，不重试
        }
        boolean cancelled = orderService.cancelOneIfUnpaid(orderId);
        if (cancelled) {
            log.info("订单 {} 超时未支付，已由延迟队列自动取消", orderId);
        }
    }
}
