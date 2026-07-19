# 易支付（epay）接口对接技术文档

本文档记录本系统原生支持易支付协议的实现细节，供二次开发和问题排查参考。

## 概述

易支付（epay）是一套第三方支付聚合接口标准。本系统在 jeepay 内原生实现了该协议，使 jeepay 自身即可作为易支付平台被业务系统调用，无需中转网关。

涉及的核心文件：

| 文件 | 职责 |
|------|------|
| `jeepay-payment/.../pay/ctrl/epay/EpaySubmitController.java` | `/submit.php` 入口，接收易支付下单请求 |
| `jeepay-core/.../core/utils/EpayKit.java` | 易支付签名算法（独立于 JeepayKit） |
| `jeepay-core/.../core/entity/EpayMerchant.java` | 易支付商户映射实体（表 `t_epay_merchant`） |
| `jeepay-service/.../service/impl/EpayMerchantService.java` | 商户映射查询服务 |
| `jeepay-manager/.../mgr/ctrl/epay/EpayMerchantController.java` | 运营平台商户映射管理 CRUD |
| `jeepay-payment/.../pay/service/PayMchNotifyService.java` | 回调通知转发（改造点） |
| `docs/sql/init.sql` + `lite.sql` | 建表与菜单 |

## 下单流程

### 业务系统端（调用方）

业务系统按易支付协议在本地构造参数并签名（易支付协议要求本地签名，不发 HTTP 请求构造订单），返回表单参数和提交地址 `{EpayAddress}/submit.php`，前端拿到后跳转（表单 GET/POST）到该地址。

易支付下单请求参数：
```
pid, type, out_trade_no, notify_url, name, money, device, sign_type, return_url, sign
```

### 本系统端（接收方）

`EpaySubmitController.submit()` 处理 `/submit.php`：

1. **收参**：合并 GET query + POST form
2. **查商户映射**：按 `pid` 查 `t_epay_merchant`（必须 state=1 启用）
3. **易支付验签**：`EpayKit.verifySign(params, appSecret)` - 用易支付算法验签，**不复用 JeepayKit**
4. **支付方式映射**：`type` -> jeepay wayCode
   - `alipay` -> `ALI_PC`（支付宝 PC 网站支付）
   - `wxpay` -> `WX_NATIVE`（微信扫码）
5. **金额转换**：元 -> 分（`BigDecimal * 100`，四舍五入）
6. **构造统一下单参数**：填充 `UnifiedOrderRQ`（mchNo/appId/mchOrderNo/amount/subject 等）
7. **调 jeepay 原生下单**：`unifiedOrder(wayCode, bizRQ)`，复用主流程，**绕过 jeepay 自有验签**（已由易支付验签）
8. **取支付链接**：从下单响应取 `payData`/`payUrl`
9. **返回跳转 HTML**：输出自动跳转到支付页的 HTML

### 关键设计点

#### 1. 双签名体系隔离

易支付签名与 jeepay 自有签名是**两套完全不同的算法**，必须隔离：

| 维度 | 易支付签名（EpayKit） | jeepay 签名（JeepayKit） |
|------|----------------------|--------------------------|
| 末尾拼接 | 直接拼密钥 `string + key` | `string + &key=密钥` |
| MD5 大小写 | 小写 | 大写 |
| 过滤字段 | sign / sign_type / 空值 | sign / 空值 |
| 用途 | 业务系统 <-> 本系统 | 本系统内部商户应用签名 |

`EpaySubmitController` 在易支付入口自己做验签，然后**绕过** jeepay 下单时的 `JeepayKit` 验签，直接走 `unifiedOrder` 主流程。

#### 2. 必须用支付方式专用的 RQ 子类

构造下单参数时，**必须用 `AliPcOrderRQ` / `WxNativeOrderRQ` 等专用子类**，不能用基类 `UnifiedOrderRQ`。因为渠道实现层（如 `AliPc.pay`）内部会强转 `(AliPcOrderRQ)`，用基类会抛 `ClassCastException`。

`EpaySubmitController.createOrderRQ(wayCode)` 工厂方法处理这个映射。

#### 3. notify_url 通过 extParam 订单级携带

易支付的 `notify_url`（业务系统的回调地址）**不放在 jeepay 商户应用的 notifyUrl 配置里**，而是塞进订单 `extParam` 字段。因为每个易支付商户（pid）对应的业务系统回调地址不同，必须订单级携带。

