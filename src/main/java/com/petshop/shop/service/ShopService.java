package com.petshop.shop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.petshop.common.PageResult;
import com.petshop.shop.entity.Shop;

/**
 * 商店 Service 接口。
 * <p>
 * 继承 IService&lt;Shop&gt; 后，单表 CRUD（save/getById/updateById/removeById/list/page…）全部白送。
 * 这里只声明「带业务逻辑」的方法：建店要绑店主、改/删要校验归属、列表要带条件。
 */
public interface ShopService extends IService<Shop> {

    /** 创建商店：商家建店时 owner_id 自动绑定当前登录人。 */
    void createShop(Shop shop);

    /** 修改商店：商家只能改自己的店（归属校验）。 */
    void updateShop(Long id, Shop shop);

    /** 删除商店（逻辑删除）：商家只能删自己的店。 */
    void deleteShop(Long id);

    /** 分页查询：支持按名称模糊 + 营业状态过滤。 */
    PageResult<Shop> pageShops(ShopPageQuery query);

    /** 同步所有商店数据到 ES */
    long syncAllToES();
}
