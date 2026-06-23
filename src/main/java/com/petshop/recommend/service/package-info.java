/**
 * 推荐·行为·收藏 模块 Service 层。
 * <p>
 * 由 E 负责实现，包含：
 * <ul>
 *   <li>UserBehaviorService —— 用户行为记录（浏览/收藏/加购/购买）</li>
 *   <li>FavoriteService —— 商品收藏/取消收藏</li>
 *   <li>RecommendService —— 协同过滤推荐（加分项）</li>
 * </ul>
 *
 * 规范：
 * <pre>
 *   public interface FavoriteService extends IService&lt;Favorite&gt; { ... }
 *
 *   &#64;Service
 *   public class FavoriteServiceImpl extends ServiceImpl&lt;FavoriteMapper, Favorite&gt; implements FavoriteService { ... }
 * </pre>
 * 参考 {@link com.petshop.controller.demo.DemoItemService} 和 {@link com.petshop.controller.demo.DemoItemServiceImpl}。
 */
package com.petshop.recommend.service;
