package com.petshop.security;

import com.petshop.api.client.ShopClient;
import com.petshop.common.BusinessException;
import com.petshop.common.ResultCode;
import com.petshop.order.entity.Order;
import com.petshop.order.mapper.OrderMapper;
import com.petshop.product.entity.Product;
import com.petshop.product.mapper.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商家(MERCHANT)数据归属校验工具。
 *
 * <p>背景：{@link RequireRole @RequireRole} 只校验“角色是否命中”，不校验“这家店/这件商品是不是你的”。
 * 因此凡 MERCHANT 能操作的写接口（改店、改商品、发货、退单审核、回复评价等），Service 必须先调用
 * 本工具做归属校验，否则商家可越权操作他人店铺资源。
 *
 * <p>微服务化改造：店铺归属数据由 shop 服务独家维护，这里通过 {@link ShopClient}（OpenFeign）
 * 远程查询店主信息；商品/订单仍走共享库本地 Mapper（与调用方同库同事务）。
 *
 * <p>约定：
 * <ul>
 *   <li>ADMIN 直接放行（管全站，不受 owner_id 限制）；</li>
 *   <li>MERCHANT 校验目标资源最终归属的 {@code shop.owner_id == 当前登录用户}，否则抛 403；</li>
 *   <li>其它角色（USER 等）一律 403——这些接口本就不该让 USER 进来，这里做双保险。</li>
 * </ul>
 *
 * <p>列表/统计查询用 {@link #myShopIds()}：MERCHANT 取本人名下 shopId 集合，注入
 * {@code WHERE shop_id IN (...)}；ADMIN 返回 {@code null} 表示不加店铺过滤。
 */
@Component
public class OwnershipChecker {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_MERCHANT = "MERCHANT";

    @Autowired
    private ShopClient shopClient;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private OrderMapper orderMapper;

    /** 当前登录人是否管理员。 */
    public boolean isAdmin() {
        return ROLE_ADMIN.equals(UserContext.getRole());
    }

    /** 是否有登录用户（通过 JWT 拦截器已设置 UserContext）。 */
    private boolean isAuthenticated() {
        return UserContext.getRole() != null;
    }

    /**
     * 校验”某店铺”归当前商家所有。ADMIN 放行；店铺不存在抛 404；非本店抛 403。
     * 未登录用户（公开接口）调用此方法表示不校验归属，直接放行。
     */
    public void assertShopOwned(Long shopId) {
        if (!isAuthenticated() || isAdmin()) {
            return;
        }
        requireMerchant();
        Long ownerId = shopClient.getShopOwnerId(shopId);
        if (ownerId == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (!currentUserId().equals(ownerId)) {
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
     *   <li>未登录 / ADMIN / USER 等非商家角色返回 {@code null}——调用方据此不加店铺过滤（看全站）；</li>
     *   <li>MERCHANT 返回其名下 shopId 列表（可能为空集，远程查询经 Feign 走 shop 服务）。</li>
     * </ul>
     */
    public List<Long> myShopIds() {
        if (!isAuthenticated() || isAdmin()) {
            return null;
        }
        if (!ROLE_MERCHANT.equals(UserContext.getRole())) {
            // USER 等非商家角色：公开浏览，不过滤
            return null;
        }
        return shopClient.getShopIdsByOwner(currentUserId());
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
