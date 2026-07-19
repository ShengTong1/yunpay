/*
 * Copyright (c) 2021-2031, 河北计全科技有限公司 (https://www.jeequan.com & jeequan@126.com).
 * <p>
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE 3.0;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.gnu.org/licenses/lgpl.html
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jeequan.jeepay.pay.service;

import com.alibaba.fastjson.JSONObject;
import com.jeequan.jeepay.components.mq.model.PayOrderMchNotifyMQ;
import com.jeequan.jeepay.components.mq.vender.IMQSender;
import com.jeequan.jeepay.core.entity.MchNotifyRecord;
import com.jeequan.jeepay.core.entity.PayOrder;
import com.jeequan.jeepay.core.entity.RefundOrder;
import com.jeequan.jeepay.core.entity.TransferOrder;
import com.jeequan.jeepay.core.utils.EpayKit;
import com.jeequan.jeepay.core.utils.JeepayKit;
import com.jeequan.jeepay.core.utils.StringKit;
import com.jeequan.jeepay.pay.rqrs.payorder.QueryPayOrderRS;
import com.jeequan.jeepay.pay.rqrs.refund.QueryRefundOrderRS;
import com.jeequan.jeepay.pay.rqrs.transfer.QueryTransferOrderRS;
import com.jeequan.jeepay.service.impl.MchNotifyRecordService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/*
* 商户通知 service
*
* @author terrfly
* @site https://www.jeequan.com
* @date 2021/6/8 17:43
*/
@Slf4j
@Service
public class PayMchNotifyService {

    @Autowired private MchNotifyRecordService mchNotifyRecordService;
    @Autowired private ConfigContextQueryService configContextQueryService;
    @Autowired private IMQSender mqSender;


    /** 商户通知信息， 只有订单是终态，才会发送通知， 如明确成功和明确失败 **/
    public void payOrderNotify(PayOrder dbPayOrder){

        try {
            // 通知地址为空
            if(StringUtils.isEmpty(dbPayOrder.getNotifyUrl())){
                return ;
            }

            //获取到通知对象
            MchNotifyRecord mchNotifyRecord = mchNotifyRecordService.findByPayOrder(dbPayOrder.getPayOrderId());

            if(mchNotifyRecord != null){

                log.info("当前已存在通知消息， 不再发送。");
                return ;
            }

            //商户app私钥
            String appSecret = configContextQueryService.queryMchApp(dbPayOrder.getMchNo(), dbPayOrder.getAppId()).getAppSecret();

            // 封装通知url
            String notifyUrl = createNotifyUrl(dbPayOrder, appSecret);
            mchNotifyRecord = new MchNotifyRecord();
            mchNotifyRecord.setOrderId(dbPayOrder.getPayOrderId());
            mchNotifyRecord.setOrderType(MchNotifyRecord.TYPE_PAY_ORDER);
            mchNotifyRecord.setMchNo(dbPayOrder.getMchNo());
            mchNotifyRecord.setMchOrderNo(dbPayOrder.getMchOrderNo()); //商户订单号
            mchNotifyRecord.setIsvNo(dbPayOrder.getIsvNo());
            mchNotifyRecord.setAppId(dbPayOrder.getAppId());
            mchNotifyRecord.setNotifyUrl(notifyUrl);
            mchNotifyRecord.setResResult("");
            mchNotifyRecord.setNotifyCount(0);
            mchNotifyRecord.setState(MchNotifyRecord.STATE_ING); // 通知中

            try {
                mchNotifyRecordService.save(mchNotifyRecord);
            } catch (Exception e) {
                log.info("数据库已存在[{}]消息，本次不再推送。", mchNotifyRecord.getOrderId());
                return ;
            }

            //推送到MQ
            Long notifyId = mchNotifyRecord.getNotifyId();
            mqSender.send(PayOrderMchNotifyMQ.build(notifyId));

        } catch (Exception e) {
            log.error("推送失败！", e);
        }
    }

