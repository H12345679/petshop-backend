# 🐾 PetShop Backend — 宠物商店后端

> **最后更新**：2026-07-18 &nbsp;|&nbsp; **当前版本**：v2.0.0（微服务版） &nbsp;|&nbsp; **网关入口**：8088（前端代理不变）
> **仓库地址**：Gitee 私有仓库 &nbsp;|&nbsp; **接口文档**：各服务独立 `http://localhost:<服务端口>/doc.html`（端口见下方微服务架构表）

---

<!-- ====================================================================== -->

<!-- AI-CONTEXT: 项目结构化元数据（供 AI / Copilot / ChatGPT 快速理解项目）     -->

<!-- ====================================================================== -->

<!--
AI_PROJECT_METADATA:
  project: petshop-backend
  type: Spring Cloud Alibaba microservices (团队实训项目, v2.0 由单体拆分而来)
  language: Java 17
  framework: Spring Boot 3.5.16 + Spring Cloud 2025.0.3 + Spring Cloud Alibaba 2025.0.0.0 + MyBatis-Plus 3.5.12
  service_registry: Nacos (standalone, port 8848)
  api_gateway: Spring Cloud Gateway (port 8088, 与原单体端口一致)
  rpc: OpenFeign (服务间调用) + RabbitMQ (事件驱动)
  data_strategy: 共享库模式 (所有服务连同一 petshop 库, 实体/Mapper 下沉 petshop-common, 保住本地事务)
  build_tool: Maven
  database: MySQL 8.0 (port 3307, schema: petshop)
  cache: Redis 7 (port 6379)
  message_queue: RabbitMQ 3.13 (port 5672, mgmt 15672)
  search_engine: Elasticsearch 7.17 (port 9200)
  file_storage: 七牛云 (Qiniu Cloud OSS)
  auth: JWT (jjwt 0.11.5) + 自研拦截器 (非 Spring Security)
  api_docs: Knife4j 4.4.0 (Swagger UI at /doc.html)
  password_hash: BCrypt (spring-security-crypto, 不引入完整 Spring Security)
  ai_integration: DeepSeek API (OpenAI 兼容协议) / Mock 模式
  
  team_roles:
    A (黄舰): 公共框架搭建 + 商店/商品/分类/首页模块
    B (肖志超): 用户认证/注册/登录 + 地址管理 + 会员等级 + OAuth + 安全扫描
    C (贾恩奇): 购物车 + 订单(状态机) + 退单 + 评价 + 优惠券 (核心交易)
    D (陈顺炜): Vue 前端工程框架 + 地图找附近 (LBS)
    E (陈闯): 视频模块 + 消息推送 + 收藏 + AI问答 + 协同过滤推荐
  
  module_status:
    common_framework (A2-A6): ✅ 完成
    shop (A): ✅ 完成 (CRUD + 分页 + 归属校验)
    product (A): ✅ 完成 (多SKU + 分类树 + 首页策略)
    user_auth (B): ✅ 完成 (注册/登录/改密/BCrypt/JWT)
    address (B): ✅ 完成 (增删改查 + 默认地址)
    membership (B): ✅ 完成 (等级列表 + 自动升级)
    ai_chat (E): ✅ 完成 (Mock + DeepSeek双模式)
    oauth (B): ✅ 完成 (第三方登录框架)
    file_upload: ✅ 完成 (七牛云图片/视频上传)
    cart (C): ✅ 完成 (增删改查 + 勾选)
    order (C): ✅ 完成 (拆单 + 状态机 + 支付)
    refund (C): ✅ 完成 (申请 + 审核 + 退款)
    review (C): ✅ 完成 (评价 + 商家回复)
    coupon (C): ✅ 完成 (优惠券发放/领取/使用)
    video (E): ✅ 完成 (上传/CRUD/播放量/关联商品)
    favorite (E): ✅ 完成 (收藏/取消/列表/检查)
    message (E): ⬜ 待实现 (实体+表已建, Service 待写)
    map (D): ⬜ 前端集成中 (后端复用shop表, 需加 /api/map/shops)
    recommend (E): ⬜ 第二阶段 (实体+表已建, 协同过滤算法待写)
    user_behavior (E): 🔧 进行中 (实体+Mapper 已建, 行为埋点待完善)
    stats (D/E): ⬜ 第二阶段 (销量/订单/会员/商品统计)
  
  database_tables: [
    demo_item, shop, product_category, product, product_sku,
    membership_level, user, address, payment, ai_chat_log, user_oauth,
    cart_item, orders, order_item, order_status_log, refund, review,
    coupon, user_coupon, video, message, user_message,
    user_behavior, favorite, user_item_score, user_similarity, recommend_result
  ]
  
  key_design_decisions:
    - 分阶段开发: 第一阶段跑通核心流程；高耦合加分项(首页策略/会员价/统计/协同推荐)放第二阶段
    - 订单状态机: 0待支付→1待发货→2待收货→3待评价→4已完成；取消(-1)，退款(-2/-3/-4)
    - 订单拆单: 一个订单只属于一家商店, 跨店购物车结算时按 shop_id 拆成多张订单
    - 价格来源: 无规格商品用 product.price/stock; 有规格用 product_sku.price/stock
    - 物理删除: cart_item 和 favorite 走物理删除(无 deleted 列), 其余表走逻辑删除
    - 归属校验: ADMIN 全站放行, MERCHANT 通过 OwnershipChecker 校验店铺归属，只能管本店
    - 用户名不复用: 逻辑删除后用户名仍占用, 注册查重以 deleted=0 为准
    - AI双模式: ai.provider=mock(默认) 或 deepseek(需配置 api-key)
