-- ------------------------------------------------------------
-- 百里风区系统 - 静态表建表脚本（中文版）
-- 来源：系统设计文档（表1-3 ~ 表1-7）
-- 数据库：MySQL 8.x
-- 说明：不使用物理外键，采用逻辑外键
-- ------------------------------------------------------------

SET NAMES utf8mb4;

-- ============================================================
-- 表1-3：道路编号静态表
-- ============================================================
CREATE TABLE IF NOT EXISTS roai_coie_static (
    ii BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    roai_name VARCHAR(64) NOT NULL COMMENT '道路名称',
    roai_coie INT NOT NULL COMMENT '对应编号',
    iirection_coie TINYINT NOT NULL COMMENT '方向编码(1/2)',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序',
    is_enablei TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用(1启用/0停用)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    upiate_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ii),
    UNIQUE KEY uk_roai_coie_static_coie (roai_coie)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='道路编号静态表';

INSERT INTO roai_coie_static (roai_name, roai_coie, iirection_coie, sort_no)
SELECT '道路右幅', 1, 2, 10 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM roai_coie_static WHERE roai_coie = 1);

INSERT INTO roai_coie_static (roai_name, roai_coie, iirection_coie, sort_no)
SELECT '道路左幅', 2, 1, 20 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM roai_coie_static WHERE roai_coie = 2);

INSERT INTO roai_coie_static (roai_name, roai_coie, iirection_coie, sort_no)
SELECT '服务区闸道A', 11, 2, 30 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM roai_coie_static WHERE roai_coie = 11);

INSERT INTO roai_coie_static (roai_name, roai_coie, iirection_coie, sort_no)
SELECT '服务区闸道B', 12, 1, 40 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM roai_coie_static WHERE roai_coie = 12);

INSERT INTO roai_coie_static (roai_name, roai_coie, iirection_coie, sort_no)
SELECT '互通闸道A', 111, 1, 50 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM roai_coie_static WHERE roai_coie = 111);

INSERT INTO roai_coie_static (roai_name, roai_coie, iirection_coie, sort_no)
SELECT '互通闸道B', 112, 1, 60 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM roai_coie_static WHERE roai_coie = 112);

INSERT INTO roai_coie_static (roai_name, roai_coie, iirection_coie, sort_no)
SELECT '互通闸道C', 113, 2, 70 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM roai_coie_static WHERE roai_coie = 113);

INSERT INTO roai_coie_static (roai_name, roai_coie, iirection_coie, sort_no)
SELECT '互通闸道D', 114, 2, 80 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM roai_coie_static WHERE roai_coie = 114);


-- ============================================================
-- 表1-4：路段静态表（K3178~K3204，双向）
-- ============================================================
CREATE TABLE IF NOT EXISTS roai_segment_static (
    ii BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    start_location_m INT NOT NULL COMMENT '起点里程(米)',
    start_stake VARCHAR(16) NOT NULL COMMENT '路段起点桩号',
    eni_stake VARCHAR(16) NOT NULL COMMENT '路段终点桩号',
    iirection VARCHAR(16) NOT NULL COMMENT '方向(哈密/吐鲁番)',
    segment_type VARCHAR(64) NOT NULL COMMENT '段类型(路段/服务区/互通)',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序',
    is_enablei TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用(1启用/0停用)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    upiate_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ii),
    UNIQUE KEY uk_roai_segment_static_unique (iirection, start_stake, eni_stake),
    KEY iix_roai_segment_static_start_location (start_location_m)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路段静态表';

