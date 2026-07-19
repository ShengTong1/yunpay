package com.jeequan.jeepay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jeequan.jeepay.core.entity.EpayMerchant;
import com.jeequan.jeepay.service.mapper.EpayMerchantMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 易支付商户映射表 服务实现类
 * </p>
 *
 * @author jeepay
 * @since 2026-06-21
 */
@Service
public class EpayMerchantService extends ServiceImpl<EpayMerchantMapper, EpayMerchant> {

    /** 根据 pid 查询启用状态的易支付商户映射（下单/验签时使用） */
    public EpayMerchant queryByPid(String pid) {
        if (StringUtils.isEmpty(pid)) {
            return null;
        }
        return getOne(EpayMerchant.gw()
                .eq(EpayMerchant::getPid, pid)
                .eq(EpayMerchant::getState, 1));
    }

    /** 校验 pid 是否已存在（用于新增/编辑时唯一性校验，excludeId 为编辑时排除自身） */
    public boolean pidExists(String pid, Long excludeId) {
        LambdaQueryWrapper<EpayMerchant> wrapper = EpayMerchant.gw().eq(EpayMerchant::getPid, pid);
        if (excludeId != null) {
            wrapper.ne(EpayMerchant::getId, excludeId);
        }
        return count(wrapper) > 0;
    }
}
