# YunPay - 原生支持易支付接口的开源支付系统

> 基于 [jeepay](https://github.com/jeequan/jeepay)（计全支付 v3.2.9）二次开发，新增**易支付（epay）协议的原生支持**，可自建支付平台替代第三方易支付，支付数据完全自控。

<p align="center">
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring%20Boot-3.3.7-yellowgreen" /></a>
  <a href="https://www.oracle.com/java/technologies/downloads/#java17"><img src="https://img.shields.io/badge/JDK-17-green.svg" /></a>
  <a href="http://www.gnu.org/licenses/lgpl.html"><img src="https://img.shields.io/badge/license-LGPL--3.0-blue" /></a>
</p>

---

## 项目简介

本项目 fork 自 [jeequan/jeepay](https://github.com/jeequan/jeepay) v3.2.9，在其基础上新增了**易支付协议的原生支持**，并做了轻量化改造。

很多业务系统（如 [NewAPI](https://github.com/QuantumNous/new-api) 等）内置易支付协议，通常只能对接第三方易支付平台。本项目让 jeepay 自身即可作为易支付平台被调用，从而用自建系统替代第三方，支付链路全程自控、数据不外泄。

### 工作原理

```
业务系统  ──(易支付协议下单)──►  {本系统域名}/submit.php   ← EpaySubmitController
                                        │
                                        ▼  复用 jeepay 原生统一下单
                                  jeepay 内部下单 (支付宝/微信)
                                        │
                                        ▼  支付成功
                                  PayMchNotifyService 回调转发
                                        │  按 extParam.epay 标记走易支付通知格式
                                        ▼
业务系统  ◄──(易支付格式异步通知+MD5签名)──  转发到业务系统的 notify_url
```

- **下单**：业务系统按易支付协议构造表单跳转到 `/submit.php`，本系统用易支付算法验签后，调 jeepay 原生统一下单，返回支付链接。
- **回调**：支付成功后，jeepay 原生回调链路把订单结果翻译成易支付通知格式（含 `trade_status`/`sign` 等），签名后转发给业务系统的 `notify_url`。
- **签名**：易支付签名（小写 MD5 + 末尾拼密钥）独立实现于 `EpayKit`，与 jeepay 自有签名隔离，互不干扰。

业务系统全程以为自己对接的是标准易支付平台，**无需改动对接代码**。

> 完整的易支付对接细节见 [EPAY.md](./EPAY.md)。

---

## 与官方 jeepay 的差异

| 改造项 | 说明 |
|--------|------|
| ✅ **新增易支付原生接口** | `/submit.php` 下单 + 回调转发，配套 `t_epay_merchant` 商户映射表与运营平台管理界面 |
| ✅ **MQ 轻量化（dbQueue）** | 新增数据库队列实现，默认无需启动 RocketMQ，docker-compose 少起 2 个容器，适合中小流量 |
| ✅ **Docker 多阶段构建** | Dockerfile 改为 Docker 内 Maven 编译，服务器无需预装 Maven/Node，clone 即可 build |
| ✅ **国内镜像源默认** | `.env.example` 默认使用华为云 SWR / docker.1ms.run 镜像，国内零配置直达 |
| ✅ **保留支付测试** | 运营平台内置 payTest 联调测试 + WebSocket 实时状态推送，方便验收渠道配置 |

---

## 快速开始

### Docker Compose 部署（推荐）

```bash
git clone https://github.com/ShengTong1/yunpay.git
cd yunpay

# 按需修改配置（数据库密码等）
cp .env.example .env
nano .env

# 一键启动（首次会自动执行 init.sql + lite.sql 建库建表，耗时较长）
docker compose up -d
```

启动完成后访问：

| 服务 | 地址 | 说明 |
|------|------|------|
| 运营平台 | `http://<服务器IP>:9227` | 默认账号 `jeepay` / `jeepay123` |
| 支付网关 | `http://<服务器IP>:9216` | 业务系统的易支付地址指向这里 |

> 商户平台（端口 9228）默认不启动、不占用端口。代码保留但通过 `profiles` 控制，需要时执行 `docker compose --profile legacy-runtime up -d merchant` 启用。

### 数据库初始化

MySQL 首次启动自动按顺序执行：
1. `01-init.sql` - 建表 + 全部菜单（含 `t_epay_merchant` 易支付商户映射表）
2. `02-lite.sql` - 添加支付测试菜单

无需手动导数据。已有数据库则手动执行一次 `docs/sql/lite.sql` 即可。

---

## 对接业务系统

### 1. 配置易支付商户映射

登录运营平台 `http://<服务器IP>:9227`，进入「商户管理 -> 易支付商户」：
- **pid**：易支付商户ID（业务系统侧自定义）
- **appSecret**：易支付商户密钥（业务系统侧自定义）
- **mchNo / appId**：对应 jeepay 已创建的商户号与应用ID（先在「商户管理 -> 商户列表 / 应用列表」建好）
- **state**：启用

### 2. 配置业务系统

在业务系统的支付设置中，将易支付相关参数指向本系统：
- **易支付请求地址**：`http://<服务器IP>:9216`
- **商户ID / 密钥**：与上面配置的 pid / appSecret 一致

### 3. 生产环境建议启用 HTTPS

支付涉及签名与订单数据，建议对外暴露的服务启用 HTTPS（可用 nginx 反代 + Let's Encrypt 证书）。本系统已识别 `X-Forwarded-*` 头，反代后正常工作。

---

## 核心技术栈

| 软件 | 版本 |
|------|------|
| JDK | 17 |
| Spring Boot | 3.3.7 |
| MySQL | 5.7 / 8.0 |
| Redis | 3.2.8+ |
| MyBatis-Plus | 3.5.7 |
| Ant Design Vue | 4.2.6 |
| WxJava | 4.7.2.B |

支持的支付渠道（jeepay 原生）：支付宝、微信支付（含 V3）、云闪付、plspay、pppay、xxpay。

---

## 目录结构

```
yunpay/
├── jeepay-core/          # 基础工具（含 EpayKit 易支付签名、EpayMerchant 实体）
├── jeepay-service/       # 数据库 Service（含 EpayMerchantService）
├── jeepay-manager/       # 运营平台（含 EpayMerchantController、payTest、WebSocket）
├── jeepay-merchant/      # 商户平台（默认未启用）
├── jeepay-payment/       # 支付网关（含 EpaySubmitController、PayMchNotifyService 回调改造）
├── jeepay-components/    # 组件（含 dbQueue MQ 实现）
├── jeepay-ui/            # 前端（manager/merchant/cashier 三端，已并入本仓库）
├── conf/                 # 各服务配置（application.yml）
├── docs/sql/             # init.sql + lite.sql（建表与菜单）
└── docker-compose.yml    # 一键部署
```

---

## 已知限制

- 易支付接口当前支持 `alipay`（支付宝PC）和 `wxpay`（微信扫码）两种 type，其他 type 需在 `EpaySubmitController.WAY_CODE_MAP` 扩展。
- dbQueue 数据库队列适合中小流量（单机/小规模），高并发场景请在 `conf/payment/application.yml` 切回 rocketMQ（配置已注释保留）。
- dbQueue 模式下多实例部署会有重复扫描问题（无分布式锁），建议单实例运行 payment 服务。

---

## 开源协议

本项目遵循 [LGPL-3.0](./LICENSE) 协议，与上游 jeepay 一致。
- fork 自 [jeequan/jeepay](https://github.com/jeequan/jeepay)，感谢计全科技的开源贡献。
- 新增的易支付支持模块同样以 LGPL-3.0 开源。
- 按 LGPL 要求，对本项目的修改需继续以 LGPL 开源；通过依赖方式引用本项目的应用不受传染。

---

## 致谢

- [jeepay](https://github.com/jeequan/jeepay) - 计全支付开源系统
