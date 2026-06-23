# petshop-backend（公共框架 A2~A6）

宠物商店项目后端公共框架。A 搭好后，B/C/D 在此基础上加各自模块。

## 技术栈

Spring Boot 2.7.18 · JDK 17 · MyBatis-Plus 3.5.5 · Druid · MySQL 8 · Redis · JWT(jjwt) · Knife4j

## 已实现（对应计划 A2~A6）

| 步骤 | 内容 | 位置 |
|---|---|---|
| A2 | SpringBoot 工程 + 分包 + dev/prod 多环境 | `PetShopApplication`、`application*.yml` |
| A3 | MySQL + MyBatis-Plus + Druid + BaseEntity（自动填充/逻辑删除） | `config/MybatisPlusConfig`、`MyMetaObjectHandler`、`common/BaseEntity` |
| A4 | 统一返回体 + 全局异常 + 统一分页 | `common/Result`、`ResultCode`、`GlobalExceptionHandler`、`PageResult` |
| A5 | JWT 签发/校验 + 拦截器 + UserContext + 权限注解 | `security/*`（`JwtUtil`、`JwtInterceptor`、`UserContext`、`@RequireLogin`、`@RequireRole`） |
| A6 | Redis + CORS + Knife4j + 统一日志 | `config/RedisConfig`、`CorsConfig`、`Knife4jConfig`、`util/RedisUtil`、`logback-spring.xml` |

## 中间件（Docker 一键启动，推荐）

全部中间件用 `docker-compose.yml` 打包，**MySQL 宿主机端口是 3307**（避开本机已有的 3306）。

```bash
# 在 petshop-backend 目录下
docker compose up -d          # 启动全部中间件
docker compose ps             # 查看状态（等 healthy）
docker compose logs -f mysql  # 看某个服务日志
docker compose down           # 停止（数据保留在卷里）
docker compose down -v        # 彻底重置（删数据，下次启动重跑 sql/init.sql）
```

| 中间件 | 连接地址 | 账号/密码 | 备注 |
|---|---|---|---|
| MySQL | `localhost:3307` | root / root | 库 `petshop`，首次启动自动跑 `sql/init.sql` |
| Redis | `localhost:6379` | 无 | 开启 AOF 持久化 |
| RabbitMQ | `localhost:5672` | guest / guest | 管理界面 <http://localhost:15672> |
| Elasticsearch | `localhost:9200` | 无（关了鉴权） | 版本 7.17，匹配 SB2.7 |

- 以上已和 `application-dev.yml` 对好，无需再改。
- ES 较吃内存（默认 512MB 堆），机器内存紧张可调小 `docker-compose.yml` 里的 `ES_JAVA_OPTS`，或临时只起部分服务：`docker compose up -d mysql redis`。

> 想换端口：改 `docker-compose.yml` 里 `"3307:3306"` 左边的数字，并同步改 `application-dev.yml` 的 url。

## 热启动（devtools）

已引入 `spring-boot-devtools`。在 IDEA 里改完代码按 **Ctrl+F9**（重新编译）即自动重启应用，无需手动停启。
打包上线时 devtools 自动失效，不影响生产。

## 本地启动

1. 装好 JDK 17（用 IDEA 打开则无需单独装 Maven，IDEA 会自动构建）。
2. 起中间件：`docker compose up -d`（或自备 MySQL/Redis，注意端口对应 3307）。
3. 运行 `PetShopApplication`（或 `mvn spring-boot:run`）。
4. 打开接口文档：<http://localhost:8080/doc.html>

## 框架自检（验证 A2~A6 跑通）

用接口文档 `/doc.html` 或下面的 curl：

```bash
# 1) 公开接口
curl http://localhost:8080/api/demo/ping
# -> {"code":200,"message":"操作成功","data":"pong"}

# 2) 演示登录，拿 token（role 传 USER 或 ADMIN）
curl -X POST "http://localhost:8080/api/demo/login?username=tom&role=ADMIN"

# 3) 带 token 访问需登录接口（把 <token> 换成上一步返回的）
curl http://localhost:8080/api/demo/me -H "Authorization: Bearer <token>"

# 4) 不带 token 访问 -> 返回 401；role=USER 访问 /admin -> 返回 403
curl http://localhost:8080/api/demo/admin -H "Authorization: Bearer <token>"
```

全部符合预期，即 A2~A6 框架就绪，B/C/D/E 可开工。**B 完成正式登录后删除 `DemoController`。**

## 各模块怎么接入

- 实体 `extends BaseEntity`，放 `com.petshop.<模块>.entity`
- Mapper 接口 `extends BaseMapper<T>`，放 `com.petshop.<模块>.mapper`（已配置 @MapperScan）
- 接口需登录加 `@RequireLogin`，需管理员加 `@RequireRole({"ADMIN"})`
- 取当前登录人：`UserContext.getUserId()` / `UserContext.getRole()`
- 统一返回：`return Result.success(data);`
- 抛业务错误：`throw new BusinessException("库存不足");`
- 分页：查询返回 `PageResult.of(page)`

## A1 —— GitHub 操作清单（A 本人在自己账号执行）

```bash
# 在 petshop-backend 目录初始化并推送
git init
git add .
git commit -m "chore: 初始化后端公共框架 A2~A6"
git branch -M main
git remote add origin https://github.com/<你的组织或用户名>/petshop.git
git push -u origin main
# 建集成分支
git checkout -b dev
git push -u origin dev
```

然后在 GitHub 网页上：
1. **分支保护**：Settings → Branches → Add rule，对 `main` 勾选 "Require a pull request before merging"。
2. **看板**：Projects → New project（Board 模板），建 `To do / In progress / Done` 三列。
3. **里程碑**：Issues → Milestones → New，建 "第一周""第二周"。
4. **拉队友**：Settings → Collaborators，加 B/C/D/E。
5. 把第二节的每个步骤（A1…E10）建成 Issue，指派到人、挂里程碑、拖进看板。
