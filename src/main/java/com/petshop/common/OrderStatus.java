package com.petshop.common;

import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态枚举 & 状态机合法流转定义。
 * <p>
 * 状态流转图：
 * <pre>
 *  0(待支付) ──支付──> 1(待发货) ──发货──> 2(待收货)
 *       │                  │                    │
 *     取消(-1)           取消(-1)             收货
 *                                               ▼
 *                                  3(待评价) ──评价──> 4(已完成)
 *                                       │
 *  退单流程：状态2/3 ──申请退单(-2)──> 管理员审核
 *              ├─ 通过 -> -3(已退款)
 *              ├─ 不通过 -> 恢复原状态(2或3)
 *              └─ 管理员直接退单 -> -4
 * </pre>
 *
 * 使用方式：
 * <pre>
 *     OrderStatus.checkTransition(fromStatus, toStatus);  // 不合法会抛 BusinessException
 * </pre>
 */
@Getter
public enum OrderStatus {

    PENDING_PAYMENT(0, "待支付"),
    PENDING_SHIPMENT(1, "待发货"),
    PENDING_RECEIVE(2, "待收货"),
    PENDING_REVIEW(3, "待评价"),
    COMPLETED(4, "已完成"),
    CANCELLED(-1, "已取消"),
    REFUND_APPLYING(-2, "退款申请中"),
    REFUNDED(-3, "已退款"),
    ADMIN_REFUNDED(-4, "管理员退款");

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // ==================== 合法流转表 ====================
    /** key=原状态, value=允许的目标状态集合 */
    private static final Map<Integer, Set<Integer>> TRANSITIONS;

    static {
        Map<Integer, Set<Integer>> m = new HashMap<>();
        // 0(待支付) -> 1(待发货) | -1(已取消)
        m.put(0, new HashSet<>(Arrays.asList(1, -1)));
        // 1(待发货) -> 2(待收货) | -1(已取消)
        m.put(1, new HashSet<>(Arrays.asList(2, -1)));
        // 2(待收货) -> 3(待评价) | -2(退款申请中)
        m.put(2, new HashSet<>(Arrays.asList(3, -2)));
        // 3(待评价) -> 4(已完成) | -2(退款申请中) | -4(管理员退款)
        m.put(3, new HashSet<>(Arrays.asList(4, -2, -4)));
        // -2(退款申请中) -> -3(退款通过) | 2(驳回恢复待收货) | 3(驳回恢复待评价)
        m.put(-2, new HashSet<>(Arrays.asList(-3, 2, 3)));
        // 终态不可流转
        m.put(4, Collections.emptySet());
        m.put(-1, Collections.emptySet());
        m.put(-3, Collections.emptySet());
        m.put(-4, Collections.emptySet());
        TRANSITIONS = Collections.unmodifiableMap(m);
    }

    /**
     * 判断状态流转是否合法。
     */
    public static boolean isValidTransition(int from, int to) {
        Set<Integer> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * 检查状态流转，不合法则抛出 BusinessException。
     */
    public static void checkTransition(int from, int to) {
        if (!isValidTransition(from, to)) {
            OrderStatus fromStatus = of(from);
            OrderStatus toStatus = of(to);
            String fromDesc = fromStatus != null ? fromStatus.getDesc() : String.valueOf(from);
            String toDesc = toStatus != null ? toStatus.getDesc() : String.valueOf(to);
            throw new BusinessException("非法状态流转：" + fromDesc + " -> " + toDesc);
        }
    }

    /**
     * 根据 code 获取枚举，找不到返回 null。
     */
    public static OrderStatus of(int code) {
        for (OrderStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