    /** 商户通知信息，退款成功的发送通知 **/
    public void refundOrderNotify(RefundOrder dbRefundOrder){

        try {
            // 通知地址为空
            if(StringUtils.isEmpty(dbRefundOrder.getNotifyUrl())){
                return ;
            }

            //获取到通知对象
            MchNotifyRecord mchNotifyRecord = mchNotifyRecordService.findByRefundOrder(dbRefundOrder.getRefundOrderId());

            if(mchNotifyRecord != null){

                log.info("当前已存在通知消息， 不再发送。");
                return ;
            }

            //商户app私钥
            String appSecret = configContextQueryService.queryMchApp(dbRefundOrder.getMchNo(), dbRefundOrder.getAppId()).getAppSecret();

            // 封装通知url
            String notifyUrl = createNotifyUrl(dbRefundOrder, appSecret);
            mchNotifyRecord = new MchNotifyRecord();
            mchNotifyRecord.setOrderId(dbRefundOrder.getRefundOrderId());
            mchNotifyRecord.setOrderType(MchNotifyRecord.TYPE_REFUND_ORDER);
            mchNotifyRecord.setMchNo(dbRefundOrder.getMchNo());
            mchNotifyRecord.setMchOrderNo(dbRefundOrder.getMchRefundNo()); //商户订单号
            mchNotifyRecord.setIsvNo(dbRefundOrder.getIsvNo());
            mchNotifyRecord.setAppId(dbRefundOrder.getAppId());
            mchNotifyRecord.setNotifyUrl(notifyUrl);
            mchNotifyRecord.setResResult("");
            mchNotifyRecord.setNotifyCount(0);
            mchNotifyRecord.setState(MchNotifyRecord.STATE_ING); // 通知中

            try {
                mchNotifyRecordService.save(mchNotifyRecord);
            } catch (Exception e) {
                log.info("数据库已存在[{}]消息，本次不再推送。", mchNotifyRecord.getOrderId());
                return ;
            }

            //推送到MQ
            Long notifyId = mchNotifyRecord.getNotifyId();
            mqSender.send(PayOrderMchNotifyMQ.build(notifyId));

        } catch (Exception e) {
            log.error("推送失败！", e);
        }
    }


    /** 商户通知信息，转账订单的通知接口 **/
    public void transferOrderNotify(TransferOrder dbTransferOrder){

        try {
            // 通知地址为空
            if(StringUtils.isEmpty(dbTransferOrder.getNotifyUrl())){
                return ;
            }

            //获取到通知对象
            MchNotifyRecord mchNotifyRecord = mchNotifyRecordService.findByTransferOrder(dbTransferOrder.getTransferId());

            if(mchNotifyRecord != null){
                log.info("当前已存在通知消息， 不再发送。");
                return ;
            }

            //商户app私钥
            String appSecret = configContextQueryService.queryMchApp(dbTransferOrder.getMchNo(), dbTransferOrder.getAppId()).getAppSecret();

            // 封装通知url
            String notifyUrl = createNotifyUrl(dbTransferOrder, appSecret);
            mchNotifyRecord = new MchNotifyRecord();
            mchNotifyRecord.setOrderId(dbTransferOrder.getTransferId());
            mchNotifyRecord.setOrderType(MchNotifyRecord.TYPE_TRANSFER_ORDER);
            mchNotifyRecord.setMchNo(dbTransferOrder.getMchNo());
            mchNotifyRecord.setMchOrderNo(dbTransferOrder.getMchOrderNo()); //商户订单号
            mchNotifyRecord.setIsvNo(dbTransferOrder.getIsvNo());
            mchNotifyRecord.setAppId(dbTransferOrder.getAppId());
            mchNotifyRecord.setNotifyUrl(notifyUrl);
            mchNotifyRecord.setResResult("");
            mchNotifyRecord.setNotifyCount(0);
            mchNotifyRecord.setState(MchNotifyRecord.STATE_ING); // 通知中

            try {
                mchNotifyRecordService.save(mchNotifyRecord);
            } catch (Exception e) {
                log.info("数据库已存在[{}]消息，本次不再推送。", mchNotifyRecord.getOrderId());
                return ;
            }

            //推送到MQ
            Long notifyId = mchNotifyRecord.getNotifyId();
            mqSender.send(PayOrderMchNotifyMQ.build(notifyId));

        } catch (Exception e) {
            log.error("推送失败！", e);
        }
    }


    /**
     * 创建响应URL
     */
    public String createNotifyUrl(PayOrder payOrder, String appSecret) {

        // 易支付订单：按易支付协议构造通知参数与签名，转发给外部系统的 notify_url
        // extParam 中含 {"epay":true,"pid":...,"epayKey":...,"epayType":...,"epayName":...,"epayNotifyUrl":...}
        JSONObject epayExt = parseEpayExt(payOrder.getExtParam());
        if (epayExt != null) {
            return buildEpayNotifyUrl(payOrder, epayExt);
        }

        QueryPayOrderRS queryPayOrderRS = QueryPayOrderRS.buildByPayOrder(payOrder);
        JSONObject jsonObject = (JSONObject)JSONObject.toJSON(queryPayOrderRS);
        jsonObject.put("reqTime", System.currentTimeMillis()); //添加请求时间

        // 报文签名
        jsonObject.put("sign", JeepayKit.getSign(jsonObject, appSecret));

        // 生成通知
        return StringKit.appendUrlQuery(payOrder.getNotifyUrl(), jsonObject);
    }