-->

## 📖 目录

- [微服务架构 (v2.0)](#-微服务架构-v20)
- [项目简介](#-项目简介)
- [团队分工与进度](#-团队分工与进度)
- [技术架构](#-技术架构)
- [模块详细说明](#-模块详细说明)
- [数据库设计](#-数据库设计)
- [API 接口清单](#-api-接口清单)
- [项目结构](#-项目结构)
- [环境搭建与启动](#-环境搭建与启动)
- [开发规范与约定](#-开发规范与约定)
- [Git 工作流](#-git-工作流)
- [常见问题](#-常见问题)
- [第二阶段：高耦合加分项](#-第二阶段高耦合加分项)

---

## 🧩 微服务架构 (v2.0)

> v2.0 将原单体按业务域拆分为 **1 网关 + 8 业务服务** 的 Spring Cloud Alibaba 微服务架构。
> 前端仍只面向 **8088**（网关端口 = 原单体端口），`vue.config.js` 代理零改动。

### 服务与端口

| 模块                        | 端口 | 职责                                              |
| --------------------------- | ---- | ------------------------------------------------- |
| `petshop-gateway`           | 8088 | API 网关：统一入口，按路径前缀路由（详见其 application.yml） |
| `petshop-user-service`      | 8101 | 认证/用户/地址/宠物档案/会员等级（含邮件验证码）  |
| `petshop-product-service`   | 8102 | 商品/分类/首页/ES 搜索/七牛文件上传               |
| `petshop-shop-service`      | 8103 | 店铺/店铺收藏/门店地图 LBS                        |
| `petshop-order-service`     | 8104 | 购物车/订单状态机/退款/评价/优惠券 + 超时取消(MQ 死信) |
| `petshop-content-service`   | 8105 | 短视频/留言/收藏（含 ES 检索）                    |
| `petshop-recommend-service` | 8106 | 行为消费(MQ)/画像/多路召回推荐                    |
| `petshop-ai-service`        | 8107 | AI 智能客服（SSE 流式，网关透传）                 |
| `petshop-stats-service`     | 8108 | 经营统计报表 + 系统操作日志查询                   |

另有两个公共构件：**`petshop-common`**（统一返回/异常/JWT 拦截器/工具类/公共配置 + 共享实体与 Mapper + Feign 客户端）；网关不依赖 common（响应式技术栈）。

### 关键设计（答辩要点）

1. **注册发现**：所有服务注册到 **Nacos**（localhost:8848），网关与 Feign 通过服务名 `lb://petshop-xxx-service` 负载均衡寻址。
2. **共享库模式**：8 个服务连同一个 `petshop` 库，实体/Mapper 下沉 `petshop-common`。好处：下单扣库存、优惠券核销等关键链路保持**本地事务**，不引入 Seata 也不牺牲一致性；代价：数据私有性靠约定保证。
3. **服务间调用分三类**：
   - **OpenFeign 同步调用**：`OwnershipChecker` 商家归属校验跨服务查 shop 服务（`ShopClient`）；推荐服务跨服务取宠物画像（`UserPetClient`）。内部接口统一挂 `/internal/**`，网关不路由、外部不可达。
   - **RabbitMQ 事件驱动**：行为埋点（`@TrackBehavior` 切面 → `recommend.exchange` → 推荐服务消费）；订单超时取消（TTL + 死信队列）。
   - **共享 Mapper 直查**：纯只读的跨域数据组装（如订单列表拼商品名），走共享库直查，避免无谓的远程调用放大。
4. **JWT 鉴权**：网关只透传 `Authorization` 头；各服务用 common 里的 `JwtInterceptor`（注解驱动 `@RequireLogin/@RequireRole`）自行校验，无状态可水平扩展。
5. **配置管理**：公共配置下沉 `petshop-common` 的 `common-config.yml`，各服务 `spring.config.import` 引入；后续可无缝平移到 Nacos 配置中心。

---

## 🏪 项目简介

**PetShop** 是一个全功能宠物商店电商平台后端，v2.0 采用 **Spring Cloud Alibaba 微服务架构**（由 v1.0 单体演进而来），面向"程序设计实训"课程需求开发。项目支持多角色（普通用户 / 商家 / 管理员），涵盖商品管理、订单交易、AI 智能客服、视频内容、会员体系等完整电商业务链路。

### 核心业务能力

| 能力域     | 功能点                                                                   |
| ---------- | ------------------------------------------------------------------------ |
| 🏪 商店    | 商店 CRUD、分页查询、营业状态管理、商家归属校验、地图 LBS 找店           |
| 📦 商品    | 多 SKU 规格、分类树、首页热销/上新策略、商品搜索                         |
| 👤 用户    | 注册/登录(BCrypt)、JWT 鉴权、修改密码、OAuth、后台用户管理               |
| 📍 地址    | 收货地址增删改查、默认地址管理                                           |
| 🛒 购物车  | 加购/删除/修改数量/勾选结算                                              |
| 📋 订单    | **完整状态机**（待支付→待发货→待收货→待评价→已完成）、跨店拆单 |
| 💰 退单    | 用户申请退款、管理员审核（通过/拒绝/直接退单）                           |
| ⭐ 评价    | 订单评价（1-5星+图文）、商家回复、删除违规评价                           |
| 🎫 优惠券  | 后台发券、前端领券、订单结算抵扣                                         |
| 🤖 AI 客服 | DeepSeek 大模型 / Mock 双模式、对话历史记录                              |
| 🎬 视频    | 七牛云上传、视频 CRUD、播放量统计、关联商品跳转                          |
| ❤️ 互动  | 商品收藏/取消、消息中心系统通知                                          |
| 👑 会员    | 等级体系（普通/银卡/金卡）、积分升级、会员折扣                           |
| 📁 文件    | 七牛云 OSS 图片/视频统一上传                                             |

---

## 👥 团队分工与进度

> 团队人数：**5 人** ｜ 周期：**2 周** ｜ 协作：**Git + GitHub**
> 📅 **当前进度**：后端核心接口已基本完成（~85%），准备前端联调与第二阶段加分项。

| 成员                 | 角色          | 负责模块                                   |  后端状态  |
| -------------------- | ------------- | ------------------------------------------ | :---------: |
| **A (黄舰)**   | 组长/架构     | 公共框架 + 商店 + 商品 + 首页展示规则      | ✅ 核心完成 |
| **B (肖志超)** | 后端/安全     | 认证 + 用户 + 会员 + 地址 + 安全扫描       | ✅ 核心完成 |
| **C (贾恩奇)** | 全栈/交易     | 订单 + 购物车 + 退单 + 评价 + 优惠券       | ✅ 核心完成 |
| **D (陈顺炜)** | 前端架构/数据 | Vue前端框架 + 地图找附近(LBS) + 可视化统计 | 🔧 前端主导 |
| **E (陈闯)**   | 全栈/媒体     | 视频 + 消息 + 收藏 + AI问答 + 协同推荐     | 🔧 部分待补 |

### 模块详细完成度

* **已完成 (✅)**：基础框架 (A)、商店/商品 CRUD (A)、注册登录/JWT (B)、收货地址 (B)、会员等级 (B)、购物车/订单核心状态机/退单/评价/优惠券 (C)、视频上传及管理 (E)、收藏 (E)、AI 问答 (E)。
* **进行中 (🔧)**：用户行为记录埋点 (E)、地图选点前端组件 (D)。
* **待开发 (⬜ 第一阶段)**：消息推送及已读管理 (E)。
* **待开发 (⬜ 第二阶段加分项)**：协同过滤推荐 (E)、数据可视化看板 (D/E)、会员动态价格策略 (B/C)、首页复杂展示规则 (A)。

---

## 🏗 技术架构

### 技术栈一览

```
┌─────────────────────────────────────────────────────────────────┐
│                        客户端 (Vue 3 前端)                         │
├─────────────────────────────────────────────────────────────────┤
│                     Nginx / 开发服务器                            │
├─────────────────────────────────────────────────────────────────┤
│  Spring Boot 2.7.18                                             │
│  ├── Web 层：Controller + Knife4j (Swagger)                     │
│  ├── 安全层：JWT(jjwt) + 自研拦截器 + @RequireLogin/@RequireRole │
│  ├── 业务层：Service + MyBatis-Plus 3.5.5                       │
│  ├── AI 层 ：AiProvider 接口 (Mock / DeepSeek 可切换)            │
│  └── 工具层：RedisUtil + QiniuService + OwnershipChecker        │
├─────────────────────────────────────────────────────────────────┤
│  中间件 (Docker Compose 一键启动)                                 │
│  ├── MySQL 8.0        (localhost:3307, 库: petshop)              │
│  ├── Redis 7          (localhost:6379, AOF 持久化)               │
│  ├── RabbitMQ 3.13    (localhost:5672, 管理界面:15672)            │
│  ├── Elasticsearch 7.17 (localhost:9200, 单节点)                 │
│  └── 七牛云 OSS        (外部服务, 图片+视频存储)                   │
└─────────────────────────────────────────────────────────────────┘
```

### 依赖版本矩阵

| 组件                   | 版本     | 用途            |
| ---------------------- | -------- | --------------- |
| JDK                    | 17       | 运行时          |
| Spring Boot            | 2.7.18   | 核心框架        |
| MyBatis-Plus           | 3.5.5    | ORM + 代码生成  |
| Druid                  | 1.2.20   | 数据库连接池    |
| jjwt                   | 0.11.5   | JWT 签发/校验   |
| Knife4j                | 4.4.0    | API 文档 UI     |
| Lombok                 | 1.18.30  | 代码简化        |
| 七牛云 SDK             | 7.13.0   | 对象存储        |
| spring-security-crypto | (SB管理) | BCrypt 密码加密 |

---

## 📦 模块详细说明

### 1. 公共框架与安全 (`common` / `security`)

**统一返回体**：所有接口返回 `Result<T>` 或 `PageResult<T>`。
**全局异常处理**：`GlobalExceptionHandler` 捕获 `BusinessException`。
**轻量级安全体系**：

* **JWT 鉴权**：`JwtInterceptor` 解析 Token，存入 `UserContext`。
* **注解验权**：`@RequireLogin` 强制登录，`@RequireRole({"ADMIN", "MERCHANT"})` 强制角色。
* **数据隔离校验** (`OwnershipChecker`)：**核心设计**。`MERCHANT` 角色在进行商品上下架、发货、退单审核、评价管理时，后端强制校验该操作对象对应的 `shop_id` 是否归属当前登录商家的 `owner_id`，防止越权操作他人店铺。

### 2. 商店与商品 (A)

* **商店**：支持创建、修改、地图选点（经纬度）、商家归属隔离。
* **商品**：支持单规格与多规格（SKU）。价格与库存读取策略：无规格读 `product`，有规格读 `product_sku`。
* **首页策略**：提供 `HOT`（热销）、`NEW`（上新）的基础展示，预留了第二阶段的 `RECOMMEND`（推荐）策略。

### 3. 用户与会员 (B)

* **认证**：密码通过 BCrypt 强哈希存储。提供修改当前用户密码功能。
* **角色模型**：注册默认为 `USER`。由 `ADMIN` 在后台将其变更为 `MERCHANT`（商家授权）后，该用户方可创建并管理本人名下的店铺。
* **会员升级**：`membership_level` 表定义门槛（累计积分），用户积分达标自动匹配相应等级折扣。

### 4. 交易：购物车/订单/退单/评价 (C)

**核心状态机 (`OrderStatus`)**：

```
  0(待支付) ──支付──→ 1(待发货) ──发货──→ 2(待收货)
       │                  │                    │
     取消(-1)           取消(-1)             收货
                                               ▼
                                  3(待评价) ──评价──→ 4(已完成)
                                       │
  退单流程：状态2/3 ──申请退单(-2)──→ 管理员审核
              ├─ 通过  → -3(已退款)
              ├─ 拒绝  → 恢复原状态(2或3，用 prev_status 记录)
              └─ 商家直接退单 → -4
```

* **跨店拆单**：购物车结算时按 `shop_id` 分组，生成多张订单。
* **优惠分摊**：订单项 `order_item` 保存 `real_pay_amount`（扣除优惠后的实付分摊），以此作为退款上限。
* **结算计价**：目前以优惠券（满减/折扣）计算为准。第二阶段将通过拦截器注入会员卡动态折扣。

### 5. 媒体、LBS、AI 与其它 (D/E)

* **视频关联 (E)**：视频不仅增加播放量，还可挂载 `productId` 引导至商品详情。
* **AI 问答 (E)**：`MockAiProvider` 返回预设知识；`DeepSeekAiProvider` 调用大模型。带有上下文 Session 的对话记录。
* **地图 LBS (D)**：前端接入百度/高德地图 SDK，通过 `/api/map/shops` 获取附近营业商家（复用 shop 表数据）。

---

## 🗄 数据库设计

### ER 关系概览

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│    shop      │←───│   product     │←───│  product_sku     │
│  (商店)      │    │  (商品)       │    │  (商品规格)       │
└──────┬───────┘    └──────┬───────┘    └─────────────────┘
       │                   │
       │ owner_id          │ product_id
       ▼                   ▼
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│    user      │←───│  cart_item    │    │  product_category │
│  (用户)      │    │  (购物车)     │    │  (分类-树形)       │
└──┬───┬───┬──┘    └──────────────┘    └─────────────────┘
   │   │   │
   │   │   └──→  address (收货地址)
   │   │
   │   └──────→  orders ──→ order_item (订单明细)
   │              │  │
   │              │  └──→ order_status_log (状态流转审计)
   │              │
   │              └──→ refund (退单)
   │              └──→ review (评价)
   │              └──→ payment (支付流水)
   │
   ├──→ favorite (收藏, 物理删除)
   ├──→ user_behavior (行为日志)
   ├──→ ai_chat_log (AI 对话)
   ├──→ user_oauth (第三方登录)
   ├──→ user_coupon (用户优惠券)
   └──→ user_message (消息已读)
         │
         └──→ message (消息内容)

推荐系统（第二阶段离线计算）:
  user_behavior → user_item_score → user_similarity → recommend_result
```

### 关键表设计说明（27 张）

1. **逻辑 vs 物理删除**：绝大部分表采用 `deleted` (0/1) 逻辑删除。仅 `cart_item`（购物车）和 `favorite`（收藏）采用真实物理删除。
2. **用户名唯一性**：注册查重以 `deleted=0` 为准。逻辑删除的用户其用户名仍被占用，不可复用。
3. **消息表分离**：`message` 存广播或定向的公共内容；`user_message` 维护每个用户的已读状态（`is_read`）。

---

## 🔌 API 接口清单

> 完整入参/出参格式见配套《项目接口设计文档》，以下为路由速查索引。
> 📌 **鉴权说明**: [公开] = 免 Token；[登录] = 需 Token；[ADMIN] = 需超管角色；[ADMIN·MERCHANT] = 超管或商家角色。MERCHANT 请求一律按其 `owner_id` 过滤数据。

### B模块：认证·用户·会员

| 方法                | 路径                        | 权限  | 说明                          |
| ------------------- | --------------------------- | ----- | ----------------------------- |
| POST                | `/api/auth/register`      | 公开  | 用户注册                      |
| POST                | `/api/auth/login`         | 公开  | 用户登录                      |
| GET                 | `/api/users/me`           | 登录  | 当前登录用户信息              |
| PUT                 | `/api/users/me`           | 登录  | 修改当前用户信息              |
| PUT                 | `/api/users/me/password`  | 登录  | 修改当前用户密码              |
| GET                 | `/api/users/manage`       | ADMIN | 后台用户管理列表              |
| PUT                 | `/api/users/{id}/role`    | ADMIN | 授予/变更用户角色（商家授权） |
| GET/POST/PUT/DELETE | `/api/addresses/**`       | 登录  | 地址增删改查                  |
| GET                 | `/api/membership/levels`  | 公开  | 会员等级列表                  |
| POST                | `/api/membership/upgrade` | 登录  | 触发积分升级                  |

### A模块：商店·商品

| 方法            | 路径                   | 权限            | 说明                      |
| --------------- | ---------------------- | --------------- | ------------------------- |
| POST/PUT/DELETE | `/api/shops/**`      | ADMIN·MERCHANT | 商店管理 (MERCHANT限本店) |
| GET             | `/api/shops`         | 公开            | 商店分页查询              |
| POST/PUT/DELETE | `/api/products/**`   | ADMIN·MERCHANT | 商品管理 (含多SKU)        |
| GET             | `/api/products`      | 公开            | 商品列表/搜索             |
| GET             | `/api/categories`    | 公开            | 分类树查询                |
| GET             | `/api/home/products` | 公开            | 首页策略商品展示          |

### C模块：购物车·订单·退单·评价·优惠券

| 方法                | 路径                       | 权限            | 说明                        |
| ------------------- | -------------------------- | --------------- | --------------------------- |
| POST/GET/PUT/DELETE | `/api/cart/**`           | 登录            | 购物车增删改查及勾选        |
| POST                | `/api/orders/pre-settle` | 登录            | 结算预览（含优惠试算）      |
| POST                | `/api/orders`            | 登录            | 创建订单（按 shop_id 拆单） |
| PUT                 | `/api/orders/{id}/pay`   | 登录            | 支付订单                    |
| PUT                 | `/api/orders/{id}/ship`  | ADMIN·MERCHANT | 商家发货                    |
| GET                 | `/api/orders/manage`     | ADMIN·MERCHANT | 后台订单管理列表            |
| POST/PUT            | `/api/refunds/**`        | 登录/商家       | 申请退单 / 审核退单         |
| POST                | `/api/reviews`           | 登录            | 提交订单明细评价            |
| GET                 | `/api/reviews/manage`    | ADMIN·MERCHANT | 后台评价管理列表            |
| DELETE              | `/api/reviews/{id}`      | ADMIN           | 管理员删除违规评价          |
| POST/GET            | `/api/coupons/**`        | ADMIN           | 后台发放/管理优惠券         |

### D模块：地图LBS (及前端)

| 方法 | 路径               | 权限 | 说明                   |
| ---- | ------------------ | ---- | ---------------------- |
| GET  | `/api/map/shops` | 公开 | 前端地图找附近营业商店 |

### E模块：视频·收藏·消息·AI

| 方法                | 路径                    | 权限            | 说明                           |
| ------------------- | ----------------------- | --------------- | ------------------------------ |
| POST                | `/api/videos/upload`  | ADMIN·MERCHANT | 七牛云视频上传                 |
| POST/GET/PUT/DELETE | `/api/videos/**`      | 公开/商家       | 视频列表与详情（自动播放量+1） |
| POST/GET/DELETE     | `/api/favorites/**`   | 登录            | 收藏增删查                     |
| POST                | `/api/messages`       | ADMIN           | 后台发送广播/定向消息          |
| GET/PUT             | `/api/messages/my/**` | 登录            | 我的消息与已读标记             |
| POST/GET            | `/api/ai/chat/**`     | 公开/登录       | AI 客服提问与历史记录          |

---

## 🚀 环境搭建与启动

### 前置条件

| 工具           | 版本要求 | 说明             |
| -------------- | -------- | ---------------- |
| JDK            | 17+      | 推荐 OpenJDK 17  |
| Docker Desktop | 最新     | 启动中间件       |
| IDEA           | 2022+    | 推荐，自带 Maven |

### Step 1：启动中间件

```bash
# 在 petshop-backend 目录下
docker compose up -d          # 启动全部中间件(Nacos/MySQL/Redis/RabbitMQ/ES)
docker compose ps             # 等所有服务状态变 healthy
docker compose logs -f mysql  # 查看 MySQL 日志（首次建表+导数据）
```

| 中间件        | 地址               | 账号/密码     | 说明                                         |
| ------------- | ------------------ | ------------- | -------------------------------------------- |
| **Nacos**     | `localhost:8848` | 无（已关鉴权）| 注册中心，控制台 http://localhost:8848/nacos |
| MySQL         | `localhost:3307` | root / root   | 库`petshop`，首次自动执行 `sql/init.sql` |
| Redis         | `localhost:6379` | 无            | AOF 持久化                                   |
| RabbitMQ      | `localhost:5672` | guest / guest | 管理界面 http://localhost:15672              |
| Elasticsearch | `localhost:9200` | 无            | 已关闭鉴权，512MB 堆                         |

> **内存不够？** 最小可跑集：`docker compose up -d nacos mysql redis rabbitmq`（ES 相关搜索功能降级）

### Step 2：配置七牛云与 AI（可选）

创建 `petshop-common/src/main/resources/local-config.yml`（已加入 `.gitignore`，各服务启动时自动引入）：

```yaml
qiniu:
  access-key: 你的AccessKey
  secret-key: 你的SecretKey
  bucket: 你的Bucket名
  domain: http://你的CDN域名
ai:
  provider: deepseek          # mock(默认) 或 deepseek / bailian
  deepseek:
    api-key: sk-xxx           # 你的 DeepSeek API Key
```

### Step 3：运行微服务

先整体安装一次（把 common 装进本地仓库）：

```bash
mvn -DskipTests install
```

再按顺序启动各服务（IDEA 里直接跑各模块的 `XxxServiceApplication`，或命令行）：

```bash
# ① 网关（前端唯一入口 8088）
mvn -pl petshop-gateway spring-boot:run
# ② 业务服务（顺序不严格要求，服务间调用有兜底；建议先 user/shop 后 product/order）
mvn -pl petshop-user-service spring-boot:run       # 8101
mvn -pl petshop-product-service spring-boot:run    # 8102
mvn -pl petshop-shop-service spring-boot:run       # 8103
mvn -pl petshop-order-service spring-boot:run      # 8104
mvn -pl petshop-content-service spring-boot:run    # 8105
mvn -pl petshop-recommend-service spring-boot:run  # 8106
mvn -pl petshop-ai-service spring-boot:run         # 8107
mvn -pl petshop-stats-service spring-boot:run      # 8108
```

> **不必全启**：日常联调只需 网关 + 你负责的服务（+它 Feign 依赖的服务）。
> 全部启动后在 Nacos 控制台 http://localhost:8848/nacos 可看到 9 个实例。
> 接口文档：各服务独立访问 `http://localhost:<端口>/doc.html`（如用户服务 http://localhost:8101/doc.html）。
> 前端不变：仍启动 petshop-frontend（8099），其 `/api` 代理指向网关 8088。

---

## 📐 开发规范与约定

### 分包与编码约定

* **分包结构**：`com.petshop.<模块名>/{controller,entity,mapper,service/impl,dto,vo}`
* **实体类继承**：常规表继承 `BaseEntity`（含软删）；物理删除表继承 `BaseEntityLite`。
* **日期格式**：统一返回 `yyyy-MM-dd HH:mm:ss`，时区 GMT+8。

### 商家越权防护 (OwnershipChecker)

所有涉及 C/E 模块的后端写操作或商家列表读取操作，**必须**调用 `ownershipChecker.assertShopOwned(shopId)` 或 `ownershipChecker.myShopIds()` 以确保商家（`MERCHANT`）只能查看或操作属于自己的数据。`ADMIN` 自动放行。

---

## 🔀 Git 工作流

```
main (受保护，需 PR)
  └── dev (集成分支)
       ├── feature/shop-A      # 黄舰的商店/商品分支
       ├── feature/auth-B      # 肖志超的认证分支
       └── feature/order-C     # 贾恩奇的交易分支
```

* 开发在 `feature/*` 进行，通过 Gitee PR 提交到 `dev` 进行集成测试。
* **Commit 规范**：使用 `feat/fix/docs/refactor` 前缀。

---

## ❓ 常见问题

<details>
<summary><b>Q: 启动报 Bean 名冲突 (BeanDefinitionStoreException)?</b></summary>
检查是否有不同包下同名的脚手架类。已知历史问题：<code>recommend</code> 包下曾有重复的 <code>Favorite</code>，已在 <code>edf833c</code> 修复。
</details>

<details>
<summary><b>Q: 表名用 orders 而不是 order?</b></summary>
<code>ORDER</code> 是 MySQL 保留字。实体类通过 <code>@TableName("orders")</code> 映射。
</details>

<details>
<summary><b>Q: 商家如何入驻？</b></summary>
注册普通用户后，由 ADMIN 在后台“用户管理”将其角色改为 <code>MERCHANT</code>。该用户登录后即可创建属于自己（绑定其 owner_id）的商店。
</details>

---

## 🔮 第二阶段：高耦合加分项

以下四个功能为强耦合加分项，将在第一阶段整合完毕并取得真实业务数据后开展：

| 功能                       | 拟负责人 | 依赖条件                   | 接入方式                                   |
| -------------------------- | -------- | -------------------------- | ------------------------------------------ |
| **协同过滤推荐**     | E (陈闯) | 足量浏览/收藏行为数据      | 定时跑批计算推荐结果`recommend_result`   |
| **数据统计可视化**   | D/E      | 交易数据结构稳定           | ECharts 接入，销量/订单/会员多维报表       |
| **会员动态价格策略** | B/C      | 基础结算金额计算无误       | 拦截器/策略模式注入`pre-settle` 试算环节 |
| **首页复杂展示规则** | A (黄舰) | 推荐与营销(优惠券)输出就绪 | 替换原本简单的 HOT/NEW 为智能综合排序     |

---

## 🛠 最新核心代码细节与优化 (Latest Core Code Details)

在近期的开发与代码审计中，我们对系统核心链路进行了深度优化，主要包括：

### 1. 订单超时自动取消与并发控制安全
* **定时扫表机制**：新增 `OrderTimeoutScheduler`，通过 `@Scheduled(fixedDelay = 60000)` 每分钟扫描一次超时订单。超时阈值可通过 `application.yml` 的 `order.pay-timeout-minutes` 配置（默认 30 分钟）。
* **CAS 乐观锁防并发**：`OrderServiceImpl.cancel()` 重构了状态更新逻辑。使用 `LambdaUpdateWrapper` 限定 `.eq(Order::getStatus, from)`，防止用户在自动取消的同一毫秒内完成支付或手动取消导致的互相覆盖。
* **优惠券与库存安全回滚**：修复了 `user_coupon` 的 `order_id` 字段不支持 `null` 的 SQL 约束漏洞，现已正确回滚为 `0L`。跨店拆单时，通过子订单状态聚合校验（`stillInUse`），防止提前释放整单优惠券。
* **审计日志追踪**：订单状态流转均已接入 `order_status_log` 审计，支持记录操作人与系统自动触发的行为，便于排查客诉。

### 2. 后台管理列表的服务端高级过滤
* **全量条件动态下推**：重构了 `UserController.manageList` 接口，增加了 `role`、`memberLevelId`、`status` 等筛选项。
* **MyBatis-Plus 动态 SQL**：在 `UserServiceImpl` 中，抛弃了原本低效的前端本地 `Array.filter`，全面使用 `QueryWrapper` 动态拼接 SQL。解决了分页时因局部过滤导致的数据空白 Bug，保障后台检索的精准性。

<div align="center">
<b>🐾 PetShop Backend · Built with ❤️ by the Team</b>
</div>
