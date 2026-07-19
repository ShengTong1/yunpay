<template>
  <page-header-wrapper>
    <a-card>
      <div class="table-page-search-wrapper">
        <a-form layout="inline" class="table-head-ground">
          <div class="table-layer">
            <jeepay-text-up v-model:value="vdata.searchData.pid" placeholder="易支付商户ID(pid)" />
            <jeepay-text-up v-model:value="vdata.searchData.mchNo" placeholder="Jeepay商户号" />
            <jeepay-text-up v-model:value="vdata.searchData.appId" placeholder="Jeepay应用ID" />
            <a-select
              v-model:value="vdata.searchData.state"
              placeholder="状态"
              class="table-head-layout"
            >
              <a-select-option value="">全部</a-select-option>
              <a-select-option value="0">禁用</a-select-option>
              <a-select-option value="1">启用</a-select-option>
            </a-select>
            <span class="table-page-search-submitButtons" style="flex-grow: 0; flex-shrink: 0">
              <a-button type="primary" :loading="vdata.btnLoading" @click="queryFunc">
                查询
              </a-button>
              <a-button style="margin-left: 8px" @click="() => (vdata.searchData = {})">
                重置
              </a-button>
            </span>
          </div>
        </a-form>
      </div>

      <!-- 列表渲染 -->
      <JeepayTable
        ref="infoTable"
        :init-data="false"
        :req-table-data-func="reqTableDataFunc"
        :table-columns="tableColumns"
        :search-data="vdata.searchData"
        row-key="id"
        @btn-load-close="vdata.btnLoading = false"
      >
        <template #opRow>
          <a-button
            v-if="$access('ENT_EPAY_MERCHANT_ADD')"
            type="primary"
            class="mg-b-30"
            @click="addFunc"
          >
            新建
          </a-button>
        </template>

        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'pid'">
            <b>{{ record.pid }}</b>
          </template>
          <template v-if="column.key === 'state'">
            <a-badge
              :status="record.state === 0 ? 'error' : 'processing'"
              :text="record.state === 0 ? '禁用' : '启用'"
            />
          </template>
          <template v-if="column.key === 'op'">
            <JeepayTableColumns>
              <a-button
                v-if="$access('ENT_EPAY_MERCHANT_EDIT')"
                type="link"
                @click="editFunc(record.id)"
              >
                修改
              </a-button>
              <a-button
                v-if="$access('ENT_EPAY_MERCHANT_DEL')"
                type="link"
                style="color: red"
                @click="delFunc(record.id)"
              >
                删除
              </a-button>
            </JeepayTableColumns>
          </template>
        </template>
      </JeepayTable>
    </a-card>
    <!-- 新增/修改 -->
    <EpayMerchantAddOrEdit ref="addOrEdit" :callback-func="searchFunc" />
  </page-header-wrapper>
</template>

<script setup lang="ts">
import { API_URL_EPAY_MERCHANT, req } from '@/api/manage'
import EpayMerchantAddOrEdit from './AddOrEdit.vue'
import { ref, reactive, onMounted, getCurrentInstance } from 'vue'

const { $infoBox, $access } = getCurrentInstance()!.appContext.config.globalProperties

const infoTable = ref()
const addOrEdit = ref()

const tableColumns = reactive([
  { key: 'pid', fixed: 'left', width: '200px', title: '易支付商户ID(pid)' },
  { key: 'mchNo', title: 'Jeepay商户号', dataIndex: 'mchNo' },
  { key: 'appId', title: 'Jeepay应用ID', dataIndex: 'appId' },
  { key: 'remark', title: '备注', dataIndex: 'remark' },
  { key: 'state', title: '状态' },
  { key: 'createdAt', dataIndex: 'createdAt', title: '创建日期' },
  { key: 'op', title: '操作', width: '180px', fixed: 'right', align: 'center' },
])

const vdata: any = reactive({
  btnLoading: false,
  tableColumns: tableColumns,
  searchData: {},
})

onMounted(() => {
  queryFunc()
})

function queryFunc() {
  vdata.btnLoading = true
  infoTable.value.refTable(true)
}

const reqTableDataFunc = (params) => {
  return req.list(API_URL_EPAY_MERCHANT, params)
}

function searchFunc() {
  infoTable.value.refTable(true)
}

function addFunc() {
  addOrEdit.value.show()
}

function editFunc(recordId) {
  addOrEdit.value.show(recordId)
}

function delFunc(id) {
  $infoBox.confirmDanger('确认删除？', '删除后该易支付商户将无法下单', () => {
    req.delById(API_URL_EPAY_MERCHANT, id).then(() => {
      $infoBox.message.success('删除成功！')
      searchFunc()
    })
  })
}
</script>

<style lang="less" scoped></style>
