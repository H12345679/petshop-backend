package com.petshop.shop.controller;

import com.petshop.common.BusinessException;
import com.petshop.common.PageResult;
import com.petshop.common.Result;
import com.petshop.common.ResultCode;
import com.petshop.log.annotation.LogOperation;
import com.petshop.security.RequireRole;
import com.petshop.shop.entity.Shop;
import com.petshop.shop.service.ShopPageQuery;
import com.petshop.shop.service.ShopService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 商店接口（对应《项目接口设计文档》A 模块 1~5 节）。
 * <p>
 * 约定：
 * <ul>
 *   <li>@RestController + @RequestMapping("/api/shops")：类上统一前缀，方法上只写子路径；</li>
 *   <li>无 @RequireLogin/@RequireRole 的方法 = 公开接口（详情、列表对所有人开放）；</li>
 *   <li>写操作加 @RequireRole({"ADMIN","MERCHANT"})；商家「只能动自己店」由 Service 里的归属校验兜底；</li>
 *   <li>统一返回 Result&lt;T&gt;，异常交给 GlobalExceptionHandler 兜底。</li>
 * </ul>
 */
@Tag(name = "01-商店")
@RestController
@RequestMapping("/api/shops")
public class ShopController {

    @Autowired
    private ShopService shopService;

    @Operation(summary = "创建商店（ADMIN/MERCHANT）")
    @RequireRole({"ADMIN", "MERCHANT"})
    @PostMapping
    public Result<Shop> create(@Valid @RequestBody Shop shop) {
        shopService.createShop(shop);
        return Result.success(shop);
    }

    @Operation(summary = "商店详情（公开）")
    @GetMapping("/{id}")
    public Result<Shop> getById(@PathVariable Long id) {
        Shop shop = shopService.getById(id);
        if (shop == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return Result.success(shop);
    }

    @Operation(summary = "修改商店（ADMIN/MERCHANT 本店）")
    @RequireRole({"ADMIN", "MERCHANT"})
    @LogOperation("修改/审核店铺")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Shop shop) {
        shopService.updateShop(id, shop);
        return Result.success();
    }

    @Operation(summary = "删除商店（逻辑删除，ADMIN/MERCHANT 本店）")
    @RequireRole({"ADMIN", "MERCHANT"})
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        shopService.deleteShop(id);
        return Result.success();
    }

    @Operation(summary = "商店分页查询（公开，支持名称模糊 + 营业状态过滤）")
    @GetMapping
    public Result<PageResult<Shop>> page(ShopPageQuery query) {
        return Result.success(shopService.pageShops(query));
    }

    @Operation(summary = "全量同步商店到ES (内部用)")
    @PostMapping("/es/sync")
    public Result<Long> syncToES() {
        return Result.success(shopService.syncAllToES());
    }

}
