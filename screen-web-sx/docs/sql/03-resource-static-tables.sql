-- ------------------------------------------------------------
-- 百里风区系统 - 资源静态表建表脚本（4.3）
-- 表：publish_facility_static / closure_device_static / duty_staff_static / duty_team_static
-- 数据库：MySQL 8.x
-- ------------------------------------------------------------

SET NAMES utf8mb4;

-- ============================================================
-- 4.3.1 信息发布设施静态表
-- ============================================================
CREATE TABLE IF NOT EXISTS publish_facility_static (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    facility_id VARCHAR(64) NOT NULL COMMENT '设施ID',
    pile_no VARCHAR(32) NOT NULL COMMENT '桩号',
    direction TINYINT NOT NULL COMMENT '方向(1哈密/2吐鲁番)',
    facility_type VARCHAR(64) NOT NULL COMMENT '设施类型',
    segment VARCHAR(128) NOT NULL COMMENT '所属区段',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    is_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用(1启用/0停用)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_publish_facility_static_facility_id (facility_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信息发布设施静态表';

INSERT INTO publish_facility_static (facility_id, pile_no, direction, facility_type, segment, sort_no, is_enabled)
SELECT 't3ghjd1', 'K3180', 2, '可变信息标志', '主线', 10, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM publish_facility_static WHERE facility_id = 't3ghjd1');

INSERT INTO publish_facility_static (facility_id, pile_no, direction, facility_type, segment, sort_no, is_enabled)
SELECT 't3ghjd2', 'K3191+800', 2, '可变信息标志', '红山口服务区前', 20, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM publish_facility_static WHERE facility_id = 't3ghjd2');

INSERT INTO publish_facility_static (facility_id, pile_no, direction, facility_type, segment, sort_no, is_enabled)
SELECT 't3ghjd3', 'K3196+450', 2, '可变信息标志', '红山口互通前', 30, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM publish_facility_static WHERE facility_id = 't3ghjd3');

INSERT INTO publish_facility_static (facility_id, pile_no, direction, facility_type, segment, sort_no, is_enabled)
SELECT 'h3ghjd1', 'K3180', 1, '可变信息标志', '主线', 110, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM publish_facility_static WHERE facility_id = 'h3ghjd1');

INSERT INTO publish_facility_static (facility_id, pile_no, direction, facility_type, segment, sort_no, is_enabled)
SELECT 'h3ghjd2', 'K3194+515', 1, '可变信息标志', '红山口服务区前', 120, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM publish_facility_static WHERE facility_id = 'h3ghjd2');

INSERT INTO publish_facility_static (facility_id, pile_no, direction, facility_type, segment, sort_no, is_enabled)
SELECT 'h3ghjd3', 'K3199+500', 1, '可变信息标志', '红山口互通前', 130, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM publish_facility_static WHERE facility_id = 'h3ghjd3');


-- ============================================================
-- 4.3.2 封路设备静态表
-- ============================================================
CREATE TABLE IF NOT EXISTS closure_device_static (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    device_id VARCHAR(64) NOT NULL COMMENT '设备ID',
    warehouse VARCHAR(128) NOT NULL COMMENT '仓库位置',
    device_type VARCHAR(255) NOT NULL COMMENT '设备类型',
    quantity INT NOT NULL DEFAULT 0 COMMENT '数量',
    available TINYINT NOT NULL DEFAULT 0 COMMENT '是否可用(1可用/0不可用)',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    is_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用(1启用/0停用)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_closure_device_static_device_id (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='封路设备静态表';

INSERT INTO closure_device_static (device_id, warehouse, device_type, quantity, available, sort_no, is_enabled)
SELECT 'CD-001', '交警队仓库', '三角锥、防撞桶、限速标识', 10, 0, 10, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM closure_device_static WHERE device_id = 'CD-001');

INSERT INTO closure_device_static (device_id, warehouse, device_type, quantity, available, sort_no, is_enabled)
SELECT 'CD-002', '服务区南仓库', '三角锥、防撞桶', 10, 1, 20, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM closure_device_static WHERE device_id = 'CD-002');

