***再见了，所有的Java战士***

我真求你了

---

___关于接口与DTO详情文档Swagger的使用___

__如何使用：__

启动项目后，进入网址 [Swagger控制台](https://localhost:8443/swagger-ui/index.html)

接口文档位于上半侧，DTO文档位于下半侧

__如何编辑注释：__

常用注释：

```
@Tag — 给 Controller 打标签（分组）。
@Operation — 给单个接口写 summary / description / security / responses 等。
@Parameter — 描述方法参数（path / query / header）。
@RequestBody (io.swagger.v3.oas.annotations.parameters.RequestBody) — 为请求体添加说明与示例。
@ApiResponse — 描述响应码及返回模型。
@Schema — 给 POJO / 字段写描述、示例、required、format、枚举等。
@ArraySchema / @Content / @ExampleObject — 处理数组、响应内容和示例。
@Hidden — 隐藏不想显示的 Controller/方法/字段。
```

对接口打注释示例：

```
 @Operation(
        summary = "保存日记（新建或更新）",
        description = "传入 diary 和 blocks 列表；若 diary.id 为空则创建，否则按 version 做乐观锁更新。",
        security = @SecurityRequirement(name = "bearerAuth"),
        responses = {
            @ApiResponse(responseCode = "200", description = "保存成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = com.anime.diary.service.DiaryService.SaveResult.class))),
            @ApiResponse(responseCode = "409", description = "版本冲突"),
            @ApiResponse(responseCode = "401", description = "未认证")
        }
    )
    public ResponseEntity<?> saveDiary(
        @RequestBody(description = "请求体: diary + blocks", required = true,
            content = @Content(schema = @Schema(implementation = com.anime.common.dto.diary.DiarySaveDTO.class)))
        @org.springframework.web.bind.annotation.RequestBody DiarySaveDTO req,
        @CurrentUser Long userId) {
        ...
    }
```

对DTO打注释示例：
```
@Schema(description = "Block DTO - 前端提交的块信息")
public class BlockDTO {
    @Schema(description = "客户端临时 id 或 DB id（更新时）", example = "123", required = false)
    private Long blockId;

    @Schema(description = "类型，例如 text / image / embed", example = "image", required = true)
    private String type;

    @Schema(description = "文本内容（text 类型）", example = "这是内容")
    private String content;

    @Schema(description = "对应 attachments.id（image 类型）", example = "456")
    private Long attachmentId;

    @Schema(description = "位置，后端会重新覆盖", example = "1")
    private Integer position;

    @Schema(description = "metadata JSON 字符串（例如 caption 等）", example = "{\"caption\":\"示例\"}")
    private String metadata;
}
```

---

___关于本地证书以及私钥的获取___

__详细过程：__

# 本地启用 HTTPS（mkcert + PKCS12）详细指南

此文档从安装 Chocolatey 开始，逐步讲解如何在 Windows 本地为 Spring Boot 项目生成受信任的 HTTPS 证书并配置使后端能写入 `Secure` HttpOnly cookie。适合前端/后端同学按步骤执行以便在本地联调 refresh-cookie、CORS、presign 等功能。

> 说明：文档假设你使用 Windows（PowerShell / Git Bash 可选），Spring Boot 项目使用 `server.ssl` 配置支持 PKCS12 keystore。若你使用 macOS / Linux，可略去 Chocolatey 部分，直接安装 mkcert 与 openssl 或使用本系统包管理器。

目录
- 前提
- 一步步操作（含命令）
  - 1. 安装 Chocolatey（可选，仅 Windows 未安装时）
  - 2. 安装 mkcert
  - 3. 安装 OpenSSL（或使用 Git Bash / WSL）
  - 4. 生成证书（mkcert）
  - 5. 将证书 + 私钥打包为 PKCS12（keystore.p12）
  - 6. 放入项目并修改 Spring Boot 配置
  - 7. 启动并测试（登录 / refresh / presign / PUT）
- 开发环境的 Cookie / CORS 建议
- 常见问题与排查
- 安全建议与注意事项
- 附：常用命令速查

---

## 前提
- Windows（本指南按 Windows 展示命令）；若是 macOS / Linux，请将命令稍做调整。
- 已有 Spring Boot 项目（可修改 `application-dev.yml`）。
- 你愿意把生成的 keystore 放到本地开发环境，不要提交到 git。

---

## 一步步操作（含命令）

下面命令以 PowerShell（管理员）为主。部分命令在 Git Bash / WSL 中也可执行（我会在相应位置注明）。

### 0. 准备（可选）
如果你已经安装了 Chocolatey、mkcert、openssl，直接跳到相应步骤。

---

### 1) 安装 Chocolatey（如果尚未安装）
Chocolatey 是 Windows 的包管理器，便于安装 mkcert、openssl 等工具。

以管理员身份打开 PowerShell，然后运行以下命令（一行）：
```powershell
Set-ExecutionPolicy Bypass -Scope Process -Force; `
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; `
iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
```

安装完成后，关闭并重新打开 PowerShell（使 PATH 生效），验证：
```powershell
choco -v
```

---

### 2) 安装 mkcert
mkcert 用于在本地生成受信任（被系统/浏览器信任）的证书。

使用 Chocolatey 安装：
```powershell
choco install mkcert -y
```

验证 mkcert 可用：
```powershell
mkcert -version
```

安装本地 CA（只需执行一次）：
```powershell
mkcert -install
```
该命令会把 mkcert 的根证书安装到系统受信任根证书存储中（浏览器会信任）。

---

### 3) 安装 OpenSSL（用于打包为 PKCS#12）
如果你使用 Git for Windows（通常自带 openssl），可以跳过。否则推荐用 Chocolatey 安装 openssl.light：

```powershell
choco install openssl.light -y
```

安装完成后，打开新的 PowerShell，确认 `openssl` 可用：
```powershell
openssl version
```

> 备选：也可以在 WSL 或 Git Bash 中运行 openssl（路径风格不同，见下文）。

---

### 4) 用 mkcert 生成证书与私钥
选择一个目录保存证书，例如 `C:\dev\certs`：
```powershell
mkdir C:\dev\certs
cd C:\dev\certs
```

执行 mkcert，为常用本地 host 生成证书（含 localhost、127.0.0.1、::1）：
```powershell
mkcert localhost 127.0.0.1 ::1
```

执行后会生成文件，类似：
- `localhost+2.pem`        （证书）
- `localhost+2-key.pem`    （私钥）

> 注意：文件名可能因 mkcert 版本而不同，请依据输出的文件名使用下一步命令。

你可以查看 `mkcert -CAROOT` 输出的 CA 路径（下面打包时会用到）：
```powershell
mkcert -CAROOT
```

---

### 5) 把证书与私钥打包为 PKCS#12（生成 `keystore.p12`）
在同一目录（包含证书与私钥），运行 openssl 命令打包。假设证书名为 `localhost+2.pem`，私钥 `localhost+2-key.pem`，并且 `mkcert -CAROOT` 返回路径 `C:\Users\<you>\AppData\Local\mkcert`。

PowerShell 示例命令：
```powershell
$caroot = & mkcert -CAROOT
openssl pkcs12 -export -in localhost+2.pem -inkey localhost+2-key.pem -out keystore.p12 -name tomcat -CAfile "$caroot\rootCA.pem" -caname root -passout pass:changeit
```

- `-out keystore.p12`：输出 PKCS12 文件
- `-name tomcat`：keystore 中的别名（Spring Boot 可用 `key-alias: tomcat`）
- `-passout pass:changeit`：keystore 密码（示例用 `changeit`，可改）

如果在 Git Bash（Unix 风格路径）：
```bash
CAROOT=$(mkcert -CAROOT)
openssl pkcs12 -export -in localhost+2.pem -inkey localhost+2-key.pem -out keystore.p12 -name tomcat -CAfile "$CAROOT/rootCA.pem" -caname root -passout pass:changeit
```

打包成功后，`keystore.p12` 会在当前目录生成。

> 提示：如果 openssl 命令提示找不到 `rootCA.pem`，确保 `mkcert -CAROOT` 正确，并传入完整路径。

---

### 6) 放入项目并配置 Spring Boot
把 `keystore.p12` 放到项目资源目录（例如 `src/main/resources/keystore/keystore.p12`）或其他安全路径（绝对路径也可）。

在 `application-dev.yml`（或 dev profile 文件）添加 SSL 配置，例如：

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: classpath:keystore/keystore.p12   # 或 file:/absolute/path/keystore.p12
    key-store-password: changeit
    key-store-type: PKCS12
    key-alias: tomcat
```

重启 Spring Boot 应用。访问：
```
https://localhost:8443/
```
如果使用 mkcert 且 `mkcert -install` 已执行，浏览器不会弹出证书不受信任的提示。

---

## 启动后验证（关键点）
1. 打开浏览器访问 `https://localhost:8443/`（注意 scheme 必须是 https）。
2. 触发登录（你的测试页面或直接 POST `/api/user/login`），在 *Network* 面板检查响应头 `Set-Cookie`：
   - 应看到 `refreshToken=...; Secure; HttpOnly; SameSite=Lax`（或项目里设置的 SameSite）。
3. 在 DevTools -> Application -> Cookies 中能看到 `refreshToken`（尽管 HttpOnly，DevTools 界面会显示）。
4. 调用刷新接口 `/api/auth/refresh` 时，前端需用 `fetch(..., { credentials: 'include' })`，浏览器会自动带上 cookie。

---

## 开发环境的 Cookie / CORS 建议

- 后端：
  - `refresh` cookie：`HttpOnly=true`、`Secure=true`（在本地启用了 HTTPS 后即可）、`SameSite` 根据是否跨域设置为 `Lax` 或 `None`。
  - `CorsConfig`：`allowCredentials(true)`（允许 cookie），并在 `allowedOriginPatterns` 指定前端 origin（不能使用 `*` 当 `allowCredentials(true)`）。
  - 暴露 `New-Access-Token` header（后端做了 `exposedHeaders("New-Access-Token")`），前端可读取。
- 前端：
  - 登录/刷新请求要使用 `credentials: 'include'`（跨域或同域都需当服务器写 HttpOnly cookie 时）。
  - Access token 存内存（不要放 localStorage），在请求中用 `Authorization: Bearer <token>` 发送。
  - 如果前端与后端跨域且需要 cookie，应在请求中设置 `credentials:'include'` 并且后端配置允许该 origin。

---

## 常见问题与排查

1. 浏览器报错：`Insecure sites can't set cookies with the 'Secure' directive`
   - 原因：请求是 `http://` 而 cookie 带 `Secure`。访问必须为 `https://`。
   - 解决：确认使用 `https://localhost:8443`，并且 server SSL 已启用。

2. 登录后没有写入 cookie（没有 `Set-Cookie`）
   - 检查后端是否实际调用 `JwtCookieUtil.writeRefreshCookie`（或等价方法）并在响应中添加 `Set-Cookie`。
   - 检查是否为 `https`（`Secure` cookie 在 HTTP 下被忽略）。
   - 若跨域，检查 `Access-Control-Allow-Origin` 是否正确且 `Access-Control-Allow-Credentials: true` 存在。

3. refresh 请求未携带 cookie
   - 确保前端请求使用 `credentials: 'include'`。
   - 检查浏览器 DevTools -> Network -> Request Headers 中是否有 `Cookie: refreshToken=...`。
   - 若没有，检查 SameSite 设置（跨域 POST 在 SameSite=Lax 下可能不会被携带）。

4. 访问根路径 `/` 返回 403
   - 检查 `SecurityConfig` 是否把 `/` 设为 `permitAll()` 或是否该路径受保护。
   - 参考：把 `"/", "/index.html"` 加入允许列表。

5. presigned PUT 上传 403（签名失败）
   - 原因：PUT 请求头与生成签名时的 header 不一致（例如 `Content-Type`、`Content-MD5`）。
   - 解决：在 PUT 时严格使用后端返回的 `putHeaders` 并匹配 header 值。

6. 浏览器仍显示证书不受信任
   - 确保执行过 `mkcert -install`，系统根 CA 已安装。
   - 确认访问的域名在证书的 SAN（例如 `localhost` / `127.0.0.1`）。

---

## 安全建议与注意事项（生产环境）
- 生产必须使用受信任 CA 签发的证书（Let’s Encrypt、云提供商证书等），不要使用 mkcert 的本地 CA。
- 生产中 cookie 一定要有 `Secure=true` 与 `HttpOnly=true`。若跨站需要 `SameSite=None` 并配合 `Secure=true`。
- 不要把 `keystore.p12`、私钥或证书推送到公共仓库；把它加入 `.gitignore`。
- 对 refresh token 的管理（Redis jti 存储、旋转、撤销）要做好监控与过期策略。
- 日志中避免打印完整的 token 或私钥。

---

## 附：常用命令速查（Windows PowerShell）

安装 Chocolatey（如需）：
```powershell
Set-ExecutionPolicy Bypass -Scope Process -Force; `
iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
```

安装 mkcert 与 openssl：
```powershell
choco install mkcert -y
choco install openssl.light -y
```

安装 mkcert CA：
```powershell
mkcert -install
```

生成证书（在目标目录）：
```powershell
cd C:\dev\certs
mkcert localhost 127.0.0.1 ::1
```

查看 mkcert CA 路径：
```powershell
mkcert -CAROOT
```

打包为 PKCS12（PowerShell）：
```powershell
$caroot = & mkcert -CAROOT
openssl pkcs12 -export -in localhost+2.pem -inkey localhost+2-key.pem -out keystore.p12 -name tomcat -CAfile "$caroot\rootCA.pem" -caname root -passout pass:changeit
```

Spring Boot `application-dev.yml` 示例：
```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: classpath:keystore/keystore.p12
    key-store-password: changeit
    key-store-type: PKCS12
    key-alias: tomcat

jwt:
  cookie-secure: true
  cookie-same-site: Lax
```
