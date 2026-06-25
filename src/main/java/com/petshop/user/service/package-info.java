/**
 * 用户·认证·会员·AI 模块 Service 层。
 * <p>
 * 由 B 负责实现，包含：
 * <ul>
 *   <li>UserService —— 注册、登录、个人信息、密码加密</li>
 *   <li>AddressService —— 收货地址增删改查 + 默认地址管理</li>
 *   <li>MembershipLevelService —— 会员等级 &amp; 动态价格</li>
 *   <li>AiChatService —— AI 问答（大模型对接）</li>
 * </ul>
 *
 * 规范：
 * <pre>
 *   public interface UserService extends IService&lt;User&gt; { ... }
 *
 *   &#64;Service
 *   public class UserServiceImpl extends ServiceImpl&lt;UserMapper, User&gt; implements UserService { ... }
 * </pre>
 * 参考 {@link com.petshop.controller.demo.DemoItemService} 和 {@link com.petshop.controller.demo.DemoItemServiceImpl}。
 */
package com.petshop.user.service;
