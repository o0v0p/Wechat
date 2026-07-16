# Wechat

基于 Spring Boot 3 的即时通信后端，提供用户账号、好友关系、群聊、消息存储、WebSocket 推送和文件上传能力。

## 项目演示

[点击观看演示视频](docs/demo/petal_20260716_113230.mp4)

## 功能架构

- 用户账号：注册、登录、退出、注销、修改密码、重置密码、个人资料维护、头像上传
- 验证码：基于 Redis 保存验证码和发送频率限制
- 好友关系：用户搜索、好友申请、申请处理、好友列表、好友详情、备注、分组、置顶、免打扰、删除好友
- 群聊管理：创建群聊、群资料维护、邀请成员、申请入群、审核申请、退出群聊、转让群主、解散群聊、成员移除
- 会话消息：单聊/群聊消息发送、历史消息分页、会话列表、未读统计、已读标记
- 消息状态：正常、撤回、引用、发送失败、删除等状态维护
- 实时通信：WebSocket 在线连接管理、心跳检测、单用户推送、群成员广播、状态变更推送
- 文件上传：图片、文件、视频等多媒体资源上传至阿里云 OSS
- 接口文档：集成 Knife4j / OpenAPI，支持接口调试和文档查看

## 技术栈

- Java 17
- Spring Boot 3.2
- MyBatis-Plus / MyBatis XML
- MySQL
- Redis
- Spring WebSocket
- Knife4j / OpenAPI
- Aliyun OSS

## 本地环境

需要先准备：

- JDK 17+
- MySQL 8.x
- Redis 6.x+
- Maven Wrapper 已随项目提供

创建数据库：

```sql
CREATE DATABASE chatdb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

导入表结构和初始化数据：

```bash
mysql -u root -p chatdb < src/main/resources/sql/chatdb.sql
```

## 配置

`src/main/resources/application.yml` 和 `src/main/resources/application-dev.yml` 存放本地配置，已加入 `.gitignore`。

敏感项不要提交到仓库，包括：

- 数据库密码
- Redis 密码
- JWT Secret
- Aliyun OSS AccessKey

建议通过环境变量、启动参数或本地私有配置文件注入。

## 启动

Windows：

```bash
.\mvnw.cmd spring-boot:run
```

打包：

```bash
.\mvnw.cmd clean package
java -jar target/Wechat-0.0.1-SNAPSHOT.jar
```

默认地址：

```text
http://localhost:8080
```

## 接口文档

启动后访问：

```text
http://localhost:8080/doc.html
```

OpenAPI JSON：

```text
http://localhost:8080/v3/api-docs
```

## WebSocket

连接地址：

```text
ws://localhost:8080/ws?token=<token>
```

握手阶段会校验 JWT。登录、修改密码、注销账号后，旧 token 会被 Redis 失效记录拦截。

## 目录说明

```text
src/main/java/org/example/wechat
  common      通用结果、异常、拦截器、工具类
  config      Web、Redis、WebSocket、OpenAPI、线程池配置
  controller  REST 接口
  dao         Mapper 接口
  pojo        DTO、Entity、VO
  service     业务服务

src/main/resources
  mapper      MyBatis XML
  sql         数据库脚本
  static      默认头像等静态资源
```

## 说明

- `common/util/netty` 是备用 Netty WebSocket 实现，当前主流程使用 Spring WebSocket，暂时保留。
- `src/test` 暂未补充自动化测试，后续建议优先覆盖登录、好友申请、群聊消息和 token 失效流程。
