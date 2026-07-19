-- Jeepay Lite mode for single-admin self hosting.
-- This file hides menus that are unnecessary for a single operator setup.
-- It is mounted as /docker-entrypoint-initdb.d/03-lite.sql for new MySQL volumes.
-- For an existing database, run this file manually once.

-- Manager: no service-provider mode.
update t_sys_entitlement
set state = 0
where sys_type = 'MGR'
  and ent_id like 'ENT_ISV%';

-- Manager: add a payment test page to the single UI.
insert into t_sys_entitlement values
('ENT_MGR_PAY_TEST', '支付测试', 'transaction', '/paytest', 'PayTestPage', 'ML', 0, 1, 'ENT_MCH', '30', 'MGR', now(), now()),
('ENT_MGR_PAY_TEST_PAYWAY_LIST', '页面：获取支付方式', 'no-icon', '', '', 'PB', 0, 1, 'ENT_MGR_PAY_TEST', '0', 'MGR', now(), now()),
('ENT_MGR_PAY_TEST_DO', '按钮：支付测试', 'no-icon', '', '', 'PB', 0, 1, 'ENT_MGR_PAY_TEST', '0', 'MGR', now(), now())
on duplicate key update
  ent_name = values(ent_name),
  menu_icon = values(menu_icon),
  menu_uri = values(menu_uri),
  component_name = values(component_name),
  ent_type = values(ent_type),
  quick_jump = values(quick_jump),
  state = values(state),
  pid = values(pid),
  ent_sort = values(ent_sort),
  updated_at = now();

insert ignore into t_sys_role_ent_rela(role_id, ent_id)
values
('ROLE_ADMIN', 'ENT_MGR_PAY_TEST'),
('ROLE_ADMIN', 'ENT_MGR_PAY_TEST_PAYWAY_LIST'),
('ROLE_ADMIN', 'ENT_MGR_PAY_TEST_DO');

-- Manager and merchant: hide transfer order pages.
update t_sys_entitlement
set state = 0
where ent_id like 'ENT_TRANSFER_ORDER%';

-- Merchant: hide division menus. The merchant service is not started in Lite mode.
update t_sys_entitlement
set state = 0
where sys_type = 'MCH'
  and ent_id like 'ENT_DIVISION%';

-- Manager: keep the system settings page, but hide user/role/permission management.
update t_sys_entitlement
set state = 0
where sys_type = 'MGR'
  and ent_id like 'ENT_UR%';

-- ===== 易支付(epay)兼容接口：易支付商户映射表 =====
-- 用于把 new-api 等仅支持易支付协议的系统接入 Jeepay。
-- pid/app_secret 对应 new-api 配置的 EpayId/EpayKey，mch_no/app_id 对应 Jeepay 商户应用。
CREATE TABLE IF NOT EXISTS `t_epay_merchant` (
  `id`          BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `pid`         VARCHAR(64)  NOT NULL COMMENT '易支付商户ID（new-api 的 EpayId）',
  `app_secret`  VARCHAR(128) NOT NULL COMMENT '易支付商户密钥（new-api 的 EpayKey）',
  `mch_no`      VARCHAR(64)  NOT NULL COMMENT '对应 Jeepay 商户号',
  `app_id`      VARCHAR(64)  NOT NULL COMMENT '对应 Jeepay 应用ID',
  `state`       TINYINT(6)   NOT NULL DEFAULT 1 COMMENT '状态: 0-停用, 1-正常',
  `remark`      VARCHAR(128) DEFAULT NULL COMMENT '备注',
  `created_at`  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pid` (`pid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='易支付商户映射表';

-- 易支付商户管理菜单（挂在「商户管理 ENT_MCH」下）
insert into t_sys_entitlement values
('ENT_EPAY_MERCHANT',       '易支付商户', 'block', '/epayMerchant', 'EpayMerchantPage', 'ML', 0, 1, 'ENT_MCH', '40', 'MGR', now(), now()),
('ENT_EPAY_MERCHANT_LIST',  '页面：易支付商户列表', 'no-icon', '', '', 'PB', 0, 1, 'ENT_EPAY_MERCHANT', '0', 'MGR', now(), now()),
('ENT_EPAY_MERCHANT_ADD',   '按钮：新增', 'no-icon', '', '', 'PB', 0, 1, 'ENT_EPAY_MERCHANT', '0', 'MGR', now(), now()),
('ENT_EPAY_MERCHANT_EDIT',  '按钮：编辑', 'no-icon', '', '', 'PB', 0, 1, 'ENT_EPAY_MERCHANT', '0', 'MGR', now(), now()),
('ENT_EPAY_MERCHANT_DEL',   '按钮：删除', 'no-icon', '', '', 'PB', 0, 1, 'ENT_EPAY_MERCHANT', '0', 'MGR', now(), now())
on duplicate key update
  ent_name = values(ent_name),
  menu_icon = values(menu_icon),
  menu_uri = values(menu_uri),
  component_name = values(component_name),
  ent_type = values(ent_type),
  state = values(state),
  pid = values(pid),
  ent_sort = values(ent_sort),
  updated_at = now();

insert ignore into t_sys_role_ent_rela(role_id, ent_id) values
('ROLE_ADMIN','ENT_EPAY_MERCHANT'),
('ROLE_ADMIN','ENT_EPAY_MERCHANT_LIST'),
('ROLE_ADMIN','ENT_EPAY_MERCHANT_ADD'),
('ROLE_ADMIN','ENT_EPAY_MERCHANT_EDIT'),
('ROLE_ADMIN','ENT_EPAY_MERCHANT_DEL');