extParam 结构：
```json
{
  "epay": true,
  "pid": "...",
  "epayKey": "...",
  "epayType": "alipay",
  "epayName": "商品名",
  "epayNotifyUrl": "https://your-domain/api/epay/notify"
}
```

## 回调转发流程

支付成功后，jeepay 原生回调链路触发 `PayMchNotifyService.createNotifyUrl()`，该方法被改造为**优先检测易支付订单**：

1. **解析 extParam**：若含 `{"epay":true,...}`，走易支付通知分支
2. **构造易支付通知参数**：
   ```
   pid, trade_no, out_trade_no, type, name, money, trade_status, sign, sign_type
   ```
   - `trade_no` = jeepay 订单号（payOrderId）
   - `out_trade_no` = 商户订单号（mchOrderNo，即业务系统的订单号）
   - `money` = 分转元，保留两位
   - `trade_status` = `TRADE_SUCCESS`（成功）或 `TRADE_CLOSED`（关闭）
3. **易支付签名**：`EpayKit.getSign(params, epayKey)` 签名
4. **转发**：追加到 `extParam.epayNotifyUrl` 上，由 jeepay 原生通知机制 HTTP 发送给业务系统

业务系统收到后按易支付协议验签（小写 MD5），验签通过且 `trade_status == TRADE_SUCCESS` 则完成订单。

## 字段对齐

本系统严格遵循易支付协议的字段约定：

| 易支付协议字段 | 本系统（接收/构造） | 一致 |
|----------------|---------------------|------|
| `pid` | `pid` | ✅ |
| `type` | `type`（映射成 wayCode） | ✅ |
| `out_trade_no` | `out_trade_no` / `mchOrderNo` | ✅ |
| `money` | `money`（元<->分转换） | ✅ |
| `name` | `name` | ✅ |
| `notify_url` | 存入 extParam.epayNotifyUrl | ✅ |
| `return_url` | `return_url` | ✅ |
| 回调 `trade_no` | payOrderId | ✅ |
| 回调 `trade_status` | TRADE_SUCCESS / TRADE_CLOSED | ✅ |
| 签名（小写 MD5 + 末尾拼 key） | EpayKit 同算法 | ✅ |

## 易支付商户映射表

`t_epay_merchant` 表把易支付协议参数映射到 jeepay 真实商户应用：

| 字段 | 说明 |
|------|------|
| `pid` | 易支付商户ID（业务系统侧自定义） |
| `app_secret` | 易支付商户密钥（业务系统侧自定义） |
| `mch_no` | jeepay 商户号 |
| `app_id` | jeepay 应用ID |
| `state` | 0-停用 1-正常 |

一个 pid 对应一个 jeepay 商户应用。业务系统侧的商户ID/密钥自定义，但要与本表 pid/app_secret 一致。

## 扩展更多支付方式

当前 `EpaySubmitController.WAY_CODE_MAP` 只映射了 alipay/wxpay。如需支持更多（如 QQ钱包、云闪付等易支付 type），在 map 中添加映射，并在 `createOrderRQ()` 增加对应的 RQ 子类创建：

```java
WAY_CODE_MAP.put("qqpay", CS.PAY_WAY_CODE.XXX);  // 需确认 jeepay 有对应 wayCode
```

## 排查指南

### 下单失败

- **「参数缺失」**：检查业务系统是否传齐 pid/type/out_trade_no/money/name
- **「易支付商户不存在或已停用」**：运营平台检查 pid 映射 state=1
- **「签名验证失败」**：确认业务系统的密钥与本表 app_secret 完全一致
- **「不支持的支付方式」**：type 不在 WAY_CODE_MAP，需扩展

### 回调未送达 / 订单未完成

1. 查 jeepay payment 日志：`docker logs jeepay-payment | grep -i "易支付通知"`
   - 有「易支付通知构造」记录 = 本系统已转发，问题在业务系统端
   - 无记录 = jeepay 未识别为易支付订单，检查 extParam.epay 标记
2. 检查业务系统的 notify_url 是否公网可达且为 HTTPS
3. 检查 `paySiteUrl` 配置（运营平台「系统管理 -> 系统配置」），支付宝/微信异步回调需要公网可达

### 签名不一致

- 易支付签名是**小写** MD5 + **末尾直接拼密钥**（非 `&key=`）
- 确认 `EpayKit.buildSignString` 过滤了 sign/sign_type/空值，按 ASCII 字典序排序
- 可对照易支付协议的签名算法逐字节验证
