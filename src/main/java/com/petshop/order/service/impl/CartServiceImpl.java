package com.petshop.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.petshop.common.BusinessException;
import com.petshop.common.ResultCode;
import com.petshop.order.entity.CartItem;
import com.petshop.order.mapper.CartItemMapper;
import com.petshop.order.service.CartService;
import com.petshop.product.entity.Product;
import com.petshop.product.entity.ProductSku;
import com.petshop.product.mapper.ProductMapper;
import com.petshop.product.mapper.ProductSkuMapper;
import com.petshop.security.UserContext;
import com.petshop.shop.entity.Shop;
import com.petshop.shop.mapper.ShopMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 购物车 Service 实现。
 * <p>
 * 继承 ServiceImpl&lt;CartItemMapper, CartItem&gt; 获得 CRUD 能力。
 * "物理删除"——cart_item 表无逻辑删除字段（继承 BaseEntityLite），
 * 配合 uk_user_product_sku 唯一键防止重复加车。
 */
@Service
public class CartServiceImpl extends ServiceImpl<CartItemMapper, CartItem> implements CartService {

    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private ProductSkuMapper productSkuMapper;
    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private com.petshop.user.service.MembershipLevelService membershipLevelService;

    @Override
    public void addToCart(CartItem cartItem) {
        Long userId = requireUserId();
        Long productId = cartItem.getProductId();
        Long skuId = cartItem.getSkuId() == null ? 0L : cartItem.getSkuId();
        int qty = cartItem.getQuantity() == null || cartItem.getQuantity() <= 0
                ? 1 : cartItem.getQuantity();

        // 1) 校验商品是否存在且上架
        Product product = productMapper.selectById(productId);
        if (product == null || product.getStatus() == null || product.getStatus() != 1) {
            throw new BusinessException("商品不存在或已下架");
        }

        // 2) 如果有规格，校验规格是否存在
        if (skuId != 0) {
            ProductSku sku = productSkuMapper.selectById(skuId);
            if (sku == null || !sku.getProductId().equals(productId)) {
                throw new BusinessException("商品规格不存在");
            }
        }

        // 3) 唯一键 uk_user_product_sku 判重：已存在就递增数量
        LambdaQueryWrapper<CartItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CartItem::getUserId, userId)
               .eq(CartItem::getProductId, productId)
               .eq(CartItem::getSkuId, skuId);
        CartItem exist = this.getOne(wrapper);
        if (exist != null) {
            exist.setQuantity(exist.getQuantity() + qty);
            this.updateById(exist);
            return;
        }

        // 4) 新增
        cartItem.setId(null);
        cartItem.setUserId(userId);
        cartItem.setSkuId(skuId);
        cartItem.setQuantity(qty);
        cartItem.setSelected(0); // 默认不勾选
        this.save(cartItem);
    }

    @Override
    public void updateQuantity(Long cartId, Integer quantity) {
        CartItem item = assertMyCart(cartId);
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "数量必须大于0");
        }
        item.setQuantity(quantity);
        this.updateById(item);
    }

    @Override
    public void removeFromCart(Long cartId) {
        CartItem item = assertMyCart(cartId);
        // 物理删除
        this.removeById(item.getId());
    }

    @Override
    public void toggleSelect(Long cartId, Integer selected) {
        CartItem item = assertMyCart(cartId);
        item.setSelected(selected != null && selected == 1 ? 1 : 0);
        this.updateById(item);
    }

    @Override
    public List<Map<String, Object>> getCartList() {
        Long userId = UserContext.getUserId();
        List<CartItem> items = baseMapper.selectList(
                new QueryWrapper<CartItem>().eq("user_id", userId).orderByDesc("create_time"));

        java.math.BigDecimal discount = membershipLevelService.getCurrentUserDiscount();

        List<Map<String, Object>> result = new ArrayList<>();
        for (CartItem item : items) {
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("id", item.getId());
            vo.put("userId", item.getUserId());
            vo.put("productId", item.getProductId());
            vo.put("skuId", item.getSkuId());
            vo.put("quantity", item.getQuantity());
            vo.put("selected", item.getSelected());
            vo.put("createTime", item.getCreateTime());

            // 聚合商品信息
            Product product = productMapper.selectById(item.getProductId());
            if (product != null) {
                vo.put("productName", product.getName());
                vo.put("productImage", product.getMainImage());
                vo.put("productStatus", product.getStatus());
                vo.put("shopId", product.getShopId());
                // 查店铺名称
                Shop shop = shopMapper.selectById(product.getShopId());
                vo.put("shopName", shop != null ? shop.getName() : "");
            }

            // 规格信息 + 实时价格与库存
            int valid = 1; // 默认有效
            if (item.getSkuId() != null && item.getSkuId() != 0) {
                ProductSku sku = productSkuMapper.selectById(item.getSkuId());
                if (sku != null) {
                    vo.put("specName", sku.getSpecName());
                    vo.put("price", sku.getPrice() != null ? sku.getPrice().multiply(discount) : null);
                    vo.put("stock", sku.getStock());
                    vo.put("skuDeleted", sku.getDeleted());
                    if (sku.getDeleted() != null && sku.getDeleted() == 1) valid = 0;
                } else {
                    valid = 0;
                    vo.put("skuDeleted", 1);
                }
            } else {
                if (product != null) {
                    vo.put("specName", "");
                    vo.put("price", product.getPrice() != null ? product.getPrice().multiply(discount) : null);
                    vo.put("stock", product.getStock());
                }
            }

            // 商品下架则失效
            if (product == null || product.getStatus() == null || product.getStatus() != 1) {
                valid = 0;
            }

            vo.put("valid", valid);
            result.add(vo);
        }
        return result;
    }

    // ========== 内部方法 ==========

    /** 获取当前用户 id，未登录抛 401 */
    private Long requireUserId() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return uid;
    }

    /** 校验购物车项属于当前用户，不存在则抛 404 */
    private CartItem assertMyCart(Long cartId) {
        Long userId = requireUserId();
        CartItem item = this.getById(cartId);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (!userId.equals(item.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return item;
    }
}
