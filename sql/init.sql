-- ============================================================
-- 宠物商店 数据库全量建表脚本（已并入审核修复 1~9）
-- Docker 首次启动自动执行；也可在 Navicat/DataGrip 手动运行。
-- 约定：业务表统一 id(BIGINT,雪花) + create_time + update_time + deleted(逻辑删除)
--       与 Java 端 BaseEntity 一致；不使用物理外键，只建索引。
--       例外：cart_item / favorite 用“物理删除”，故无 deleted 列（见下方说明）。
--
-- 【规则说明（写进设计文档）】
--  · 订单拆单(修复1)：一个订单只属于一个商店。购物车跨多店时，结算按 shop_id
--                     分组，拆成多张订单，每张订单的 order_item 同属一个 shop。
--  · 价格来源(修复5)：无规格商品取 product.price/stock；有规格商品取 product_sku.price/stock。
--                     下单一律以选中的 sku 为准（无规格时 sku_id=0，用 product）。
--  · 用户名复用(修复6)：user.username 唯一。逻辑删除后用户名不再复用；
--                     注册查重以 deleted=0 为准（历史删除行保留，不允许撞名）。
-- 表归属：A=shop/product  B=user/会员/支付  C=订单  D=video/message  E=推荐/行为
-- ============================================================

CREATE DATABASE IF NOT EXISTS petshop
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE petshop;

