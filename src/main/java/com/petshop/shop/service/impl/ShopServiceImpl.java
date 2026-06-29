package com.petshop.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.petshop.common.BusinessException;
import com.petshop.common.PageResult;
import com.petshop.common.ResultCode;
import com.petshop.security.OwnershipChecker;
import com.petshop.security.UserContext;
import com.petshop.shop.entity.Shop;
import com.petshop.shop.mapper.ShopMapper;
import com.petshop.shop.service.ShopPageQuery;
import com.petshop.shop.service.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 商店 Service 实现。
 * <p>
 * 继承 ServiceImpl&lt;ShopMapper, Shop&gt; → 自动拥有 save/getById/updateById/removeById/page 等。
 * 本类只补「业务规则」：建店绑店主、改删校验归属、列表组合条件。
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements ShopService {

    /** 商家数据归属校验工具（ADMIN 放行、MERCHANT 校验 owner_id）。 */
    @Autowired
    private OwnershipChecker ownershipChecker;

    @Override
    public void createShop(Shop shop) {
        // 1) 商家建店：owner_id 强制为当前登录人，忽略前端越权传入；ADMIN 可代为指定 ownerId
        if (!ownershipChecker.isAdmin()) {
            shop.setOwnerId(UserContext.getUserId());
        }
        // 2) 默认值兜底：不传状态默认营业
        if (shop.getStatus() == null) {
            shop.setStatus(1);
        }
        // 3) 主键由雪花算法生成，防止前端塞 id 干扰
        shop.setId(null);
        // 4) save() 是 ServiceImpl 白送的：自动 INSERT，并回填生成的 id 到 shop 对象
        this.save(shop);
    }

    @Override
    public void updateShop(Long id, Shop shop) {
        // 商家只能改自己的店；ADMIN 直接放行；店不存在抛 404，越权抛 403
        ownershipChecker.assertShopOwned(id);

        shop.setId(id);          // 用路径上的 id 作为 WHERE 条件，避免改错对象
        shop.setOwnerId(null);   // 不允许通过修改接口转移店主
        // updateById 默认只更新「非空字段」，所以这是局部更新（前端没传的字段不动）
        boolean ok = this.updateById(shop);
        if (!ok) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
    }

    @Override
    public void deleteShop(Long id) {
        ownershipChecker.assertShopOwned(id);
        // removeById 因实体有 @TableLogic，实际执行 UPDATE ... SET deleted=1（逻辑删除）
        boolean ok = this.removeById(id);
        if (!ok) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
    }

    @Override
    public PageResult<Shop> pageShops(ShopPageQuery query) {
        // LambdaQueryWrapper：用方法引用拼条件，编译期就能查出字段名写错
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        // 第一个参数是「条件是否生效」：名称非空才加 LIKE，状态非 null 才加 =
        wrapper.like(StringUtils.hasText(query.getName()), Shop::getName, query.getName());
        wrapper.eq(query.getStatus() != null, Shop::getStatus, query.getStatus());
        wrapper.eq(query.getOwnerId() != null, Shop::getOwnerId, query.getOwnerId());
        wrapper.orderByDesc(Shop::getCreateTime);

        // MERCHANT 只看自己名下的店铺；ADMIN/null/empty 不过滤
        List<Long> shopIds = ownershipChecker.myShopIds();
        if (shopIds != null) {
            if (shopIds.isEmpty()) {
                wrapper.eq(Shop::getId, -1L); // MERCHANT 无店铺 → 返回空
            } else {
                wrapper.in(Shop::getId, shopIds);
            }
        }

        // query.toPage() 自动处理页码与每页上限；page() 是白送的分页方法
        Page<Shop> page = this.page(query.toPage(), wrapper);
        // 转成全项目统一的分页外壳
        return PageResult.of(page);
    }
}
