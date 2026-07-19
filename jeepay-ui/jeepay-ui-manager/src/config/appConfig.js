export default {
  APP_TITLE: 'Jeepay运营平台',
  ACCESS_TOKEN_NAME: 'iToken'
}

export const asyncRouteDefine = {
  'CurrentUserInfo': { defaultPath: '/current/userinfo', component: () => import('@/views/current/UserinfoPage.vue') },

  'MainPage': { defaultPath: '/main', component: () => import('@/views/dashboard/Analysis.vue') },
  'SysUserPage': { defaultPath: '/users', component: () => import('@/views/sysuser/SysUserPage.vue') },
  'RolePage': { defaultPath: '/roles', component: () => import('@/views/role/RolePage.vue') },
  'EntPage': { defaultPath: '/ents', component: () => import('@/views/ent/EntPage.vue') },
  'PayWayPage': { defaultPath: '/payways', component: () => import('@/views/payconfig/payWay/List.vue') },
  'IfDefinePage': { defaultPath: '/ifdefines', component: () => import('@/views/payconfig/payIfDefine/List.vue') },
  'MchListPage': { defaultPath: '/mch', component: () => import('@/views/mch/MchList.vue') },
  'MchAppPage': { defaultPath: '/apps', component: () => import('@/views/mchApp/List.vue') },
  'PayTestPage': { defaultPath: '/paytest', component: () => import('@/views/payTest/PayTest.vue') },
  'EpayMerchantPage': { defaultPath: '/epayMerchant', component: () => import('@/views/epayMerchant/List.vue') },
  'PayOrderListPage': { defaultPath: '/payOrder', component: () => import('@/views/order/pay/PayOrderList.vue') },
  'RefundOrderListPage': { defaultPath: '/refundOrder', component: () => import('@/views/order/refund/RefundOrderList.vue') },
  'TransferOrderListPage': { defaultPath: '/transferOrder', component: () => import('@/views/order/transfer/TransferOrderList.vue') },
  'MchNotifyListPage': { defaultPath: '/notify', component: () => import('@/views/order/notify/MchNotifyList.vue') },
  'SysConfigPage': { defaultPath: '/config', component: () => import('@/views/sys/config/SysConfig.vue') },
  'SysLogPage': { defaultPath: '/log', component: () => import('@/views/sys/log/SysLog.vue') }
}
