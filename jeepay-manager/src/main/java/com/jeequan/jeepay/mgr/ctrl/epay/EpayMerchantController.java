package com.jeequan.jeepay.mgr.ctrl.epay;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jeequan.jeepay.core.aop.MethodLog;
import com.jeequan.jeepay.core.constants.ApiCodeEnum;
import com.jeequan.jeepay.core.entity.EpayMerchant;
import com.jeequan.jeepay.core.entity.MchApp;
import com.jeequan.jeepay.core.entity.MchInfo;
import com.jeequan.jeepay.core.model.ApiPageRes;
import com.jeequan.jeepay.core.model.ApiRes;
import com.jeequan.jeepay.mgr.ctrl.CommonCtrl;
import com.jeequan.jeepay.service.impl.EpayMerchantService;
import com.jeequan.jeepay.service.impl.MchAppService;
import com.jeequan.jeepay.service.impl.MchInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;

/**
 * 易支付商户映射管理
 *
 * <p>用于配置 new-api 等仅支持易支付协议的系统如何映射到 Jeepay 商户应用。
 *
 * @author jeepay
 * @since 2026-06-21
 */
@Tag(name = "易支付商户管理")
@RestController
@RequestMapping("/api/epayMerchants")
public class EpayMerchantController extends CommonCtrl {

    @Autowired private EpayMerchantService epayMerchantService;
    @Autowired private MchInfoService mchInfoService;
    @Autowired private MchAppService mchAppService;

    /** 易支付商户列表 */
    @Operation(summary = "查询易支付商户列表")
    @Parameters({
            @Parameter(name = "iToken", description = "用户身份凭证", required = true, in = ParameterIn.HEADER),
            @Parameter(name = "pageNumber", description = "分页页码"),
            @Parameter(name = "pageSize", description = "分页条数"),
            @Parameter(name = "pid", description = "易支付商户ID"),
            @Parameter(name = "mchNo", description = "Jeepay 商户号"),
            @Parameter(name = "appId", description = "Jeepay 应用ID"),
            @Parameter(name = "state", description = "状态: 0-停用, 1-启用")
    })
    @PreAuthorize("hasAuthority('ENT_EPAY_MERCHANT_LIST')")
    @GetMapping
    public ApiPageRes<EpayMerchant> list() {
        EpayMerchant queryObj = getObject(EpayMerchant.class);

        LambdaQueryWrapper<EpayMerchant> wrapper = EpayMerchant.gw().orderByDesc(EpayMerchant::getId);
        if (queryObj != null) {
            if (StringUtils.isNotEmpty(queryObj.getPid())) {
                wrapper.eq(EpayMerchant::getPid, queryObj.getPid());
            }
            if (StringUtils.isNotEmpty(queryObj.getMchNo())) {
                wrapper.eq(EpayMerchant::getMchNo, queryObj.getMchNo());
            }
            if (StringUtils.isNotEmpty(queryObj.getAppId())) {
                wrapper.eq(EpayMerchant::getAppId, queryObj.getAppId());
            }
            if (queryObj.getState() != null) {
                wrapper.eq(EpayMerchant::getState, queryObj.getState());
            }
        }

        IPage<EpayMerchant> pages = epayMerchantService.page(getIPage(), wrapper);
        return ApiPageRes.pages(pages);
    }