-- ============================================================
-- 框架自检演示表（验证 BaseEntity/自动填充/逻辑删除，可删）
-- ============================================================
CREATE TABLE IF NOT EXISTS demo_item (
    id          BIGINT       NOT NULL COMMENT '主键',
    name        VARCHAR(64)  NOT NULL COMMENT '名称',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_time DATETIME     NULL COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '框架自检演示表';


-- ============================================================
-- A 模块：商店 & 商品
-- ============================================================

-- 商店
CREATE TABLE IF NOT EXISTS shop (
    id          BIGINT        NOT NULL COMMENT '主键',
    name        VARCHAR(100)  NOT NULL COMMENT '商店名称',
    description VARCHAR(500)  NULL COMMENT '商店简介',
    phone       VARCHAR(20)   NULL COMMENT '联系电话',
    province    VARCHAR(50)   NULL COMMENT '省',
    city        VARCHAR(50)   NULL COMMENT '市',
    district    VARCHAR(50)   NULL COMMENT '区/县',
    address     VARCHAR(255)  NULL COMMENT '详细地址',
    longitude   DECIMAL(10,6) NULL COMMENT '经度（地图找附近用）',
    latitude    DECIMAL(10,6) NULL COMMENT '纬度（地图找附近用）',
    logo        VARCHAR(255)  NULL COMMENT '商店logo',
    owner_id    BIGINT        NULL COMMENT '店主用户id',
    status      TINYINT       NOT NULL DEFAULT 1 COMMENT '1营业 0停业',
    create_time DATETIME      NULL,
    update_time DATETIME      NULL,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_owner (owner_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商店';

-- 商品分类
CREATE TABLE IF NOT EXISTS product_category (
    id          BIGINT       NOT NULL COMMENT '主键',
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父分类id 0=顶级',
    name        VARCHAR(50)  NOT NULL COMMENT '分类名',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    icon        VARCHAR(255) NULL COMMENT '图标',
    create_time DATETIME     NULL,
    update_time DATETIME     NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品分类';

-- 商品（价格来源见顶部规则：有规格以 product_sku 为准）
CREATE TABLE IF NOT EXISTS product (
    id             BIGINT        NOT NULL COMMENT '主键',
    shop_id        BIGINT        NOT NULL COMMENT '所属商店id',
    category_id    BIGINT        NULL COMMENT '分类id',
    name           VARCHAR(100)  NOT NULL COMMENT '商品名称',
    type           TINYINT       NOT NULL DEFAULT 2 COMMENT '1宠物(唯一,库存=1) 2周边(数量不限)',
    description    TEXT          NULL COMMENT '商品详情',
    price          DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '售价(无规格时用)',
    original_price DECIMAL(10,2) NULL COMMENT '原价',
    stock          INT           NOT NULL DEFAULT 0 COMMENT '库存(无规格时用;宠物=1)',
    sales          INT           NOT NULL DEFAULT 0 COMMENT '销量',
    main_image     VARCHAR(255)  NULL COMMENT '主图',
    images         TEXT          NULL COMMENT '多图(JSON数组)',
    status         TINYINT       NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
    create_time    DATETIME      NULL,
    update_time    DATETIME      NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_shop (shop_id),
    KEY idx_category (category_id),
    KEY idx_type (type),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品';

-- 商品规格(SKU)：有规格商品以本表 price/stock 为准
CREATE TABLE IF NOT EXISTS product_sku (
    id          BIGINT        NOT NULL COMMENT '主键',
    product_id  BIGINT        NOT NULL COMMENT '商品id',
    spec_name   VARCHAR(100)  NOT NULL COMMENT '规格描述 如"颜色:红;尺寸:L"',
    price       DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '该规格价格',
    stock       INT           NOT NULL DEFAULT 0 COMMENT '该规格库存',
    image       VARCHAR(255)  NULL COMMENT '规格图',
    create_time DATETIME      NULL,
    update_time DATETIME      NULL,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品规格SKU';


-- ============================================================
-- B 模块：用户 / 地址 / 会员 / 支付 / AI / 第三方登录
-- ============================================================

-- 会员等级（含折扣，支撑"会员动态价格"加分项）
CREATE TABLE IF NOT EXISTS membership_level (
    id          BIGINT       NOT NULL COMMENT '主键',
    level       INT          NOT NULL COMMENT '等级序号 越大越高',
    name        VARCHAR(50)  NOT NULL COMMENT '等级名 如 普通/银卡/金卡',
    discount    DECIMAL(3,2) NOT NULL DEFAULT 1.00 COMMENT '折扣 1=无折扣 0.90=9折',
    threshold   INT          NOT NULL DEFAULT 0 COMMENT '升级所需积分',
    icon        VARCHAR(255) NULL COMMENT '等级图标',
    description VARCHAR(255) NULL COMMENT '权益说明',
    create_time DATETIME     NULL,
    update_time DATETIME     NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会员等级';

-- 用户（修复3：增加 balance 余额；修复6：username 唯一+不复用）
CREATE TABLE IF NOT EXISTS user (
    id              BIGINT        NOT NULL COMMENT '主键',
    username        VARCHAR(50)   NOT NULL COMMENT '用户名(唯一,逻辑删除后不复用)',
    password        VARCHAR(100)  NOT NULL COMMENT '密码(BCrypt加密)',
    nickname        VARCHAR(50)   NULL COMMENT '昵称',
    avatar          VARCHAR(255)  NULL COMMENT '头像',
    phone           VARCHAR(20)   NULL COMMENT '手机号',
    email           VARCHAR(100)  NULL COMMENT '邮箱',
    gender          TINYINT       NOT NULL DEFAULT 0 COMMENT '0未知 1男 2女',
    role            VARCHAR(20)   NOT NULL DEFAULT 'USER' COMMENT 'USER/ADMIN/MERCHANT',
    member_level_id BIGINT        NOT NULL DEFAULT 0 COMMENT '会员等级id 0=游客/非会员',
    balance         DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '账户余额(余额支付用)',
    points          INT           NOT NULL DEFAULT 0 COMMENT '积分',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
    last_login_time DATETIME      NULL COMMENT '最后登录时间',
    create_time     DATETIME      NULL,
    update_time     DATETIME      NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户';

-- 收货地址（多个，含默认；单一默认由代码保证）
CREATE TABLE IF NOT EXISTS address (
    id          BIGINT       NOT NULL COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '用户id',
    receiver    VARCHAR(50)  NOT NULL COMMENT '收货人',
    phone       VARCHAR(20)  NOT NULL COMMENT '收货电话',
    province    VARCHAR(50)  NULL COMMENT '省',
    city        VARCHAR(50)  NULL COMMENT '市',
    district    VARCHAR(50)  NULL COMMENT '区/县',
    detail      VARCHAR(255) NOT NULL COMMENT '详细地址',
    is_default  TINYINT      NOT NULL DEFAULT 0 COMMENT '1默认 0非默认',
    create_time DATETIME     NULL,
    update_time DATETIME     NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收货地址';

-- 支付流水（修复3）
CREATE TABLE IF NOT EXISTS payment (
    id          BIGINT        NOT NULL COMMENT '主键',
    payment_no  VARCHAR(32)   NOT NULL COMMENT '支付流水号',
    order_id    BIGINT        NOT NULL COMMENT '订单id',
    user_id     BIGINT        NOT NULL COMMENT '用户id',
    amount      DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '支付金额',
    pay_type    TINYINT       NOT NULL DEFAULT 2 COMMENT '1余额 2模拟支付',
    status      TINYINT       NOT NULL DEFAULT 0 COMMENT '0待支付 1成功 2失败',
    pay_time    DATETIME      NULL COMMENT '支付时间',
    create_time DATETIME      NULL,
    update_time DATETIME      NULL,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no (payment_no),
    KEY idx_order (order_id),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '支付流水';

-- AI 问答记录
CREATE TABLE IF NOT EXISTS ai_chat_log (
    id          BIGINT        NOT NULL COMMENT '主键',
    user_id     BIGINT        NULL COMMENT '用户id',
    session_id  VARCHAR(64)   NULL COMMENT '会话id',
    question    VARCHAR(1000) NOT NULL COMMENT '提问',
    answer      TEXT          NULL COMMENT '回答',
    create_time DATETIME      NULL,
    update_time DATETIME      NULL,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI问答记录';

-- 第三方登录绑定（选做）
CREATE TABLE IF NOT EXISTS user_oauth (
    id          BIGINT       NOT NULL COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '用户id',
    provider    VARCHAR(20)  NOT NULL COMMENT 'wechat/qq/...',
    open_id     VARCHAR(100) NOT NULL COMMENT '第三方openid',
    union_id    VARCHAR(100) NULL COMMENT 'unionid',
    create_time DATETIME     NULL,
    update_time DATETIME     NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user (user_id),
    KEY idx_openid (provider, open_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第三方登录绑定';


-- ============================================================
-- C 模块：购物车 / 订单 / 退单 / 评价 / 优惠券
-- ============================================================

-- 购物车项（修复2：物理删除，无 deleted；唯一键防重复加车）
-- 注意：CartItem 实体不要带 deleted 字段，让 MyBatis-Plus 走物理删除。
CREATE TABLE IF NOT EXISTS cart_item (
    id          BIGINT  NOT NULL COMMENT '主键',
    user_id     BIGINT  NOT NULL COMMENT '用户id',
    product_id  BIGINT  NOT NULL COMMENT '商品id',
    sku_id      BIGINT  NOT NULL DEFAULT 0 COMMENT '规格id 0=无规格',
    quantity    INT     NOT NULL DEFAULT 1 COMMENT '数量',
    selected    TINYINT NOT NULL DEFAULT 1 COMMENT '是否勾选结算 1是 0否',
    create_time DATETIME NULL,
    update_time DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_product_sku (user_id, product_id, sku_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '购物车项(物理删除)';

-- 订单（修复1：一单一店,跨店拆单；修复7：prev_status 退款拒绝后恢复）
-- 表名用 orders，order 是 MySQL 保留字；实体加 @TableName("orders")
CREATE TABLE IF NOT EXISTS orders (
    id               BIGINT        NOT NULL COMMENT '主键',
    order_no         VARCHAR(32)   NOT NULL COMMENT '订单号',
    user_id          BIGINT        NOT NULL COMMENT '下单用户id',
    shop_id          BIGINT        NOT NULL COMMENT '商店id(一个订单只属于一个商店)',
    total_amount     DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '商品总额',
    discount_amount  DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '优惠金额(会员折扣+券)',
    pay_amount       DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '实付金额',
    coupon_id        BIGINT        NOT NULL DEFAULT 0 COMMENT '使用的券id 0=未用',
    status           TINYINT       NOT NULL DEFAULT 0 COMMENT '状态机: 0待支付 1待发货 2待收货 3待评价 4已完成 -1已取消 -2退款申请中 -3已退款 -4管理员退款',
    prev_status      TINYINT       NULL COMMENT '申请退款前的状态(用于退款被拒后恢复)',
    pay_type         TINYINT       NULL COMMENT '支付方式 1余额 2模拟支付',
    pay_time         DATETIME      NULL COMMENT '支付时间',
    ship_time        DATETIME      NULL COMMENT '发货时间',
    receive_time     DATETIME      NULL COMMENT '收货时间',
    finish_time      DATETIME      NULL COMMENT '完成时间',
    cancel_reason    VARCHAR(255)  NULL COMMENT '取消原因',
    receiver_name    VARCHAR(50)   NULL COMMENT '收货人(快照)',
    receiver_phone   VARCHAR(20)   NULL COMMENT '收货电话(快照)',
    receiver_address VARCHAR(255)  NULL COMMENT '收货地址(快照)',
    remark           VARCHAR(255)  NULL COMMENT '备注',
    create_time      DATETIME      NULL,
    update_time      DATETIME      NULL,
    deleted          TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user (user_id),
    KEY idx_shop (shop_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单';

-- 订单明细
CREATE TABLE IF NOT EXISTS order_item (
    id            BIGINT        NOT NULL COMMENT '主键',
    order_id      BIGINT        NOT NULL COMMENT '订单id',
    product_id    BIGINT        NOT NULL COMMENT '商品id',
    sku_id        BIGINT        NOT NULL DEFAULT 0 COMMENT '规格id',
    shop_id       BIGINT        NULL COMMENT '商店id(与订单同店)',
    product_name  VARCHAR(100)  NULL COMMENT '商品名(快照)',
    product_image VARCHAR(255)  NULL COMMENT '商品图(快照)',
    spec          VARCHAR(100)  NULL COMMENT '规格(快照)',
    price         DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '单价(快照)',
    quantity      INT           NOT NULL DEFAULT 1 COMMENT '数量',
    subtotal      DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '小计',
    create_time   DATETIME      NULL,
    update_time   DATETIME      NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_order (order_id),
    KEY idx_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单明细';

-- 订单状态流转记录（状态机审计）
CREATE TABLE IF NOT EXISTS order_status_log (
    id            BIGINT       NOT NULL COMMENT '主键',
    order_id      BIGINT       NOT NULL COMMENT '订单id',
    from_status   TINYINT      NULL COMMENT '原状态',
    to_status     TINYINT      NOT NULL COMMENT '新状态',
    operator_id   BIGINT       NULL COMMENT '操作人id',
    operator_role VARCHAR(20)  NULL COMMENT '操作人角色 USER/ADMIN',
    remark        VARCHAR(255) NULL COMMENT '备注/原因',
    create_time   DATETIME     NULL,
    update_time   DATETIME     NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单状态流转记录';

-- 退单
CREATE TABLE IF NOT EXISTS refund (
    id            BIGINT        NOT NULL COMMENT '主键',
    refund_no     VARCHAR(32)   NOT NULL COMMENT '退单号',
    order_id      BIGINT        NOT NULL COMMENT '订单id',
    user_id       BIGINT        NOT NULL COMMENT '用户id',
    amount        DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '退款金额',
    reason        VARCHAR(255)  NULL COMMENT '退单理由',
    type          TINYINT       NOT NULL DEFAULT 1 COMMENT '1用户申请 2管理员直接退',
    status        TINYINT       NOT NULL DEFAULT 0 COMMENT '0申请中 1审核通过(已退) 2审核拒绝',
    audit_user_id BIGINT        NULL COMMENT '审核管理员id',
    audit_time    DATETIME      NULL COMMENT '审核时间',
    audit_remark  VARCHAR(255)  NULL COMMENT '审核备注',
    create_time   DATETIME      NULL,
    update_time   DATETIME      NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_no (refund_no),
    KEY idx_order (order_id),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '退单';

-- 评价（修复9：一个订单明细只能评一次）
CREATE TABLE IF NOT EXISTS review (
    id            BIGINT        NOT NULL COMMENT '主键',
    order_id      BIGINT        NOT NULL COMMENT '订单id',
    order_item_id BIGINT        NOT NULL COMMENT '订单明细id',
    user_id       BIGINT        NOT NULL COMMENT '用户id',
    product_id    BIGINT        NOT NULL COMMENT '商品id',
    shop_id       BIGINT        NULL COMMENT '商店id',
    rating        TINYINT       NOT NULL DEFAULT 5 COMMENT '评分 1-5',
    content       VARCHAR(1000) NULL COMMENT '评价内容',
    images        TEXT          NULL COMMENT '评价图(JSON)',
    reply         VARCHAR(500)  NULL COMMENT '商家回复',
    create_time   DATETIME      NULL,
    update_time   DATETIME      NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item (order_item_id),
    KEY idx_order (order_id),
    KEY idx_product (product_id),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '评价';

-- 优惠券（加分项）
CREATE TABLE IF NOT EXISTS coupon (
    id          BIGINT        NOT NULL COMMENT '主键',
    name        VARCHAR(50)   NOT NULL COMMENT '券名',
    type        TINYINT       NOT NULL DEFAULT 1 COMMENT '1满减 2折扣',
    threshold   DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '满X元可用',
    amount      DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '减Y元 或 折扣(0.9=9折)',
    total       INT           NOT NULL DEFAULT 0 COMMENT '发行总量',
    remain      INT           NOT NULL DEFAULT 0 COMMENT '剩余数量',
    start_time  DATETIME      NULL COMMENT '生效时间',
    end_time    DATETIME      NULL COMMENT '失效时间',
    status      TINYINT       NOT NULL DEFAULT 1 COMMENT '1有效 0停用',
    create_time DATETIME      NULL,
    update_time DATETIME      NULL,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '优惠券';

-- 用户领取的优惠券
CREATE TABLE IF NOT EXISTS user_coupon (
    id          BIGINT   NOT NULL COMMENT '主键',
    user_id     BIGINT   NOT NULL COMMENT '用户id',
    coupon_id   BIGINT   NOT NULL COMMENT '券id',
    status      TINYINT  NOT NULL DEFAULT 0 COMMENT '0未使用 1已使用 2已过期',
    used_time   DATETIME NULL COMMENT '使用时间',
    order_id    BIGINT   NOT NULL DEFAULT 0 COMMENT '使用的订单id',
    create_time DATETIME NULL,
    update_time DATETIME NULL,
    deleted     TINYINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户优惠券';


-- ============================================================
-- D 模块：视频 / 消息（修复4：消息内容与"每用户已读"分表）
-- ============================================================

-- 视频
CREATE TABLE IF NOT EXISTS video (
    id          BIGINT       NOT NULL COMMENT '主键',
    title       VARCHAR(100) NOT NULL COMMENT '标题',
    cover       VARCHAR(255) NULL COMMENT '封面',
    url         VARCHAR(255) NOT NULL COMMENT '视频地址',
    description VARCHAR(500) NULL COMMENT '简介',
    product_id  BIGINT       NOT NULL DEFAULT 0 COMMENT '关联商品id 0=无',
    shop_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '关联商店id',
    views       INT          NOT NULL DEFAULT 0 COMMENT '播放量',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
    create_time DATETIME     NULL,
    update_time DATETIME     NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '宠物视频';

-- 消息内容（修复4：is_read 移到 user_message；scope 区分广播/定向）
CREATE TABLE IF NOT EXISTS message (
    id          BIGINT        NOT NULL COMMENT '主键',
    title       VARCHAR(100)  NOT NULL COMMENT '标题',
    content     VARCHAR(1000) NULL COMMENT '内容',
    type        TINYINT       NOT NULL DEFAULT 1 COMMENT '1系统 2订单 3活动 4宠物资讯',
    scope       TINYINT       NOT NULL DEFAULT 1 COMMENT '1全体广播 2定向',
    create_time DATETIME      NULL,
    update_time DATETIME      NULL,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '消息内容';

-- 用户消息已读状态（修复4：每个用户对每条消息独立已读）
-- 定向消息：发送时为目标用户插入一行；广播消息：用户首次读取时插入一行。
CREATE TABLE IF NOT EXISTS user_message (
    id          BIGINT   NOT NULL COMMENT '主键',
    message_id  BIGINT   NOT NULL COMMENT '消息id',
    user_id     BIGINT   NOT NULL COMMENT '用户id',
    is_read     TINYINT  NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    read_time   DATETIME NULL COMMENT '阅读时间',
    create_time DATETIME NULL,
    update_time DATETIME NULL,
    deleted     TINYINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_user (message_id, user_id),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户消息已读状态';


-- ============================================================
-- E 模块：用户行为 / 收藏（协同推荐 / 统计可视化用）
-- ============================================================

-- 用户行为日志（只追加，喂推荐/统计）
CREATE TABLE IF NOT EXISTS user_behavior (
    id            BIGINT  NOT NULL COMMENT '主键',
    user_id       BIGINT  NOT NULL COMMENT '用户id',
    product_id    BIGINT  NOT NULL COMMENT '商品id',
    behavior_type TINYINT NOT NULL COMMENT '1浏览 2收藏 3加购 4购买',
    create_time   DATETIME NULL,
    update_time   DATETIME NULL,
    deleted       TINYINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user (user_id),
    KEY idx_product (product_id),
    KEY idx_behavior (behavior_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户行为记录';

-- 收藏（修复8：单独表,物理删除,便于查"是否已收藏"和取消收藏）
-- 注意：Favorite 实体不要带 deleted 字段，走物理删除。
CREATE TABLE IF NOT EXISTS favorite (
    id          BIGINT   NOT NULL COMMENT '主键',
    user_id     BIGINT   NOT NULL COMMENT '用户id',
    product_id  BIGINT   NOT NULL COMMENT '商品id',
    create_time DATETIME NULL,
    update_time DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_product (user_id, product_id),
    KEY idx_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品收藏(物理删除)';


-- ----- 协同过滤推荐（基于用户的 User-based CF）-----
-- 下面 3 张为"派生表"，由定时任务从 user_behavior 重算，可直接 TRUNCATE 重建，故无 deleted。

-- 用户-商品 隐式评分矩阵（行为加权：浏览1 收藏3 加购4 购买5）
CREATE TABLE IF NOT EXISTS user_item_score (
    id          BIGINT       NOT NULL COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '用户id',
    product_id  BIGINT       NOT NULL COMMENT '商品id',
    score       DECIMAL(8,4) NOT NULL DEFAULT 0 COMMENT '隐式评分=行为加权',
    update_time DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_product (user_id, product_id),
    KEY idx_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户商品评分矩阵';

-- 用户相似度（user-based CF 核心：每个用户只存 Top-K 个最相似邻居）
CREATE TABLE IF NOT EXISTS user_similarity (
    id          BIGINT       NOT NULL COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '目标用户',
    sim_user_id BIGINT       NOT NULL COMMENT '相似用户',
    similarity  DECIMAL(8,6) NOT NULL DEFAULT 0 COMMENT '相似度 0~1',
    update_time DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_sim (user_id, sim_user_id),
    KEY idx_user_sim (user_id, similarity)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户相似度';

-- 推荐结果（离线算好、在线直接查）
CREATE TABLE IF NOT EXISTS recommend_result (
    id          BIGINT        NOT NULL COMMENT '主键',
    user_id     BIGINT        NOT NULL COMMENT '用户id',
    product_id  BIGINT        NOT NULL COMMENT '推荐商品id',
    score       DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '推荐得分',
    source      VARCHAR(20)   NOT NULL DEFAULT 'UCF' COMMENT '算法来源 UCF=基于用户',
    create_time DATETIME      NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_product (user_id, product_id),
    KEY idx_user_score (user_id, score)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '推荐结果';


-- ============================================================
-- 初始化种子数据（密码均为 123456 的 BCrypt 值）
-- ============================================================
INSERT IGNORE INTO membership_level (id, level, name, discount, threshold, description, create_time, update_time, deleted) VALUES
 (1, 1, '普通会员', 1.00, 0,    '无折扣',  NOW(), NOW(), 0),
 (2, 2, '银卡会员', 0.95, 1000, '95折',    NOW(), NOW(), 0),
 (3, 3, '金卡会员', 0.90, 5000, '9折',     NOW(), NOW(), 0);

INSERT IGNORE INTO product_category (id, parent_id, name, sort, create_time, update_time, deleted) VALUES
 (1, 0, '宠物',     1, NOW(), NOW(), 0),
 (2, 0, '宠物食品', 2, NOW(), NOW(), 0),
 (3, 0, '宠物玩具', 3, NOW(), NOW(), 0),
 (4, 0, '宠物用品', 4, NOW(), NOW(), 0);

INSERT IGNORE INTO user (id, username, password, nickname, role, member_level_id, status, create_time, update_time, deleted) VALUES
 (1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '管理员', 'ADMIN', 0, 1, NOW(), NOW(), 0),
 (2, 'test',  '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '测试用户', 'USER', 1, 1, NOW(), NOW(), 0);
