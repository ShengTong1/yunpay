package com.jeequan.jeepay.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jeequan.jeepay.core.model.BaseModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 易支付商户映射表
 * 用于把仅支持易支付协议的系统（如 new-api）接入 Jeepay。
 * pid/appSecret 对应 new-api 的 EpayId/EpayKey，mchNo/appId 对应 Jeepay 商户应用。
 * </p>
 *
 * @author jeepay
 * @since 2026-06-21
 */
@Schema(description = "易支付商户映射表")
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("t_epay_merchant")
public class EpayMerchant extends BaseModel {

    private static final long serialVersionUID = 1L;

    /** 构建 LambdaQueryWrapper，与其它实体保持一致 */
    public static final LambdaQueryWrapper<EpayMerchant> gw() {
        return new LambdaQueryWrapper<>();
    }

    /** 主键 */
    @Schema(title = "id", description = "主键")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 易支付商户ID（new-api 的 EpayId） */
    @Schema(title = "pid", description = "易支付商户ID")
    private String pid;

    /** 易支付商户密钥（new-api 的 EpayKey） */
    @Schema(title = "appSecret", description = "易支付商户密钥")
    private String appSecret;

    /** 对应 Jeepay 商户号 */
    @Schema(title = "mchNo", description = "对应 Jeepay 商户号")
    private String mchNo;

    /** 对应 Jeepay 应用ID */
    @Schema(title = "appId", description = "对应 Jeepay 应用ID")
    private String appId;

    /** 状态: 0-停用, 1-正常 */
    @Schema(title = "state", description = "状态: 0-停用, 1-正常")
    private Byte state;

    /** 备注 */
    @Schema(title = "remark", description = "备注")
    private String remark;

    /** 创建时间 */
    @Schema(title = "createdAt", description = "创建时间")
    private java.util.Date createdAt;

    /** 更新时间 */
    @Schema(title = "updatedAt", description = "更新时间")
    private java.util.Date updatedAt;
}
