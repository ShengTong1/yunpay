package com.jeequan.jeepay.pay.ctrl.epay;

import com.alibaba.fastjson.JSONObject;
import com.jeequan.jeepay.core.constants.CS;
import com.jeequan.jeepay.core.entity.EpayMerchant;
import com.jeequan.jeepay.core.entity.PayOrder;
import com.jeequan.jeepay.core.exception.BizException;
import com.jeequan.jeepay.core.model.ApiRes;
import com.jeequan.jeepay.core.utils.EpayKit;
import com.jeequan.jeepay.pay.ctrl.payorder.AbstractPayOrderController;
import com.jeequan.jeepay.pay.rqrs.payorder.UnifiedOrderRQ;
import com.jeequan.jeepay.pay.rqrs.payorder.UnifiedOrderRS;
import com.jeequan.jeepay.pay.rqrs.payorder.payway.AliPcOrderRQ;
import com.jeequan.jeepay.pay.rqrs.payorder.payway.WxNativeOrderRQ;
import com.jeequan.jeepay.service.impl.EpayMerchantService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 易支付（epay）兼容接口 —— 前台支付提交
 *
 * <p>对接 new-api 等仅支持易支付协议的系统。go-epay SDK 的 Purchase 不发请求，
 * 仅本地构造参数+签名后由前端跳转到 {PayAddress}/submit.php，本接口接收并处理。
 *
 * <p>协议依据：github.com/Calcium-Ion/go-epay 的 order.go / util.go。
 *
 * @author jeepay
 * @since 2026-06-21
 */
@Slf4j
@RestController
public class EpaySubmitController extends AbstractPayOrderController {

    @Autowired private EpayMerchantService epayMerchantService;

    /** 易支付 type → Jeepay wayCode 映射（go-epay 默认 device=pc） */
    private static final Map<String, String> WAY_CODE_MAP = new HashMap<>();
    static {
        WAY_CODE_MAP.put("alipay", CS.PAY_WAY_CODE.ALI_PC);   // 支付宝 PC 网站支付
        WAY_CODE_MAP.put("wxpay", CS.PAY_WAY_CODE.WX_NATIVE); // 微信扫码支付
    }

    /**
     * 易支付前台支付提交接口 /submit.php
     * 支持 GET / POST，参数以表单形式提交。
     */
    @RequestMapping(value = "/submit.php")
    public void submit(HttpServletResponse httpResponse) throws IOException {

        // 1. 收集请求参数（GET query + POST form 合并）
        Map<String, String> params = collectParams();

        String pid = params.get("pid");
        String type = params.get("type");
        String outTradeNo = params.get("out_trade_no");
        String money = params.get("money");
        String name = params.get("name");
        String notifyUrl = params.get("notify_url");
        String returnUrl = params.get("return_url");

        log.info("进入[epay]易支付下单：pid={}, out_trade_no={}, type={}, money={}", pid, outTradeNo, type, money);

        // 2. 基础参数校验
        if (StringUtils.isAnyBlank(pid, type, outTradeNo, money, name)) {
            throw new BizException("参数缺失：pid/type/out_trade_no/money/name 必填");
        }

        // 3. 查询易支付商户映射（pid → Jeepay mchNo/appId/密钥）
        EpayMerchant epayMerchant = epayMerchantService.queryByPid(pid);
        if (epayMerchant == null) {
            throw new BizException("易支付商户不存在或已停用");
        }

        // 4. 易支付签名验签（独立算法，不复用 JeepayKit）
        if (!EpayKit.verifySign(params, epayMerchant.getAppSecret())) {
            log.warn("易支付验签失败：pid={}, params={}", pid, params);
            throw new BizException("签名验证失败");
        }

        // 5. 支付方式映射
        String wayCode = WAY_CODE_MAP.get(type);
        if (wayCode == null) {
            throw new BizException("不支持的支付方式：" + type);
        }

        // 6. 金额转换：元 → 分
        long amount;
        try {
            amount = new BigDecimal(money).multiply(new BigDecimal("100")).setScale(0, BigDecimal.ROUND_HALF_UP).longValue();
        } catch (Exception e) {
            throw new BizException("金额格式错误");
        }
        if (amount <= 0) {
            throw new BizException("金额必须大于0");
        }

        // 7. 构造 Jeepay 统一下单参数（绕过 Jeepay 验签，已由易支付验签）
        // 关键：必须使用支付方式专用的 RQ 子类（如 AliPcOrderRQ），否则渠道实现内部强转会 ClassCastException。
        UnifiedOrderRQ bizRQ = createOrderRQ(wayCode);
        bizRQ.setMchNo(epayMerchant.getMchNo());
        bizRQ.setAppId(epayMerchant.getAppId());
        bizRQ.setMchOrderNo(outTradeNo);
        bizRQ.setAmount(amount);
        bizRQ.setCurrency("CNY");
        bizRQ.setSubject(name);
        bizRQ.setBody(name);
        bizRQ.setClientIp(getClientIp());
        // notifyUrl 留空：Jeepay 默认用商户应用的 notifyUrl 配置；这里走 Jeepay 原生回调链路，
        // 支付成功后由 PayMchNotifyService 按 extParam 中的易支付标记转发给 new-api 的 notify_url。
        bizRQ.setNotifyUrl(notifyUrl);
        bizRQ.setReturnUrl(returnUrl);
        bizRQ.setDivisionMode(PayOrder.DIVISION_MODE_FORBID);

        // 8. 扩展参数存易支付标记（回调转发时识别）
        JSONObject extParamObj = new JSONObject(new LinkedHashMap<>());
        extParamObj.put("epay", true);
        extParamObj.put("pid", pid);
        extParamObj.put("epayKey", epayMerchant.getAppSecret());
        extParamObj.put("epayType", type);
        extParamObj.put("epayName", name);
        extParamObj.put("epayNotifyUrl", notifyUrl);
        bizRQ.setExtParam(extParamObj.toJSONString());

        // 9. 调用 Jeepay 统一下单（复用主流程，不做 Jeepay 验签）
        ApiRes apiRes = unifiedOrder(wayCode, bizRQ);
        if (apiRes.getCode() != 0) {
            throw new BizException(apiRes.getMsg());
        }

        // 10. 取支付跳转地址
        UnifiedOrderRS bizRS = (UnifiedOrderRS) apiRes.getData();
        String payUrl = extractPayUrl(bizRS);
        if (StringUtils.isEmpty(payUrl)) {
            throw new BizException("获取支付链接失败");
        }

        // 11. 返回自动跳转 HTML 页面
        writeRedirectHtml(httpResponse, payUrl);
    }