    /**
     * 解析订单 extParam，若为易支付订单则返回扩展 JSON，否则返回 null。
     */
    private JSONObject parseEpayExt(String extParam) {
        if (StringUtils.isEmpty(extParam)) {
            return null;
        }
        try {
            JSONObject obj = JSONObject.parseObject(extParam);
            if (obj != null && Boolean.TRUE.equals(obj.getBoolean("epay"))) {
                return obj;
            }
        } catch (Exception e) {
            log.warn("解析 extParam 失败：{}", extParam, e);
        }
        return null;
    }

    /**
     * 构造易支付格式的异步通知 URL。
     * 参数：pid, trade_no, out_trade_no, type, name, money, trade_status, sign, sign_type
     * 通知目标地址取自 extParam.epayNotifyUrl（即 new-api 下单时传入的 notify_url）。
     */
    private String buildEpayNotifyUrl(PayOrder payOrder, JSONObject epayExt) {

        String epayNotifyUrl = epayExt.getString("epayNotifyUrl");
        String epayKey = epayExt.getString("epayKey");
        String pid = epayExt.getString("pid");
        String type = epayExt.getString("epayType");
        String name = epayExt.getString("epayName");

        // trade_status：TRADE_SUCCESS=支付成功，TRADE_CLOSED=交易关闭，WAIT_BUYER_PAY=等待支付
        // 仅订单终态（成功/失败/关闭）才会进入通知，成功用 TRADE_SUCCESS，其余用 TRADE_CLOSED。
        String tradeStatus = (payOrder.getState() == PayOrder.STATE_SUCCESS)
                ? "TRADE_SUCCESS" : "TRADE_CLOSED";

        // 金额：分 → 元（保留两位）
        String money = String.format("%.2f", payOrder.getAmount() / 100.0);

        // 易支付通知参数（顺序无关，签名时按字典序排序）
        JSONObject params = new JSONObject(true);
        params.put("pid", pid);
        params.put("trade_no", payOrder.getPayOrderId());       // 易支付平台订单号 → Jeepay 订单号
        params.put("out_trade_no", payOrder.getMchOrderNo());   // 商户订单号
        params.put("type", type);
        params.put("name", name);
        params.put("money", money);
        params.put("trade_status", tradeStatus);
        params.put("sign_type", EpayKit.SIGN_TYPE);

        // 易支付签名（独立算法）
        params.put("sign", EpayKit.getSign(params, epayKey));

        log.info("易支付通知构造：payOrderId={}, notifyUrl={}, tradeStatus={}", payOrder.getPayOrderId(), epayNotifyUrl, tradeStatus);
        return StringKit.appendUrlQuery(epayNotifyUrl, params);
    }


    /**
     * 创建响应URL
     */
    public String createNotifyUrl(RefundOrder refundOrder, String appSecret) {

        QueryRefundOrderRS queryRefundOrderRS = QueryRefundOrderRS.buildByRefundOrder(refundOrder);
        JSONObject jsonObject = (JSONObject)JSONObject.toJSON(queryRefundOrderRS);
        jsonObject.put("reqTime", System.currentTimeMillis()); //添加请求时间

        // 报文签名
        jsonObject.put("sign", JeepayKit.getSign(jsonObject, appSecret));

        // 生成通知
        return StringKit.appendUrlQuery(refundOrder.getNotifyUrl(), jsonObject);
    }


    /**
     * 创建响应URL
     */
    public String createNotifyUrl(TransferOrder transferOrder, String appSecret) {

        QueryTransferOrderRS rs = QueryTransferOrderRS.buildByRecord(transferOrder);
        JSONObject jsonObject = (JSONObject)JSONObject.toJSON(rs);
        jsonObject.put("reqTime", System.currentTimeMillis()); //添加请求时间

        // 报文签名
        jsonObject.put("sign", JeepayKit.getSign(jsonObject, appSecret));

        // 生成通知
        return StringKit.appendUrlQuery(transferOrder.getNotifyUrl(), jsonObject);
    }


    /**
     * 创建响应URL
     */
    public String createReturnUrl(PayOrder payOrder, String appSecret) {

        if(StringUtils.isEmpty(payOrder.getReturnUrl())){
            return "";
        }

        QueryPayOrderRS queryPayOrderRS = QueryPayOrderRS.buildByPayOrder(payOrder);
        JSONObject jsonObject = (JSONObject)JSONObject.toJSON(queryPayOrderRS);
        jsonObject.put("reqTime", System.currentTimeMillis()); //添加请求时间

        // 报文签名
        jsonObject.put("sign", JeepayKit.getSign(jsonObject, appSecret));   // 签名

        // 生成跳转地址
        return StringKit.appendUrlQuery(payOrder.getReturnUrl(), jsonObject);

    }

}
