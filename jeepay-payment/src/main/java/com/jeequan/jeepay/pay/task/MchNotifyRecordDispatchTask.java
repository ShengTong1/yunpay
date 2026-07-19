/*
 * Copyright (c) 2021-2031, 河北计全科技有限公司 (https://www.jeequan.com & jeequan@126.com).
 * <p>
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE 3.0;
 */
package com.jeequan.jeepay.pay.task;

import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jeequan.jeepay.components.mq.constant.MQVenderCS;
import com.jeequan.jeepay.components.mq.model.PayOrderMchNotifyMQ;
import com.jeequan.jeepay.core.entity.MchNotifyRecord;
import com.jeequan.jeepay.service.impl.MchNotifyRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Dispatch merchant notify records from database in Lite mode.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = MQVenderCS.YML_VENDER_KEY, havingValue = MQVenderCS.DB_QUEUE)
public class MchNotifyRecordDispatchTask {

    private static final int QUERY_PAGE_SIZE = 100;

    @Autowired private MchNotifyRecordService mchNotifyRecordService;
    @Autowired private PayOrderMchNotifyMQ.IMQReceiver payOrderMchNotifyMQReceiver;

    @Scheduled(cron = "*/5 * * * * ?")
    public void start() {

        LambdaQueryWrapper<MchNotifyRecord> wrapper = MchNotifyRecord.gw()
                .eq(MchNotifyRecord::getState, MchNotifyRecord.STATE_ING)
                .apply("notify_count < notify_count_limit")
                .orderByAsc(MchNotifyRecord::getLastNotifyTime)
                .orderByAsc(MchNotifyRecord::getCreatedAt);

        try {
            IPage<MchNotifyRecord> page = mchNotifyRecordService.page(new Page<>(1, QUERY_PAGE_SIZE), wrapper);
            if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
                return;
            }

            Date now = new Date();
            for (MchNotifyRecord record : page.getRecords()) {
                if (!isDue(record, now)) {
                    continue;
                }
                payOrderMchNotifyMQReceiver.receive(new PayOrderMchNotifyMQ.MsgPayload(record.getNotifyId()));
            }
        } catch (Exception e) {
            log.error("Dispatch merchant notify records failed.", e);
        }
    }

    private boolean isDue(MchNotifyRecord record, Date now) {
        if (record.getLastNotifyTime() == null) {
            return true;
        }
        int notifyCount = record.getNotifyCount() == null ? 0 : record.getNotifyCount();
        int delaySeconds = Math.max(notifyCount * 30, 30);
        return DateUtil.offsetSecond(now, -delaySeconds).compareTo(record.getLastNotifyTime()) >= 0;
    }
}
