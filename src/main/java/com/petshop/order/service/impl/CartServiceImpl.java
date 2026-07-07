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

import java.math.BigDecimal;
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
        int quantity = cartItem.getQuantity() == null || cartItem.getQuantity() <= 0
                ? 1 : cartItem.getQuantity();

        validateProductAndSku(productId, skuId);

        LambdaQueryWrapper<CartItem> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CartItem::getUserId, userId)
                    .eq(CartItem::getProductId, productId)
                    .eq(CartItem::getSkuId, skuId);

        CartItem existingCartItem = this.getOne(queryWrapper);
        if (existingCartItem != null) {
            existingCartItem.setQuantity(existingCartItem.getQuantity() + quantity);
            this.updateById(existingCartItem);
            return;
        }

        cartItem.setId(null);
        cartItem.setUserId(userId);
        cartItem.setSkuId(skuId);
        cartItem.setQuantity(quantity);
        cartItem.setSelected(0);
        this.save(cartItem);
    }

    private void validateProductAndSku(Long productId, Long skuId) {
        Product product = productMapper.selectById(productId);
        if (product == null || product.getStatus() == null || product.getStatus() != 1) {
            throw new BusinessException("商品不存在或已下架");
        }
        if (skuId != 0) {
            ProductSku sku = productSkuMapper.selectById(skuId);
            if (sku == null || !sku.getProductId().equals(productId)) {
                throw new BusinessException("商品规格不存在");
            }
        }
    }

    @Override
    public void updateQuantity(Long cartId, Integer quantity) {
        CartItem cartItem = getAndValidateMyCartItem(cartId);
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "商品数量必须大于0");
        }
        cartItem.setQuantity(quantity);
        this.updateById(cartItem);
    }

    @Override
    public void removeFromCart(Long cartId) {
        CartItem cartItem = getAndValidateMyCartItem(cartId);
        // 直接从数据库中物理删除该购物车条目
        this.removeById(cartItem.getId());
    }

    @Override
    public void toggleSelect(Long cartId, Integer selected) {
        CartItem cartItem = getAndValidateMyCartItem(cartId);
        cartItem.setSelected(selected != null && selected == 1 ? 1 : 0);
        this.updateById(cartItem);
    }

    @Override
    public List<Map<String, Object>> getCartList() {
        Long userId = UserContext.getUserId();
        // 按创建时间倒序查询出该用户购物车内的所有条目
        List<CartItem> userCartItems = baseMapper.selectList(
                new QueryWrapper<CartItem>().eq("user_id", userId).orderByDesc("create_time"));

        // 获取当前登录用户的会员折扣率，以便在购物车中直接展示会员优惠价
        BigDecimal membershipDiscountRate = membershipLevelService.getCurrentUserDiscount();

        List<Map<String, Object>> resultList = new ArrayList<>();
        for (CartItem item : userCartItems) {
            resultList.add(buildCartItemDetail(item, membershipDiscountRate));
        }
        return resultList;
    }

    /** 构建单个购物车条目的详情 Map（含商品/规格/价格/有效性） */
    private Map<String, Object> buildCartItemDetail(CartItem item, BigDecimal membershipDiscountRate) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", item.getId());
        detail.put("userId", item.getUserId());
        detail.put("productId", item.getProductId());
        detail.put("skuId", item.getSkuId());
        detail.put("quantity", item.getQuantity());
        detail.put("selected", item.getSelected());
        detail.put("createTime", item.getCreateTime());

        // 聚合关联的商品基础信息
        Product product = productMapper.selectById(item.getProductId());
        fillProductInfo(detail, product);

        // 获取具体的规格信息，并关联实时的价格与库存数据
        int isValid = fillSkuAndPriceInfo(detail, item, product, membershipDiscountRate);

        // 如果主商品已经被删除或手动下架，同样标记为失效
        if (product == null || product.getStatus() == null || product.getStatus() != 1) {
            isValid = 0;
        }
        detail.put("valid", isValid);
        return detail;
    }

    /** 填充商品基础信息（名称/主图/状态/店铺名） */
    private void fillProductInfo(Map<String, Object> detail, Product product) {
        if (product == null) return;
        detail.put("productName", product.getName());
        detail.put("productImage", product.getMainImage());
        detail.put("productStatus", product.getStatus());
        detail.put("shopId", product.getShopId());
        // 根据商品的 shopId 查询关联的店铺名称，方便前端在购物车按店铺分组显示
        Shop shop = shopMapper.selectById(product.getShopId());
        detail.put("shopName", shop != null ? shop.getName() : "");
    }

    /**
     * 填充 SKU/价格/库存信息，返回有效性标志（1=可购买，0=已失效）
     */
    private int fillSkuAndPriceInfo(Map<String, Object> detail, CartItem item,
                                    Product product, BigDecimal membershipDiscountRate) {
        if (item.getSkuId() != null && item.getSkuId() != 0) {
            return fillWithSkuPrice(detail, item.getSkuId(), membershipDiscountRate);
        }
        // 如果商品没有多规格属性，则直接使用主商品的价格和库存
        if (product != null) {
            detail.put("specName", "");
            detail.put("price", product.getPrice());
            detail.put("memberPrice", product.getPrice() != null
                    ? product.getPrice().multiply(membershipDiscountRate) : null);
            detail.put("stock", product.getStock());
        }
        return 1;
    }

    /** 根据 skuId 填充规格价格信息，返回有效性标志 */
    private int fillWithSkuPrice(Map<String, Object> detail, Long skuId, BigDecimal membershipDiscountRate) {
        ProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null) {
            detail.put("skuDeleted", 1);
            return 0;
        }
        detail.put("specName", sku.getSpecName());
        // price为划线原价（与订单和结算逻辑统一）
        detail.put("price", sku.getPrice());
        // memberPrice为会员折后价，等于 原价 * 会员折扣率
        detail.put("memberPrice", sku.getPrice() != null
                ? sku.getPrice().multiply(membershipDiscountRate) : null);
        detail.put("stock", sku.getStock());
        detail.put("skuDeleted", sku.getDeleted());
        // 如果规格已经被删除，则标记购物车内的该商品为失效状态
        return (sku.getDeleted() != null && sku.getDeleted() == 1) ? 0 : 1;
    }

    // ========== 内部方法 ==========

    /** 获取当前用户 id，未登录抛 401 */
    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    /** 安全校验：验证该购物车项属于当前登录用户，防止越权操作，不存在则抛 404 */
    private CartItem getAndValidateMyCartItem(Long cartId) {
        Long currentUserId = requireUserId();
        CartItem cartItem = this.getById(cartId);
        if (cartItem == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (!currentUserId.equals(cartItem.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return cartItem;
    }
}