    /**
     * 按 wayCode 创建支付方式专用的下单 RQ。
     * 必须用对应子类，否则渠道实现内部（如 AliPc.pay）的强转会 ClassCastException。
     */
    private UnifiedOrderRQ createOrderRQ(String wayCode) {
        if (CS.PAY_WAY_CODE.ALI_PC.equals(wayCode)) {
            return new AliPcOrderRQ();
        }
        if (CS.PAY_WAY_CODE.WX_NATIVE.equals(wayCode)) {
            return new WxNativeOrderRQ();
        }
        throw new BizException("不支持的支付方式：" + wayCode);
    }

    /** 从下单响应中提取支付跳转地址（payData 优先，其次 payUrl/codeImgUrl） */
    private String extractPayUrl(UnifiedOrderRS bizRS) {
        if (bizRS == null) {
            return null;
        }
        if (StringUtils.isNotEmpty(bizRS.getPayData())) {
            return bizRS.getPayData();
        }
        // 兜底：部分支付方式直接把 payUrl 放在 data map 里
        Object payUrlObj = ((JSONObject) JSONObject.toJSON(bizRS)).get("payUrl");
        return payUrlObj == null ? null : payUrlObj.toString();
    }

    /** 合并 GET query 与 POST form 参数 */
    private Map<String, String> collectParams() {
        Map<String, String> params = new LinkedHashMap<>();
        // GET query
        request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0 && StringUtils.isNotEmpty(v[0])) {
                params.put(k, v[0]);
            }
        });
        return params;
    }

    /** 输出自动跳转到支付页的 HTML */
    private void writeRedirectHtml(HttpServletResponse httpResponse, String payUrl) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
        html.append("<meta http-equiv=\"refresh\" content=\"0;url=").append(payUrl).append("\">");
        html.append("<title>正在跳转到支付页面...</title></head><body>");
        html.append("<p>正在跳转到支付页面，如未自动跳转请 <a href=\"").append(payUrl).append("\">点击这里</a></p>");
        html.append("<script>window.location.href=").append(JSONObject.toJSONString(payUrl)).append(";</script>");
        html.append("</body></html>");
        httpResponse.setContentType("text/html;charset=UTF-8");
        httpResponse.getWriter().write(html.toString());
    }
}
