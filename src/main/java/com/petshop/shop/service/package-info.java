/**
 * 商店 &amp; 商品模块 Service 层。
 * <p>
 * 由 A 负责实现，包含：
 * <ul>
 *   <li>ShopService / ShopServiceImpl —— 商店 CRUD</li>
 *   <li>ProductService / ProductServiceImpl —— 商品 CRUD（含分类、SKU）</li>
 * </ul>
 *
 * 规范：
 * <pre>
 *   public interface ShopService extends IService&lt;Shop&gt; { ... }
 *
 *   &#64;Service
 *   public class ShopServiceImpl extends ServiceImpl&lt;ShopMapper, Shop&gt; implements ShopService { ... }
 * </pre>
 * 参考 {@link com.petshop.controller.demo.DemoItemService} 和 {@link com.petshop.controller.demo.DemoItemServiceImpl}。
 */
package com.petshop.shop.service;
