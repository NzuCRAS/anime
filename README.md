# Anime - Blue Archive Community Backend 一个ACGN向的个人收藏网站

<div align="center">

![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.6-brightgreen.svg)
![MySQL](https://img.shields.io/badge/Database-MySQL-blue.svg)
![Redis](https://img.shields.io/badge/Cache-Redis-red.svg)
![MinIO](https://img.shields.io/badge/Storage-MinIO-pink.svg)

后端服务代码仓库，为**Blue Archive**风格的Momotalk社区提供核心API支持。
包含用户认证、聊天、动态发布、文件存储等功能。

[📖 详细接口文档](doc/API文档_chat&video.md) • [🧱 架构设计](doc/架构图.md) • [⚡ 快速部署](#-setup--deployment)

</div>

---

## 📂 项目结构

```text
anime/
├── doc/                 # 项目文档 (架构图, API说明, 补丁文档)
├── docker/              # Docker 组件挂载目录 (MinIO data等)
├── src/
│   ├── main/java/       # 核心业务代码
│   └── main/resources/  # 配置文件 (application.yml, keystore)
├── anime-develop.sql    # 数据库初始化脚本
├── docker-compose.yml   # 基础设施编排 (Redis, MinIO)
└── pom.xml              # Maven 依赖管理
```

## 🛠️ Stack & Dependencies

- **核心框架**: Spring Boot 3.3.6
- **数据库**: MySQL 8.0+
- **缓存**: Redis 7.2
- **对象存储**: MinIO
- **安全认证**: JWT + Spring Security (支持 Refresh Token 旋转 & Cookie 安全策略)

## ⚡ Setup & Deployment

### 1. 基础设施启动
使用 Docker Compose 快速启动 Redis 和 MinIO 服务：

```bash
docker-compose up -d
```
> **注意**: 请检查 `docker-compose.yml` 中的 Volume 路径是否适合您的本地环境 (当前配置为 `C://Code//JAVA//minIo` 等)。

### 2. 数据库初始化
在您的 MySQL 数据库中执行以下脚本以初始化表结构：
- `anime-develop.sql`

### 3. 应用启动
推荐使用 Maven Wrapper 启动项目：
```bash
./mvnw spring-boot:run
```

## 🔐 HTTPS Configuration (Local)

本项目强制依赖 HTTPS 环境以支持 `Secure` 和 `HttpOnly` Cookie（用于 Refresh Token）。请按照以下步骤在本地配置受信任的 SSL 证书。

### 前置准备
确保已安装 [Chocolatey](https://chocolatey.org/) (Windows)。

### 步骤详解

#### 1. 安装工具链
以管理员身份运行 PowerShell：
```powershell
# 安装 mkcert, openssl
choco install mkcert openssl.light -y
```

#### 2. 生成本地受信任证书 (CA)
```powershell
mkcert -install
```

#### 3. 签发证书与私钥
创建存储目录并生成证书：
```powershell
mkdir C:\dev\certs; cd C:\dev\certs
mkcert localhost 127.0.0.1 ::1
```
你将获得 `localhost+2.pem` (证书) 和 `localhost+2-key.pem` (私钥)。

#### 4. 打包为 PKCS#12 (Keystore)
将 PEM 转换为 Spring Boot 可用的 `.p12` 格式：
```powershell
# 获取 mkcert CA 路径
$caroot = & mkcert -CAROOT

# 打包命令 (密码设为: changeit)
openssl pkcs12 -export \
  -in localhost+2.pem \
  -inkey localhost+2-key.pem \
  -out keystore.p12 \
  -name tomcat \
  -CAfile "$caroot\rootCA.pem" \
  -caname root \
  -passout pass:changeit
```

#### 5. 配置项目
将生成的 `keystore.p12` 放入 `src/main/resources/keystore/` 目录。
确保 `application-dev.yml` 配置如下：
```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: classpath:keystore/keystore.p12
    key-store-password: changeit
    key-store-type: PKCS12
    key-alias: tomcat
```

现在访问 `https://localhost:8443` 将显示安全锁标志 🔒。

## 📖 API Documentation

项目集成了 Swagger UI，启动后可直接访问可视化接口文档。

- **Swagger UI**: [https://localhost:8443/swagger-ui/index.html](https://localhost:8443/swagger-ui/index.html)

### 开发规范 (DTO & Annotations)

- **@Tag**: Controller 分组
- **@Operation**: 接口描述 (Summary, Description)
- **@Schema**: DTO 字段说明

**Example:**
```java
@Operation(summary = "用户登录")
public ResponseEntity<?> login(@RequestBody LoginDTO req) { ... }
```

## 👥 Contributors

- **Backend Reference**: [NzuCRAS/anime](https://github.com/NzuCRAS/anime)
- **Frontend Reference**: [a2Melody/blue_archive](https://github.com/a2Melody/blue_archive)

---
*Created for the Blue Archive Community Project.*
