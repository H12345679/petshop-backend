/**
 * 视频·消息 模块 Service 层。
 * <p>
 * 由 D 负责实现，包含：
 * <ul>
 *   <li>VideoService —— 视频上传、列表、详情、关联商品</li>
 *   <li>MessageService —— 消息推送 &amp; 已读管理</li>
 * </ul>
 *
 * 规范：
 * <pre>
 *   public interface VideoService extends IService&lt;Video&gt; { ... }
 *
 *   &#64;Service
 *   public class VideoServiceImpl extends ServiceImpl&lt;VideoMapper, Video&gt; implements VideoService { ... }
 * </pre>
 * 参考 {@link com.petshop.demo.service.DemoItemService} 和 {@link com.petshop.demo.service.impl.DemoItemServiceImpl}。
 */
package com.petshop.content.service;
