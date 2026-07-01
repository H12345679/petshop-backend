package com.petshop.order.schedule;

import com.petshop.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 未支付订单超时自动取消。
 * <p>
 * 默认每 60 秒扫描一次，把创建超过 {@code order.pay-timeout-minutes}(默认 30) 分钟仍待支付的订单
 * 自动置为已取消并回滚库存/优惠券，兑现下单页"超时订单自动取消并释放库存"的提示。
 * 可用配置覆盖：
 * <pre>
 * order:
 *   pay-timeout-minutes: 30       # 超时时长（分钟）
 *   timeout-scan-interval-ms: 60000  # 扫描间隔（毫秒）
 * </pre>
 */
@Slf4j
@Component
public class OrderTimeoutScheduler {

    @Autowired
    private OrderService orderService;

    @Value("${order.pay-timeout-minutes:30}")
    private int payTimeoutMinutes;

    @Scheduled(fixedDelayString = "${order.timeout-scan-interval-ms:60000}",
            initialDelayString = "${order.timeout-scan-initial-delay-ms:60000}")
    public void cancelTimeoutOrders() {
        try {
            int n = orderService.cancelTimeoutOrders(payTimeoutMinutes);
            if (n > 0) {
                log.info("超时未支付订单自动取消 {} 单（超时阈值 {} 分钟）", n, payTimeoutMinutes);
            }
        } catch (Exception e) {
            log.error("超时订单扫描取消失败", e);
        }
    }
}