INSERT INTO closure_device_static (device_id, warehouse, device_type, quantity, available, sort_no, is_enabled)
SELECT 'CD-003', '服务区北仓库', '三角锥、防撞桶', 10, 1, 30, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM closure_device_static WHERE device_id = 'CD-003');


-- ============================================================
-- 4.3.3 执勤人员静态表
-- ============================================================
CREATE TABLE IF NOT EXISTS duty_staff_static (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    staff_id VARCHAR(64) NOT NULL COMMENT '人员ID',
    name VARCHAR(64) NOT NULL COMMENT '姓名',
    on_duty TINYINT NOT NULL DEFAULT 0 COMMENT '是否在岗(1在岗/0不在岗)',
    team_id VARCHAR(64) DEFAULT '' COMMENT '所属班组ID',
    phone VARCHAR(32) DEFAULT '' COMMENT '联系电话',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    is_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用(1启用/0停用)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_duty_staff_static_staff_id (staff_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执勤人员静态表';

INSERT INTO duty_staff_static (staff_id, name, on_duty, team_id, phone, sort_no, is_enabled)
SELECT 'Staff001', '张三', 1, 'TEAM-1', '15676648462', 10, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM duty_staff_static WHERE staff_id = 'Staff001');

INSERT INTO duty_staff_static (staff_id, name, on_duty, team_id, phone, sort_no, is_enabled)
SELECT 'Staff002', '张四', 1, 'TEAM-1', '15676648463', 20, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM duty_staff_static WHERE staff_id = 'Staff002');

INSERT INTO duty_staff_static (staff_id, name, on_duty, team_id, phone, sort_no, is_enabled)
SELECT 'Staff003', '张五', 1, 'TEAM-1', '15676648464', 30, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM duty_staff_static WHERE staff_id = 'Staff003');

INSERT INTO duty_staff_static (staff_id, name, on_duty, team_id, phone, sort_no, is_enabled)
SELECT 'Staff004', '张六', 0, '', '15676648465', 40, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM duty_staff_static WHERE staff_id = 'Staff004');

INSERT INTO duty_staff_static (staff_id, name, on_duty, team_id, phone, sort_no, is_enabled)
SELECT 'Staff005', '张七', 0, '', '15676648466', 50, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM duty_staff_static WHERE staff_id = 'Staff005');


-- ============================================================
-- 4.3.4 执勤班组静态表
-- ============================================================
CREATE TABLE IF NOT EXISTS duty_team_static (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    team_id VARCHAR(64) NOT NULL COMMENT '班组ID',
    name VARCHAR(64) NOT NULL COMMENT '班组名称',
    leader_id VARCHAR(64) DEFAULT '' COMMENT '组长人员ID',
    node VARCHAR(128) DEFAULT '' COMMENT '驻点',
    dispatch_state VARCHAR(32) NOT NULL DEFAULT 'READY' COMMENT '调度状态',
    member_ids VARCHAR(512) DEFAULT '' COMMENT '成员ID列表(JSON或CSV)',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    is_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用(1启用/0停用)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_duty_team_static_team_id (team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执勤班组静态表';

INSERT INTO duty_team_static (team_id, name, leader_id, node, dispatch_state, member_ids, sort_no, is_enabled)
SELECT 'TEAM-1', '班组1', 'Staff001', '红山口互通南', 'READY', '["Staff001","Staff002","Staff003"]', 10, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM duty_team_static WHERE team_id = 'TEAM-1');

INSERT INTO duty_team_static (team_id, name, leader_id, node, dispatch_state, member_ids, sort_no, is_enabled)
SELECT 'TEAM-2', '班组2', '', '红山口服务区南', 'READY', '[]', 20, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM duty_team_static WHERE team_id = 'TEAM-2');

INSERT INTO duty_team_static (team_id, name, leader_id, node, dispatch_state, member_ids, sort_no, is_enabled)
SELECT 'TEAM-3', '班组3', '', '红山口服务区北', 'READY', '[]', 30, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM duty_team_static WHERE team_id = 'TEAM-3');
