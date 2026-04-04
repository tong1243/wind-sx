# 风区管控系统 RESTful 接口文档（4.1 - 4.5）

## 通用说明
- Base URL: `http://localhost:12700`
- Header:
  - `Content-Type: application/json`
  - `Authorization: Bearer <JWT>`

统一返回结构（数据）:
```json
{
  "code": 200,
  "flag": true,
  "info": "xxx",
  "data": {}
}
```

统一返回结构（消息）:
```json
{
  "code": 200,
  "flag": true,
  "info": "xxx",
  "msg": "ok"
}
```

---

## 4.1 路段运行状态

### 1) 获取路段运行总览
- `GET /api/v1/road-statuses`
- Query:
  - `timestamp` `long` 必填，毫秒时间戳

### 2) 获取服务区车辆统计
- `GET /api/v1/service-areas`
- Query:
  - `timestamp` `long` 必填

### 3) 获取交通状态分析
- `GET /api/v1/traffic-states`
- Query:
  - `timestamp` `long` 必填

---

## 4.2 大风时空影响

### 1) 获取全线风力可视化
- `GET /api/v1/wind-sections`
- Query:
  - `timestamp` `long` 必填
  - `mode` `string` 可选：`real` | `forecast` | `max72h`

### 2) 获取风力限速阈值
- `GET /api/v1/wind-speed-thresholds`

### 3) 更新某风级限速阈值
- `PUT /api/v1/wind-speed-thresholds/{windLevel}`
- Path:
  - `windLevel` `int` 必填，1~12
- Body:
```json
{
  "passengerSpeedLimit": 55,
  "freightSpeedLimit": 45,
  "dangerousGoodsSpeedLimit": 35
}
```
- Body 字段校验:
  - 三个限速字段均必填，范围 `0~150`

### 4) 获取风力时空影响判断
- `GET /api/v1/wind-impacts/spatiotemporal`
- Query:
  - `timestamp` `long` 必填

### 5) 查询大风观测/预测数据
- `GET /api/v1/wind-observations`
- Query:
  - `timestamp` `long` 必填
  - `period` `string` 可选：`real` | `history` | `forecast`
  - `direction` `string` 可选：`toWH` | `toEZ`

### 6) 获取阻断时长预测
- `GET /api/v1/block-duration-forecasts`
- Query:
  - `timestamp` `long` 必填

---

## 4.3 人员与设备信息库

### 1) 信息发布设施
- `GET /api/v1/publish-facilities`
- `PUT /api/v1/publish-facilities/{facilityId}`
- `DELETE /api/v1/publish-facilities/{facilityId}`

### 2) 封路设备
- `GET /api/v1/closure-devices`
- `PUT /api/v1/closure-devices/{deviceId}`
- `DELETE /api/v1/closure-devices/{deviceId}`

### 3) 执勤人员
- `GET /api/v1/staff`
- `PUT /api/v1/staff/{staffId}`
- `DELETE /api/v1/staff/{staffId}`

### 4) 执勤班组
- `GET /api/v1/teams`
- `PUT /api/v1/teams/{teamId}`

### 5) 班组成员编组
- `PUT /api/v1/teams/{teamId}/members`
- Body:
```json
{
  "memberIds": ["ST-01", "ST-02"]
}
```

---

## 4.4 管控预案库

### 1) 获取管控原则
- `GET /api/v1/control-principles`

### 2) 获取管控预案列表
- `GET /api/v1/control-plans`

### 3) 更新某等级管控预案
- `PUT /api/v1/control-plans/{level}`
- Body（可选字段）:
```json
{
  "minWindLevel": 7,
  "maxWindLevel": 8,
  "passengerSpeedLimit": 60,
  "freightSpeedLimit": 50,
  "description": "..."
}
```

### 4) 获取 VMS 发布内容
- `GET /api/v1/vms-contents`

### 5) 更新 VMS 发布内容
- `PUT /api/v1/vms-contents/{level}`
- Body:
```json
{
  "content": "大风预警，请减速慢行"
}
```

### 6) 获取人员设备调用预案
- `GET /api/v1/dispatch-plans`

### 7) 更新人员设备调用预案
- `PUT /api/v1/dispatch-plans/{segment}`
- Body（可选字段）:
```json
{
  "contactStaff": "ST-01",
  "teamId": "T-01",
  "warehouse": "Hongshan Exit"
}
```

---

## 4.5 方案自动生成发布

### 1) 获取管控执行流程
- `GET /api/v1/control-flows`

### 2) 生成管控方案
- `POST /api/v1/control-plans`
- Body:
```json
{
  "timestamp": 1712140800000,
  "segment": "HS-G30 K3020-K3030",
  "realtimeWindLevel": 9,
  "forecastMaxWindLevel": 10
}
```

### 3) 更新管控方案状态（发布）
- `PATCH /api/v1/control-plans/{planId}`
- Body:
```json
{
  "status": "PUBLISHED"
}
```
- 说明:
  - 当前仅支持 `PUBLISHED`

### 4) 获取自动更新推荐
- `GET /api/v1/control-plan-recommendations`
- Query:
  - `timestamp` `long` 必填

### 5) 获取大风事件记录
- `GET /api/v1/wind-events`
- Query:
  - `segment` `string` 可选
  - `direction` `string` 可选
  - `controlLevel` `int` 可选
  - `format` `string` 可选：`csv`（传 `csv` 时返回导出内容）

---

## 持久化说明
系统启动会自动建表并持久化 4.1-4.5 相关数据:
- `wind_control_kv`
- `wind_control_plan`
- `wind_event_record`

