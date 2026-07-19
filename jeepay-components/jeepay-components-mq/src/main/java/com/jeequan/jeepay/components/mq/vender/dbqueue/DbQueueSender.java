/*
 * Copyright (c) 2021-2031, 河北计全科技有限公司 (https://www.jeequan.com & jeequan@126.com).
 * <p>
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE 3.0;
 */
package com.jeequan.jeepay.components.mq.vender.dbqueue;

import com.jeequan.jeepay.components.mq.constant.MQVenderCS;
import com.jeequan.jeepay.components.mq.model.AbstractMQ;
import com.jeequan.jeepay.components.mq.model.CleanMchLoginAuthCacheMQ;
import com.jeequan.jeepay.components.mq.model.PayOrderDivisionMQ;
import com.jeequan.jeepay.components.mq.model.PayOrderMchNotifyMQ;
import com.jeequan.jeepay.components.mq.model.PayOrderReissueMQ;
import com.jeequan.jeepay.components.mq.model.ResetAppConfigMQ;
import com.jeequan.jeepay.components.mq.model.ResetIsvMchAppInfoConfigMQ;
import com.jeequan.jeepay.components.mq.vender.IMQSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight sender for the self-hosted Lite mode.
 *
 * <p>Durable merchant notify delivery is handled by the payment service database scanner.
 * This sender keeps existing IMQSender injection points working and dispatches same-process
 * messages that do not require a broker.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = MQVenderCS.YML_VENDER_KEY, havingValue = MQVenderCS.DB_QUEUE)
public class DbQueueSender implements IMQSender {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "jeepay-dbqueue-dispatcher");
        thread.setDaemon(true);
        return thread;
    });

    private final ObjectProvider<CleanMchLoginAuthCacheMQ.IMQReceiver> cleanMchLoginAuthCacheReceiver;
    private final ObjectProvider<PayOrderDivisionMQ.IMQReceiver> payOrderDivisionReceiver;
    private final ObjectProvider<PayOrderMchNotifyMQ.IMQReceiver> payOrderMchNotifyReceiver;
    private final ObjectProvider<PayOrderReissueMQ.IMQReceiver> payOrderReissueReceiver;
    private final ObjectProvider<ResetAppConfigMQ.IMQReceiver> resetAppConfigReceiver;
    private final ObjectProvider<ResetIsvMchAppInfoConfigMQ.IMQReceiver> resetIsvMchAppInfoConfigReceiver;

    public DbQueueSender(ObjectProvider<CleanMchLoginAuthCacheMQ.IMQReceiver> cleanMchLoginAuthCacheReceiver,
                         ObjectProvider<PayOrderDivisionMQ.IMQReceiver> payOrderDivisionReceiver,
                         ObjectProvider<PayOrderMchNotifyMQ.IMQReceiver> payOrderMchNotifyReceiver,
                         ObjectProvider<PayOrderReissueMQ.IMQReceiver> payOrderReissueReceiver,
                         ObjectProvider<ResetAppConfigMQ.IMQReceiver> resetAppConfigReceiver,
                         ObjectProvider<ResetIsvMchAppInfoConfigMQ.IMQReceiver> resetIsvMchAppInfoConfigReceiver) {
        this.cleanMchLoginAuthCacheReceiver = cleanMchLoginAuthCacheReceiver;
        this.payOrderDivisionReceiver = payOrderDivisionReceiver;
        this.payOrderMchNotifyReceiver = payOrderMchNotifyReceiver;
        this.payOrderReissueReceiver = payOrderReissueReceiver;
        this.resetAppConfigReceiver = resetAppConfigReceiver;
        this.resetIsvMchAppInfoConfigReceiver = resetIsvMchAppInfoConfigReceiver;
    }

    @Override
    public void send(AbstractMQ mqModel) {
        dispatch(mqModel);
    }

    @Override
    public void send(AbstractMQ mqModel, int delay) {
        int safeDelay = Math.max(delay, 0);
        executor.schedule(() -> dispatch(mqModel), safeDelay, TimeUnit.SECONDS);
    }

    private void dispatch(AbstractMQ mqModel) {
        try {
            if (mqModel instanceof PayOrderMchNotifyMQ) {
                log.debug("Merchant notify message is handled by payment database scanner.");
                return;
            }
            if (mqModel instanceof PayOrderReissueMQ) {
                PayOrderReissueMQ.IMQReceiver receiver = payOrderReissueReceiver.getIfAvailable();
                if (receiver != null) {
                    receiver.receive(((PayOrderReissueMQ) mqModel).getPayload());
                }
                return;
            }
            if (mqModel instanceof PayOrderDivisionMQ) {
                PayOrderDivisionMQ.IMQReceiver receiver = payOrderDivisionReceiver.getIfAvailable();
                if (receiver != null) {
                    receiver.receive(((PayOrderDivisionMQ) mqModel).getPayload());
                }
                return;
            }
            if (mqModel instanceof ResetAppConfigMQ) {
                ResetAppConfigMQ.IMQReceiver receiver = resetAppConfigReceiver.getIfAvailable();
                if (receiver != null) {
                    receiver.receive(((ResetAppConfigMQ) mqModel).getPayload());
                }
                return;
            }
            if (mqModel instanceof ResetIsvMchAppInfoConfigMQ) {
                ResetIsvMchAppInfoConfigMQ.IMQReceiver receiver = resetIsvMchAppInfoConfigReceiver.getIfAvailable();
                if (receiver != null) {
                    receiver.receive(((ResetIsvMchAppInfoConfigMQ) mqModel).getPayload());
                }
                return;
            }
            if (mqModel instanceof CleanMchLoginAuthCacheMQ) {
                CleanMchLoginAuthCacheMQ.IMQReceiver receiver = cleanMchLoginAuthCacheReceiver.getIfAvailable();
                if (receiver != null) {
                    receiver.receive(((CleanMchLoginAuthCacheMQ) mqModel).getPayload());
                }
                return;
            }
            log.warn("Unsupported dbQueue message: {}", mqModel.getClass().getName());
        } catch (Exception e) {
            log.error("Dispatch dbQueue message failed. mqName={}", mqModel.getMQName(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
