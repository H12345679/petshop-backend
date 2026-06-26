# 🐾 PetShop Backend — 宠物商店后端

> **最后更新**：2026-06-25 &nbsp;|&nbsp; **当前版本**：v1.0.0 &nbsp;|&nbsp; **服务端口**：8088
> **仓库地址**：Gitee 私有仓库 &nbsp;|&nbsp; **接口文档**：http://localhost:8088/doc.html

---

<!-- ====================================================================== -->

<!-- AI-CONTEXT: 项目结构化元数据（供 AI / Copilot / ChatGPT 快速理解项目）     -->

<!-- ====================================================================== -->

<!--
AI_PROJECT_METADATA:
  project: petshop-backend
  type: Spring Boot monolith (团队实训项目)
  language: Java 17
  framework: Spring Boot 2.7.18 + MyBatis-Plus 3.5.5
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

## 🏪 项目简介

**PetShop** 是一个全功能宠物商店电商平台后端，采用 **Spring Boot 单体架构**，面向"程序设计实训"课程需求开发。项目支持多角色（普通用户 / 商家 / 管理员），涵盖商品管理、订单交易、AI 智能客服、视频内容、会员体系等完整电商业务链路。

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
docker compose up -d          # 启动全部中间件(MySQL/Redis/RabbitMQ/ES)
docker compose ps             # 等所有服务状态变 healthy
docker compose logs -f mysql  # 查看 MySQL 日志（首次建表+导数据）
```

| 中间件        | 地址               | 账号/密码     | 说明                                         |
| ------------- | ------------------ | ------------- | -------------------------------------------- |
| MySQL         | `localhost:3307` | root / root   | 库`petshop`，首次自动执行 `sql/init.sql` |
| Redis         | `localhost:6379` | 无            | AOF 持久化                                   |
| RabbitMQ      | `localhost:5672` | guest / guest | 管理界面 http://localhost:15672              |
| Elasticsearch | `localhost:9200` | 无            | 已关闭鉴权，512MB 堆                         |

> **内存不够？** 可只起核心服务：`docker compose up -d mysql redis`

### Step 2：配置七牛云与 AI（可选）

创建 `src/main/resources/application-local.yml`（已加入 `.gitignore`）：

```yaml
qiniu:
  access-key: 你的AccessKey
  secret-key: 你的SecretKey
  bucket: 你的Bucket名
  domain: http://你的CDN域名
ai:
  provider: deepseek          # mock(默认) 或 deepseek
  deepseek:
    api-key: sk-xxx           # 你的 DeepSeek API Key
```

### Step 3：运行项目

```bash
mvn spring-boot:run
```

访问 http://localhost:8088/doc.html 查阅接口文档。

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
| **首页复杂展示规则** | A (黄舰) | 推荐与营销(优惠券)输出就绪 | 替换原本简单的 HOT/NEW 为智能综合排序      |

<div align="center">
<b>🐾 PetShop Backend — Built with ❤️ by the Team</b>
</div>