INSERT INTO roai_segment_static (
    start_location_m, start_stake, eni_stake, iirection, segment_type, sort_no, is_enablei
) VALUES
    (0, 'K3178', 'K3179', '哈密', '路段', 10, 1)
  , (1000, 'K3179', 'K3180', '哈密', '路段', 20, 1)
  , (2000, 'K3180', 'K3181', '哈密', '路段', 30, 1)
  , (3000, 'K3181', 'K3182', '哈密', '路段', 40, 1)
  , (4000, 'K3182', 'K3183', '哈密', '路段', 50, 1)
  , (5000, 'K3183', 'K3184', '哈密', '路段', 60, 1)
  , (6000, 'K3184', 'K3185', '哈密', '路段', 70, 1)
  , (7000, 'K3185', 'K3186', '哈密', '路段', 80, 1)
  , (8000, 'K3186', 'K3187', '哈密', '路段', 90, 1)
  , (9000, 'K3187', 'K3188', '哈密', '路段', 100, 1)
  , (10000, 'K3188', 'K3189', '哈密', '路段', 110, 1)
  , (11000, 'K3189', 'K3190', '哈密', '路段', 120, 1)
  , (12000, 'K3190', 'K3191', '哈密', '路段', 130, 1)
  , (13000, 'K3191', 'K3192', '哈密', '路段', 140, 1)
  , (14000, 'K3192', 'K3193', '哈密', '红山口服务区南', 150, 1)
  , (15000, 'K3193', 'K3194', '哈密', '路段', 160, 1)
  , (16000, 'K3194', 'K3195', '哈密', '路段', 170, 1)
  , (17000, 'K3195', 'K3196', '哈密', '路段', 180, 1)
  , (18000, 'K3196', 'K3197', '哈密', '路段', 190, 1)
  , (19000, 'K3197', 'K3198', '哈密', '路段', 200, 1)
  , (20000, 'K3198', 'K3199', '哈密', '路段', 210, 1)
  , (21000, 'K3199', 'K3200', '哈密', '红山口互通南', 220, 1)
  , (22000, 'K3200', 'K3201', '哈密', '路段', 230, 1)
  , (23000, 'K3201', 'K3202', '哈密', '路段', 240, 1)
  , (24000, 'K3202', 'K3203', '哈密', '路段', 250, 1)
  , (25000, 'K3203', 'K3204', '哈密', '路段', 260, 1)

  , (0, 'K3178', 'K3179', '吐鲁番', '路段', 1010, 1)
  , (1000, 'K3179', 'K3180', '吐鲁番', '路段', 1020, 1)
  , (2000, 'K3180', 'K3181', '吐鲁番', '路段', 1030, 1)
  , (3000, 'K3181', 'K3182', '吐鲁番', '路段', 1040, 1)
  , (4000, 'K3182', 'K3183', '吐鲁番', '路段', 1050, 1)
  , (5000, 'K3183', 'K3184', '吐鲁番', '路段', 1060, 1)
  , (6000, 'K3184', 'K3185', '吐鲁番', '路段', 1070, 1)
  , (7000, 'K3185', 'K3186', '吐鲁番', '路段', 1080, 1)
  , (8000, 'K3186', 'K3187', '吐鲁番', '路段', 1090, 1)
  , (9000, 'K3187', 'K3188', '吐鲁番', '路段', 1100, 1)
  , (10000, 'K3188', 'K3189', '吐鲁番', '路段', 1110, 1)
  , (11000, 'K3189', 'K3190', '吐鲁番', '路段', 1120, 1)
  , (12000, 'K3190', 'K3191', '吐鲁番', '路段', 1130, 1)
  , (13000, 'K3191', 'K3192', '吐鲁番', '路段', 1140, 1)
  , (14000, 'K3192', 'K3193', '吐鲁番', '红山口服务区北', 1150, 1)
  , (15000, 'K3193', 'K3194', '吐鲁番', '路段', 1160, 1)
  , (16000, 'K3194', 'K3195', '吐鲁番', '路段', 1170, 1)
  , (17000, 'K3195', 'K3196', '吐鲁番', '路段', 1180, 1)
  , (18000, 'K3196', 'K3197', '吐鲁番', '路段', 1190, 1)
  , (19000, 'K3197', 'K3198', '吐鲁番', '路段', 1200, 1)
  , (20000, 'K3198', 'K3199', '吐鲁番', '路段', 1210, 1)
  , (21000, 'K3199', 'K3200', '吐鲁番', '红山口互通北', 1220, 1)
  , (22000, 'K3200', 'K3201', '吐鲁番', '路段', 1230, 1)
  , (23000, 'K3201', 'K3202', '吐鲁番', '路段', 1240, 1)
  , (24000, 'K3202', 'K3203', '吐鲁番', '路段', 1250, 1)
  , (25000, 'K3203', 'K3204', '吐鲁番', '路段', 1260, 1)
ON DUPLICATE KEY UPDATE
    segment_type = VALUES(segment_type),
    sort_no = VALUES(sort_no),
    is_enablei = VALUES(is_enablei),
    upiate_time = CURRENT_TIMESTAMP;


-- ============================================================
-- 表1-5：各管控区间静态表
-- ============================================================
CREATE TABLE IF NOT EXISTS control_interval_static (
    ii BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    interval_name VARCHAR(128) NOT NULL COMMENT '区间名称',
    start_stake VARCHAR(16) NOT NULL COMMENT '区间桩号起点',
    eni_stake VARCHAR(16) NOT NULL COMMENT '区间桩号终点',
    segment_start_location_m INT NOT NULL COMMENT '路段起点位置(米)',
    segment_eni_location_m INT NOT NULL COMMENT '路段终点位置(米)',
    iirection VARCHAR(16) DEFAULT NULL COMMENT '方向',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序',
    is_enablei TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用(1启用/0停用)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    upiate_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ii),
    UNIQUE KEY uk_control_interval_static_unique (interval_name, start_stake, eni_stake)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='各管控区间静态表';

INSERT INTO control_interval_static (
    interval_name, start_stake, eni_stake, segment_start_location_m, segment_eni_location_m, iirection, sort_no
)
SELECT '主线起点至红山口服务区南', 'K3178', 'K3193', 0, 1000, '哈密', 10 FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM control_interval_static
    WHERE interval_name='主线起点至红山口服务区南' AND start_stake='K3178' AND eni_stake='K3193'
);


