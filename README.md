# 🐾 PetShop Backend - 宠物商城后端服务

基于 **Spring Boot 3 + MyBatis-Plus** 架构打造的现代化 B2B2C / O2O 宠物电商平台后端服务。本项目不仅实现了电商标准交易链路，还融合了地图 LBS、多媒体视频流与 DeepSeek AI 智能服务，涵盖了从后台管理到前台消费的完整业务链路。

---

## 🏗 核心技术栈

- **核心框架**: Spring Boot 3 (Java 17)
- **持久层**: MyBatis-Plus + MySQL 8.x
- **缓存与并发**: Redis
- **安全与认证**: JWT (JSON Web Token)
- **第三方服务**: 七牛云 (对象存储)、DeepSeek (AI 大模型)、高德地图 API (LBS)

---

## 🧩 系统核心模块与详细代码架构讲解

本系统划分为五大核心模块，每个模块均在安全性、并发性与业务健壮性上做了深度设计，以下是核心模块的代码细节讲解：

### 模块 A：认证鉴权与权限管理体系 (Auth & User)
提供基于无状态 JWT 的用户认证体系，支持游客 (0)、普通用户、商家 (`MERCHANT`) 与管理员 (`ADMIN`) 多角色权限体系。
- **动态多维检索重构 (`UserController.java`)**：
  后台用户列表（`/api/manage`）废弃了低效的前端假分页。在 `UserServiceImpl` 中，通过 MyBatis-Plus 的 `QueryWrapper`，实现 `role`、`memberLevelId`、`status` 等多维度的全量服务端动态 SQL 过滤。
- **全局权限拦截 (`AuthInterceptor`)**：
  所有带 `/api/manage` 或特定前缀的接口均经过拦截器校验 Token 有效性，同时根据 `@CheckRole` 注解实现方法级别的角色细粒度鉴权，保障管理端接口不被越权访问。

### 模块 B：商家与商品核心 (Shop & Product)
支持 B2B2C 模式下的多商家入驻与商品多 SKU 管理，是商城的数据基石。
- **防越权守卫 (`OwnershipChecker`)**：
  系统的核心安全机制。所有涉及商家数据（如商品上下架、订单发货）的增删改查操作，均在 Service 层强制调用 `ownershipChecker.assertShopOwned(shopId)` 或 `ownershipChecker.myShopIds()`。这确保了恶意商家绝对无法通过篡改网络请求头或 Payload 中的 `shop_id` 来操作其他商铺的商品或资产。
- **商品层级模型**：
  采用 `Product` (SPU) 结合 `ProductSku` (SKU) 的标准电商模型，支持多种规格、颜色与库存独立管理。

### 模块 C：电商交易与结算链路 (Order, Cart, Coupon, Refund)
系统最核心、逻辑最复杂的模块，覆盖了从购物车试算到订单完结的全生命周期。
- **跨店拆单逻辑 (`OrderServiceImpl.createOrder`)**：
  用户若在购物车中同时结算不同商家的商品，系统在 `createOrder` 时会自动对数据进行 `groupBy(shopId)`，将其拆分为多个独立子订单。每个子订单拥有独立的 `shop_id`、物流状态，但共享同一次支付回调与前端合并展示。
- **优惠券并发与释放守卫 (`CouponService`)**：
  - **发放与锁定**：`user_coupon` 表强制 `order_id` 外键不可为 `NULL`（未使用状态设为 `0L`），确保底层 SQL 约束一致性。
  - **状态聚合释放**：跨店拆单时，同一张全场满减券会被关联到多个子订单。系统实现了 `stillInUse` 检测，只有当“所有关联的子订单均取消或退款”时，才真正回滚释放该优惠券，防止用户利用退款刷券。
- **订单超时自动取消与防并发 (CAS 乐观锁)**：
  - **分布式定时任务 (`OrderTimeoutScheduler`)**：配合 `@Scheduled(fixedDelay = 60000)` 每分钟扫表，筛选超时的待支付订单（默认 30 分钟）。
  - **CAS 并发漏洞防御**：使用 `LambdaUpdateWrapper` 限定更新条件 `.eq(Order::getStatus, 0)`。如果系统自动取消的同一瞬间，用户成功付款或手动取消，该更新操作将因 `status` 不为 0 而自动失效，完美避免了“已付款订单被定时任务误判为取消”的灾难级 Bug。

### 模块 D：多媒体、社交与 AI (Video, AI Chat, Messages)
赋予传统电商更丰富的互动体验。
- **七牛云视频流接入 (`VideoController`)**：
  对接七牛云对象存储实现宠物视频的瀑布流分享。支持视频封面的自动提取与云端播放地址分发。
- **AI 智能客服 (`AiChatController`)**：
  对接 DeepSeek 大语言模型 (`/api/ai/chat`)。在代码层面对话做了流式输出（SSE/假流式）的适配，为用户提供宠物饲养建议与智能导购服务。
- **站内信广播 (`MessageService`)**：
  支持管理员向全站广播，或向特定商家的客户精准推送，数据表分为 `message` (内容体) 与 `user_message` (投递关系)，极大优化了群发的存储开销。

### 模块 E：地图与 LBS (Map)
- **地理位置检索**：提供查询“周边宠物门店”的服务，适配 O2O 线下洗护、商品自提等业务需求。依赖于经纬度计算。

---

## 📐 开发规范与工程约定

1. **统一响应结构**：所有 Controller 接口必须返回 `Result<T>` 泛型对象，包含 `code`, `msg`, `data`，便于前端全局统一拦截处理。
2. **全局异常拦截 (`GlobalExceptionHandler`)**：将所有业务异常 (`BusinessException`)、SQL 约束异常 (`SQLIntegrityConstraintViolationException`) 以及参数校验异常在顶层捕获，转化为标准 JSON 响应，杜绝将 500 堆栈直接暴露给调用方。
3. **软删除与物理删除策略**：
   - 核心资产及日志表（用户、订单、商品、退款单）均继承 `BaseEntity` 包含 `deleted` 标志位。
   - 纯关联表或行为日志表（如点赞、收藏）继承 `BaseEntityLite`，采用物理删除减小空间占用。

---

## 🚀 部署与启动指南

### 1. 基础环境
- **JDK**: OpenJDK 17
- **中间件**: MySQL 8.x, Redis (默认端口 6379)
- **构建工具**: Maven 3.6+

### 2. 数据库初始化
进入项目根目录，运行 SQL 脚本：
```bash
# 首先导入表结构 (内置建库语句 CREATE DATABASE petshop)
mysql -uroot -p < sql/schema.sql
# 然后导入测试数据
mysql -uroot -p petshop < sql/data.sql
```

### 3. 配置修改 (`application.yml`)
修改数据库密码、Redis 配置及第三方服务密钥：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3307/petshop?...
    username: root
    password: yourpassword
  data:
    redis:
      host: 127.0.0.1
      port: 6379

qiniu:
  access-key: your-ak
  secret-key: your-sk
ai:
  deepseek:
    api-key: your-api-key
```

### 4. 运行服务
运行启动类 `PetShopApplication`，系统默认运行在 `8088` 端口。

<div align="center">
<b>🐾 PetShop Backend · Built with ❤️</b>
</div>
