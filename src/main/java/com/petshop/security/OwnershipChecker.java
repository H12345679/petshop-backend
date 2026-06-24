package com.petshop.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petshop.common.BusinessException;
import com.petshop.common.ResultCode;
import com.petshop.order.entity.Order;
import com.petshop.order.mapper.OrderMapper;
import com.petshop.shop.entity.Product;
import com.petshop.shop.entity.Shop;
import com.petshop.shop.mapper.ProductMapper;
import com.petshop.shop.mapper.ShopMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 商家(MERCHANT)数据归属校验工具。
 *
 * <p>背景：{@link RequireRole @RequireRole} 只校验“角色是否命中”，不校验“这家店/这件商品是不是你的”。
 * 因此凡 MERCHANT 能操作的写接口（改店、改商品、发货、退单审核、回复评价等），Service 必须先调用
 * 本工具做归属校验，否则商家可越权操作他人店铺资源。
 *
 * <p>约定：
 * <ul>
 *   <li>ADMIN 直接放行（管全站，不受 owner_id 限制）；</li>
 *   <li>MERCHANT 校验目标资源最终归属的 {@code shop.owner_id == 当前登录用户}，否则抛 403；</li>
 *   <li>其它角色（USER 等）一律 403——这些接口本就不该让 USER 进来，这里做双保险。</li>
 * </ul>
 *
 * <p>写接口示例：
 * <pre>{@code
 *   // ProductServiceImpl#update
 *   ownershipChecker.assertProductOwned(productId);   // 不是本店商品直接 403
 *   // ... 继续更新
 * }</pre>
 *
 * <p>列表/统计查询用 {@link #myShopIds()}：MERCHANT 取本人名下 shopId 集合，注入
 * {@code WHERE shop_id IN (...)}；ADMIN 返回 {@code null} 表示不加店铺过滤。
 * <pre>{@code
 *   List<Long> shopIds = ownershipChecker.myShopIds();
 *   wrapper.in(shopIds != null, Order::getShopId, shopIds); // ADMIN(null)时该条件不生效
 * }</pre>
 */
@Component
public class OwnershipChecker {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_MERCHANT = "MERCHANT";

    @Autowired
    private ShopMapper shopMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private OrderMapper orderMapper;

    /** 当前登录人是否管理员。 */
    public boolean isAdmin() {
        return ROLE_ADMIN.equals(UserContext.getRole());
    }

    /**
     * 校验“某店铺”归当前商家所有。ADMIN 放行；店铺不存在抛 404；非本店抛 403。
     */
    public void assertShopOwned(Long shopId) {
        if (isAdmin()) {
            return;
        }
        requireMerchant();
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (!currentUserId().equals(shop.getOwnerId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    /**
     * 校验“某商品”所属店铺归当前商家。ADMIN 放行。
     */
    public void assertProductOwned(Long productId) {
        if (isAdmin()) {
            return;
        }
        requireMerchant();
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        assertShopOwned(product.getShopId());
    }

    /**
     * 校验“某订单”所属店铺归当前商家。ADMIN 放行。
     * 退单审核 / 发货等接口可由 orderId 推导出店铺后调用本方法。
     */
    public void assertOrderOwned(Long orderId) {
        if (isAdmin()) {
            return;
        }
        requireMerchant();
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        assertShopOwned(order.getShopId());
    }

    /**
     * 列表 / 统计查询用：当前商家名下所有 shopId。
     * <ul>
     *   <li>ADMIN 返回 {@code null}——调用方据此不加店铺过滤（看全站）；</li>
     *   <li>MERCHANT 返回其名下 shopId 列表（可能为空集，空集应使查询返回空结果）。</li>
     * </ul>
     */
    public List<Long> myShopIds() {
        if (isAdmin()) {
            return null;
        }
        requireMerchant();
        List<Shop> shops = shopMapper.selectList(
                new LambdaQueryWrapper<Shop>().eq(Shop::getOwnerId, currentUserId()));
        return shops.stream().map(Shop::getId).collect(Collectors.toList());
    }

    // ---------------- 内部 ----------------

    private Long currentUserId() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return uid;
    }

    private void requireMerchant() {
        if (!ROLE_MERCHANT.equals(UserContext.getRole())) {
            // 非 ADMIN 非 MERCHANT（如 USER）走到归属校验，本就是越权
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }
}