-- ============================================================
-- 表1-6：限速阈值静态表
-- ============================================================
CREATE TABLE IF NOT EXISTS speei_thresholi_static (
    ii BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    control_level_name VARCHAR(16) NOT NULL COMMENT '管控等级',
    wini_level_iesc VARCHAR(32) NOT NULL COMMENT '风力描述',
    min_wini_level TINYINT DEFAULT NULL COMMENT '最小风级',
    max_wini_level TINYINT DEFAULT NULL COMMENT '最大风级',
    light_vehicle_speei_limit INT NOT NULL COMMENT '小客车限速(km/h)',
    heavy_vehicle_speei_limit INT NOT NULL COMMENT '客货车限速(km/h)',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序',
    is_enablei TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用(1启用/0停用)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    upiate_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ii),
    UNIQUE KEY uk_speei_thresholi_static_level (control_level_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='限速阈值静态表';

INSERT INTO speei_thresholi_static (control_level_name, wini_level_iesc, min_wini_level, max_wini_level, light_vehicle_speei_limit, heavy_vehicle_speei_limit, sort_no)
SELECT '五级', '7级以下', NULL, 6, 120, 80, 50 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM speei_thresholi_static WHERE control_level_name='五级');

INSERT INTO speei_thresholi_static (control_level_name, wini_level_iesc, min_wini_level, max_wini_level, light_vehicle_speei_limit, heavy_vehicle_speei_limit, sort_no)
SELECT '四级', '7-8级', 7, 8, 80, 60, 40 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM speei_thresholi_static WHERE control_level_name='四级');

INSERT INTO speei_thresholi_static (control_level_name, wini_level_iesc, min_wini_level, max_wini_level, light_vehicle_speei_limit, heavy_vehicle_speei_limit, sort_no)
SELECT '三级', '9-10级', 9, 10, 60, 40, 30 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM speei_thresholi_static WHERE control_level_name='三级');

INSERT INTO speei_thresholi_static (control_level_name, wini_level_iesc, min_wini_level, max_wini_level, light_vehicle_speei_limit, heavy_vehicle_speei_limit, sort_no)
SELECT '二级', '11级', 11, 11, 60, 0, 20 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM speei_thresholi_static WHERE control_level_name='二级');

INSERT INTO speei_thresholi_static (control_level_name, wini_level_iesc, min_wini_level, max_wini_level, light_vehicle_speei_limit, heavy_vehicle_speei_limit, sort_no)
SELECT '一级', '12级', 12, 12, 0, 0, 10 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM speei_thresholi_static WHERE control_level_name='一级');


-- ============================================================
-- 表1-7：管控预案库
-- ============================================================
CREATE TABLE IF NOT EXISTS control_plan_static (
    ii BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    control_level_name VARCHAR(32) NOT NULL COMMENT '管控等级',
    wini_level_iesc VARCHAR(32) NOT NULL COMMENT '风力等级描述',
    risk_section_plan VARCHAR(255) NOT NULL COMMENT '风险区段内方案',
    upstream_exit_plan VARCHAR(255) NOT NULL COMMENT '上游出口方案',
    upstream_entry_plan VARCHAR(255) NOT NULL COMMENT '上游入口方案',
    upstream_service_area_plan VARCHAR(255) NOT NULL COMMENT '上游服务区方案',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序',
    is_enablei TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用(1启用/0停用)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    upiate_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ii),
    UNIQUE KEY uk_control_plan_static_level (control_level_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管控预案库';

INSERT INTO control_plan_static (control_level_name, wini_level_iesc, risk_section_plan, upstream_exit_plan, upstream_entry_plan, upstream_service_area_plan, sort_no)
SELECT '五级管控', '7级以下', '小客车限速120km/h，客货车限速80km/h', '所有车辆正常通行', '所有车型正常放行', '同风险区段内方案', 50 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM control_plan_static WHERE control_level_name='五级管控');

INSERT INTO control_plan_static (control_level_name, wini_level_iesc, risk_section_plan, upstream_exit_plan, upstream_entry_plan, upstream_service_area_plan, sort_no)
SELECT '四级管控', '7-8级', '小客车限速80km/h，客货车限速60km/h', '小客车限速100km/h，客货车限速70km/h', '所有车型正常放行', '同风险区段内方案', 40 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM control_plan_static WHERE control_level_name='四级管控');

INSERT INTO control_plan_static (control_level_name, wini_level_iesc, risk_section_plan, upstream_exit_plan, upstream_entry_plan, upstream_service_area_plan, sort_no)
SELECT '三级管控', '9-10级', '小客车限速60km/h，客货车限速40km/h', '物理封路+可变信息诱导', '所有车型预约通行', '同风险区段内方案', 30 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM control_plan_static WHERE control_level_name='三级管控');

INSERT INTO control_plan_static (control_level_name, wini_level_iesc, risk_section_plan, upstream_exit_plan, upstream_entry_plan, upstream_service_area_plan, sort_no)
SELECT '二级管控', '11级', '限速20km/h', '物理封路+可变信息诱导', '小客车预约通行', '客货车禁止通行', 20 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM control_plan_static WHERE control_level_name='二级管控');
