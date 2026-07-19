package com.jeequan.jeepay.mgr.ctrl.paytest;

import com.alibaba.fastjson.JSONObject;
import com.jeequan.jeepay.JeepayClient;
import com.jeequan.jeepay.core.constants.CS;
import com.jeequan.jeepay.core.entity.MchApp;
import com.jeequan.jeepay.core.entity.MchPayPassage;
import com.jeequan.jeepay.core.exception.BizException;
import com.jeequan.jeepay.core.model.ApiRes;
import com.jeequan.jeepay.core.model.DBApplicationConfig;
import com.jeequan.jeepay.exception.JeepayException;
import com.jeequan.jeepay.mgr.ctrl.CommonCtrl;
import com.jeequan.jeepay.model.PayOrderCreateReqModel;
import com.jeequan.jeepay.request.PayOrderCreateRequest;
import com.jeequan.jeepay.response.PayOrderCreateResponse;
import com.jeequan.jeepay.service.impl.MchAppService;
import com.jeequan.jeepay.service.impl.MchPayPassageService;
import com.jeequan.jeepay.service.impl.SysConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.Set;

@Tag(name = "支付测试")
@RestController
@RequestMapping("/api/paytest")
public class PaytestController extends CommonCtrl {

    @Autowired private MchAppService mchAppService;
    @Autowired private MchPayPassageService mchPayPassageService;
    @Autowired private SysConfigService sysConfigService;

    @Operation(summary = "查询应用支持的支付方式")
    @Parameters({
            @Parameter(name = "iToken", description = "用户身份凭证", required = true, in = ParameterIn.HEADER),
            @Parameter(name = "appId", description = "应用ID", required = true)
    })
    @PreAuthorize("hasAuthority('ENT_MGR_PAY_TEST_PAYWAY_LIST')")
    @GetMapping("/payways/{appId}")
    public ApiRes<Set<String>> payWayList(@PathVariable("appId") String appId) {

        MchApp mchApp = mchAppService.getById(appId);
        if(mchApp == null || mchApp.getState() != CS.PUB_USABLE || !mchApp.getAppId().equals(appId)){
            throw new BizException("商户应用不存在或不可用");
        }

        Set<String> payWaySet = new HashSet<>();
        mchPayPassageService.list(
                MchPayPassage.gw().select(MchPayPassage::getWayCode)
                        .eq(MchPayPassage::getMchNo, mchApp.getMchNo())
                        .eq(MchPayPassage::getAppId, appId)
                        .eq(MchPayPassage::getState, CS.PUB_USABLE)
        ).forEach(r -> payWaySet.add(r.getWayCode()));

        return ApiRes.ok(payWaySet);
    }

    @Operation(summary = "发起支付测试订单")
    @PreAuthorize("hasAuthority('ENT_MGR_PAY_TEST_DO')")
    @PostMapping("/payOrders")
    public ApiRes doPay() {

        String appId = getValStringRequired("appId");
        Long amount = getRequiredAmountL("amount");
        String mchOrderNo = getValStringRequired("mchOrderNo");
        String wayCode = getValStringRequired("wayCode");
        String orderTitle = getValStringRequired("orderTitle");

        if(StringUtils.isEmpty(orderTitle)){
            throw new BizException("订单标题不能为空");
        }

        String payDataType = getValString("payDataType");
        String authCode = getValString("authCode");

        MchApp mchApp = mchAppService.getById(appId);
        if(mchApp == null || mchApp.getState() != CS.PUB_USABLE || !mchApp.getAppId().equals(appId)){
            throw new BizException("商户应用不存在或不可用");
        }

        String mchNo = mchApp.getMchNo();
        PayOrderCreateRequest request = new PayOrderCreateRequest();
        PayOrderCreateReqModel model = new PayOrderCreateReqModel();
        request.setBizModel(model);

        model.setMchNo(mchNo);
        model.setAppId(appId);
        model.setMchOrderNo(mchOrderNo);
        model.setWayCode(wayCode);
        model.setAmount(amount);
        model.setCurrency(wayCode.equalsIgnoreCase("pp_pc") ? "USD" : "CNY");
        model.setClientIp(getClientIp());
        model.setSubject(orderTitle + "[" + mchNo + "商户联调]");
        model.setBody(orderTitle + "[" + mchNo + "商户联调]");

        DBApplicationConfig dbApplicationConfig = sysConfigService.getDBApplicationConfig();
        model.setNotifyUrl(getEnvOrDefault("JEEPAY_MGR_NOTIFY_URL", dbApplicationConfig.getMgrSiteUrl()) + "/api/anon/paytestNotify/payOrder");
        model.setDivisionMode((byte) 0);

        JSONObject extParams = new JSONObject();
        if(StringUtils.isNotEmpty(payDataType)) {
            extParams.put("payDataType", payDataType.trim());
        }
        if(StringUtils.isNotEmpty(authCode)) {
            extParams.put("authCode", authCode.trim());
        }
        model.setChannelExtra(extParams.toString());

        JeepayClient jeepayClient = new JeepayClient(getEnvOrDefault("JEEPAY_PAY_API_URL", dbApplicationConfig.getPaySiteUrl()), mchApp.getAppSecret());
        try {
            PayOrderCreateResponse response = jeepayClient.execute(request);
            if(response.getCode() != 0){
                throw new BizException(response.getMsg());
            }
            return ApiRes.ok(response.get());
        } catch (JeepayException e) {
            throw new BizException(e.getMessage());
        }
    }

    private String getEnvOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return StringUtils.isNotBlank(value) ? StringUtils.removeEnd(value.trim(), "/") : defaultValue;
    }
}
