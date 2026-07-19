<template>
  <a-drawer
    v-model:open="vdata.open"
    :title="vdata.isAdd ? '新增易支付商户' : '修改易支付商户'"
    width="40%"
    :mask-closable="false"
    @close="onClose"
  >
    <a-form ref="infoFormModel" :model="vdata.saveObject" layout="vertical" :rules="rules">
      <a-row :gutter="16">
        <a-col :span="24">
          <a-form-item label="易支付商户ID (pid) — 对应 new-api 的 EpayId" name="pid">
            <a-input
              v-model:value="vdata.saveObject.pid"
              placeholder="new-api 配置的 EpayId"
              :disabled="!vdata.isAdd"
            />
          </a-form-item>
        </a-col>
        <a-col :span="24">
          <a-form-item label="易支付商户密钥 (appSecret) — 对应 new-api 的 EpayKey" name="appSecret">
            <a-textarea
              v-model:value="vdata.saveObject.appSecret"
              :placeholder="vdata.saveObject.appSecret_ph || 'new-api 配置的 EpayKey'"
            />
            <a-button
              type="primary"
              ghost
              style="margin-top: 10px"
              @click="randomKey(false, 32, 0)"
            >
              <a-icon type="file-sync" />
              随机生成密钥
            </a-button>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="Jeepay 商户号" name="mchNo">
            <a-input v-model:value="vdata.saveObject.mchNo" placeholder="Jeepay 商户号" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="Jeepay 应用ID" name="appId">
            <a-input v-model:value="vdata.saveObject.appId" placeholder="Jeepay 应用ID" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="状态" name="state">
            <a-radio-group v-model:value="vdata.saveObject.state">
              <a-radio :value="1">启用</a-radio>
              <a-radio :value="0">停用</a-radio>
            </a-radio-group>
          </a-form-item>
        </a-col>
        <a-col :span="24">
          <a-form-item label="备注" name="remark">
            <a-textarea v-model:value="vdata.saveObject.remark" placeholder="请输入" />
          </a-form-item>
        </a-col>
      </a-row>
    </a-form>

    <div class="drawer-btn-center">
      <a-button :style="{ marginRight: '8px' }" @click="onClose">取消</a-button>
      <a-button type="primary" @click="onSubmit">保存</a-button>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import { API_URL_EPAY_MERCHANT, req } from '@/api/manage'
import { reactive, ref, getCurrentInstance } from 'vue'

const { $infoBox } = getCurrentInstance()!.appContext.config.globalProperties

const props: any = defineProps({
  callbackFunc: { type: Function, default: () => {} },
})

const infoFormModel = ref()

const vdata: any = reactive({
  isAdd: true,
  open: false,
  id: null,
  saveObject: {},
})

const rules: any = reactive({
  pid: [{ required: true, message: '请输入易支付商户ID', trigger: 'blur' }],
  appSecret: [{ required: true, message: '请输入或生成易支付商户密钥', trigger: 'blur' }],
  mchNo: [{ required: true, message: '请输入 Jeepay 商户号', trigger: 'blur' }],
  appId: [{ required: true, message: '请输入 Jeepay 应用ID', trigger: 'blur' }],
})

function show(id) {
  vdata.isAdd = !id
  vdata.id = id || null
  vdata.saveObject = { state: 1 }

  if (!vdata.isAdd) {
    req.getById(API_URL_EPAY_MERCHANT, id).then((res) => {
      vdata.saveObject = res
      // 编辑时密钥不回显明文，只显示占位提示
      vdata.saveObject.appSecret_ph = res.appSecret
      vdata.saveObject.appSecret = ''
    })
    vdata.open = true
  } else {
    // 新增时 appSecret 必填
    rules.appSecret.push({ required: true, message: '请输入或生成密钥', trigger: 'blur' })
    vdata.open = true
  }
}

function onClose() {
  vdata.open = false
}

function onSubmit() {
  infoFormModel.value.validate().then((valid) => {
    if (valid) {
      delete vdata.saveObject.appSecret_ph
      if (vdata.isAdd) {
        req.add(API_URL_EPAY_MERCHANT, vdata.saveObject).then(() => {
          $infoBox.message.success('新增成功')
          vdata.open = false
          props.callbackFunc()
        })
      } else {
        // 编辑时密钥为空则不更新
        if (vdata.saveObject.appSecret === '') {
          delete vdata.saveObject.appSecret
        }
        req.updateById(API_URL_EPAY_MERCHANT, vdata.id, vdata.saveObject).then(() => {
          $infoBox.message.success('修改成功')
          vdata.open = false
          props.callbackFunc()
        })
      }
    }
  })
}

// 生成随机密钥
function randomKey(randomFlag, min, max) {
  let str = ''
  let range = min
  const arr = [
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
  ]
  if (randomFlag) {
    range = Math.round(Math.random() * (max - min)) + min
  }
  for (let i = 0; i < range; i++) {
    const pos = Math.round(Math.random() * (arr.length - 1))
    str += arr[pos]
  }
  vdata.saveObject.appSecret = str
}

defineExpose({ show })
</script>

<style lang="less" scoped></style>
