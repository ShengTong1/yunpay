package com.jeequan.jeepay.core.utils;

import cn.hutool.crypto.SecureUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 易支付（epay）签名工具
 *
 * <p>协议依据：go-epay SDK（github.com/Calcium-Ion/go-epay）的 util.go。
 * 易支付签名算法与 Jeepay 自有签名（{@link JeepayKit#getSign}）完全不同，
 * 不可复用，必须独立实现：
 * <ol>
 *   <li>过滤掉 sign、sign_type 以及空值参数</li>
 *   <li>按参数名 ASCII 字典序升序排序（大小写敏感）</li>
 *   <li>拼成 k=v&amp;k=v（末尾不带 &amp;）</li>
 *   <li>末尾直接拼接商户密钥（不是 &amp;key=密钥）</li>
 *   <li>MD5 取小写</li>
 * </ol>
 *
 * @author jeepay
 * @since 2026-06-21
 */
@Slf4j
public class EpayKit {

    /** 易支付签名类型固定 MD5 */
    public static final String SIGN_TYPE = "MD5";

    /**
     * 计算易支付签名
     *
     * @param params 参与签名的参数（含 sign/sign_type/空值会被自动过滤）
     * @param key    商户密钥
     * @return MD5 小写签名
     */
    public static String getSign(Map<String, ?> params, String key) {
        String stringToSign = buildSignString(params);
        // 末尾直接拼接密钥（非 &key=）
        String signString = stringToSign + key;
        String sign = SecureUtil.md5(signString).toLowerCase();
        log.info("epay signStr:{}, sign:{}", stringToSign, sign);
        return sign;
    }

    /**
     * 验证易支付签名
     *
     * @param params 含 sign 字段的参数集合
     * @param key    商户密钥
     * @return 签名是否有效
     */
    public static boolean verifySign(Map<String, ?> params, String key) {
        if (params == null || key == null) {
            return false;
        }
        Object signObj = params.get("sign");
        if (signObj == null || "".equals(signObj.toString())) {
            return false;
        }
        String expectSign = getSign(params, key);
        return expectSign.equalsIgnoreCase(signObj.toString());
    }

    /**
     * 构造待签名字符串：过滤 sign/sign_type/空值 → 字典序 → k=v&amp;k=v
     */
    private static String buildSignString(Map<String, ?> params) {
        // 收集参与签名的参数名（过滤 sign、sign_type、空值）
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, ?> entry : params.entrySet()) {
            String k = entry.getKey();
            Object v = entry.getValue();
            if ("sign".equals(k) || "sign_type".equals(k)) {
                continue;
            }
            if (v == null || "".equals(v.toString())) {
                continue;
            }
            keys.add(k);
        }
        // 按 ASCII 字典序升序（大小写敏感）
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            if (i > 0) {
                sb.append("&");
            }
            sb.append(k).append("=").append(params.get(k));
        }
        return sb.toString();
    }
}