    /** 新增易支付商户 */
    @Operation(summary = "新增易支付商户")
    @Parameters({
            @Parameter(name = "iToken", description = "用户身份凭证", required = true, in = ParameterIn.HEADER),
            @Parameter(name = "pid", description = "易支付商户ID", required = true),
            @Parameter(name = "appSecret", description = "易支付商户密钥", required = true),
            @Parameter(name = "mchNo", description = "Jeepay 商户号", required = true),
            @Parameter(name = "appId", description = "Jeepay 应用ID", required = true),
            @Parameter(name = "state", description = "状态: 0-停用, 1-启用"),
            @Parameter(name = "remark", description = "备注")
    })
    @PreAuthorize("hasAuthority('ENT_EPAY_MERCHANT_ADD')")
    @MethodLog(remark = "新增易支付商户")
    @PostMapping
    public ApiRes add() {
        EpayMerchant epayMerchant = getObject(EpayMerchant.class);

        // 校验必填
        if (StringUtils.isAnyBlank(epayMerchant.getPid(), epayMerchant.getAppSecret(),
                epayMerchant.getMchNo(), epayMerchant.getAppId())) {
            return ApiRes.fail(ApiCodeEnum.PARAMS_ERROR);
        }
        // pid 唯一性校验
        if (epayMerchantService.pidExists(epayMerchant.getPid(), null)) {
            return ApiRes.customFail("易支付商户ID已存在");
        }
        // 校验 Jeepay 商户/应用存在且匹配
        String checkMsg = checkMchApp(epayMerchant.getMchNo(), epayMerchant.getAppId());
        if (checkMsg != null) {
            return ApiRes.customFail(checkMsg);
        }
        if (epayMerchant.getState() == null) {
            epayMerchant.setState((byte) 1);
        }
        if (!epayMerchantService.save(epayMerchant)) {
            return ApiRes.fail(ApiCodeEnum.SYS_OPERATION_FAIL_CREATE);
        }
        return ApiRes.ok();
    }

    /** 易支付商户详情 */
    @Operation(summary = "易支付商户详情")
    @PreAuthorize("hasAnyAuthority('ENT_EPAY_MERCHANT_LIST', 'ENT_EPAY_MERCHANT_EDIT')")
    @GetMapping("/{id}")
    public ApiRes<EpayMerchant> detail(@PathVariable("id") Long id) {
        EpayMerchant epayMerchant = epayMerchantService.getById(id);
        if (epayMerchant == null) {
            return ApiRes.fail(ApiCodeEnum.SYS_OPERATION_FAIL_SELETE);
        }
        return ApiRes.ok(epayMerchant);
    }

    /** 更新易支付商户 */
    @Operation(summary = "更新易支付商户")
    @PreAuthorize("hasAuthority('ENT_EPAY_MERCHANT_EDIT')")
    @MethodLog(remark = "更新易支付商户")
    @PutMapping("/{id}")
    public ApiRes update(@PathVariable("id") Long id) {
        EpayMerchant epayMerchant = getObject(EpayMerchant.class);
        epayMerchant.setId(id);

        // pid 唯一性校验（排除自身）
        if (StringUtils.isNotEmpty(epayMerchant.getPid()) && epayMerchantService.pidExists(epayMerchant.getPid(), id)) {
            return ApiRes.customFail("易支付商户ID已存在");
        }
        // 校验 Jeepay 商户/应用
        if (StringUtils.isNotEmpty(epayMerchant.getMchNo()) && StringUtils.isNotEmpty(epayMerchant.getAppId())) {
            String checkMsg = checkMchApp(epayMerchant.getMchNo(), epayMerchant.getAppId());
            if (checkMsg != null) {
                return ApiRes.customFail(checkMsg);
            }
        }
        if (!epayMerchantService.updateById(epayMerchant)) {
            return ApiRes.fail(ApiCodeEnum.SYS_OPERATION_FAIL_UPDATE);
        }
        return ApiRes.ok();
    }

    /** 删除易支付商户 */
    @Operation(summary = "删除易支付商户")
    @PreAuthorize("hasAuthority('ENT_EPAY_MERCHANT_DEL')")
    @MethodLog(remark = "删除易支付商户")
    @DeleteMapping("/{id}")
    public ApiRes delete(@PathVariable("id") Long id) {
        if (!epayMerchantService.removeById(id)) {
            return ApiRes.fail(ApiCodeEnum.SYS_OPERATION_FAIL_DELETE);
        }
        return ApiRes.ok();
    }

    /** 校验 Jeepay 商户号与应用ID 是否存在且匹配 */
    private String checkMchApp(String mchNo, String appId) {
        MchInfo mchInfo = mchInfoService.getById(mchNo);
        if (mchInfo == null) {
            return "Jeepay 商户号不存在";
        }
        MchApp mchApp = mchAppService.getById(appId);
        if (mchApp == null || !mchNo.equals(mchApp.getMchNo())) {
            return "Jeepay 应用ID不存在或与商户号不匹配";
        }
        return null;
    }
}
