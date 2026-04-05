# API接口文档（风区管控 4.1-4.5）

## 目录
- [通用说明](#通用说明)
- [统一响应格式](#统一响应格式)
- [错误处理说明](#错误处理说明)
- [接口列表](#接口列表)
  - [4.1 路段运行状态模块](#41-路段运行状态模块)
  - [4.2 大风时空影响模块](#42-大风时空影响模块)
  - [4.3 人员与设备信息库](#43-人员与设备信息库)
  - [4.4 管控预案库](#44-管控预案库)
  - [4.5 方案自动生成发布](#45-方案自动生成发布)
- [通用数据类型定义](#通用数据类型定义)
- [更新日志](#更新日志)

---

## 通用说明

### 1) 基础信息
- Base URL: `http://localhost:12700`
- Content-Type: `application/json`
- 时间戳参数统一使用毫秒（`long`）

### 2) 鉴权
- 文档中的接口默认需要携带 `Authorization: Bearer <JWT>`。
- 当前 4.1-4.5 模块未额外定义白名单接口，联调时建议统一携带 Token。

### 3) 常用枚举约定
- `direction`: `1`（下行）/ `2`（上行）
- `periodType`: `real` / `future4h` / `all`
- `period`: `real` / `history` / `forecast`
- `format`: `csv`（用于导出）
- `status`（方案状态）: `PUBLISHED` / `CLOSED`

### 4) 路段桩号格式
- 支持 `K3020`、`K3020+300` 两种写法。
- 区间查询时，系统按桩号数值做区间重叠匹配。

---

## 统一响应格式

### 1) 数据响应
```json
{
  "code": 200,
  "flag": true,
  "info": "业务说明",
  "data": {}
}
```

### 2) 消息响应
```json
{
  "code": 200,
  "flag": true,
  "info": "业务说明",
  "msg": "ok"
}
```

---

## 错误处理说明

### 1) 业务失败（返回包装体）
- 部分接口（如删除）会返回 `code=500, flag=false`，并通过 `msg` 告知失败原因（如 `not found`）。

### 2) 参数校验失败（Bean Validation）
- 触发 `@NotNull/@Min/@Max/@Size/@NotBlank` 时，Spring 可能返回 400（默认错误结构）。

### 3) 运行时业务异常
- 当前代码中存在 `IllegalArgumentException/IllegalStateException`（如方案不存在、等级非法等），默认会返回服务端异常响应。

---

## 接口列表

## 4.1 路段运行状态模块

### 4.1.1 获取路段运行总览

**接口地址**: `GET /api/v1/road-statuses`  
**鉴权**: 是

**Query参数**

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| timestamp | long | 是 | 毫秒时间戳 |

**成功响应 data 示例**
```json
{
  "timestamp": 1712140800000,
  "digitalTwinEnabled": true,
  "interchangeCount": 5,
  "serviceAreaCount": 2,
  "sections": [
    {
      "segmentId": "S1",
      "segmentName": "HS-G30 K3010-K3020",
      "direction": 1,
      "color": "green",
      "windLevel": 7
    }
  ]
}
```

---

### 4.1.2 获取断面参数检测

**接口地址**: `GET /api/v1/section-parameter-detections`  
**鉴权**: 是

**Query参数**

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| timestamp | long | 是 | 毫秒时间戳 |
| direction | int | 否 | `1`=下行，`2`=上行 |

**成功响应 data 示例**
```json
[
  {
    "timestamp": 1712140800000,
    "segmentId": "S1",
    "segment": "HS-G30 K3010-K3020",
    "direction": 1,
    "currentVehicleCount": 34,
    "avgSpeedKmPerHour": 60,
    "congestionStatus": "SLOW",
    "updateIntervalMin": 5
  }
]
```

---

### 4.1.3 获取事件检测信息

**接口地址**: `GET /api/v1/event-detections`  
**鉴权**: 是

**Query参数**

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| timestamp | long | 是 | 毫秒时间戳 |

**成功响应 data 示例**
```json
[
  {
    "eventId": "DET-40800",
    "eventType": "OVERSPEED",
    "segment": "HS-G30 K3020-K3030",
    "vehiclePlate": "新A8F21X",
    "thresholdSpeedKmPerHour": 120,
    "status": "UNPROCESSED",
    "timestamp": 1712140800000
  }
]
```

---

### 4.1.4 获取服务区车辆统计

**接口地址**: `GET /api/v1/service-areas`  
**鉴权**: 是

**Query参数**

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| timestamp | long | 是 | 毫秒时间戳 |

**成功响应 data 示例**
```json
[
  {
    "serviceArea": "Hongshan Service",
    "timestamp": 1712140800000,
    "inboundVehicle": 34,
    "outboundVehicle": 28,
    "insideVehicle": 96
  }
]
```

---

### 4.1.5 获取交通状态分析

**接口地址**: `GET /api/v1/traffic-states`  
**鉴权**: 是

**Query参数**

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| timestamp | long | 是 | 毫秒时间戳 |

**成功响应 data 示例**
```json
[
  {
    "segment": "HS-G30 K3010-K3020",
    "direction": 1,
    "vehPerHour": 1380,
    "updatedEveryMin": 5
  }
]
```

---

## 4.2 大风时空影响模块

### 4.2.1 获取全线风力可视化

**接口地址**: `GET /api/v1/wind-sections`  
**鉴权**: 是

**Query参数**

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| timestamp | long | 是 | 毫秒时间戳 |
| mode | string | 否 | `real` / `forecast` / `max4h`，兼容 `max72h` |

**成功响应 data 示例**
```json
{
  "timestamp": 1712140800000,
  "mode": "max4h",
  "sections": [
    {
      "segmentId": "S1",
      "segmentName": "HS-G30 K3010-K3020",
      "direction": 1,
      "color": "green",
      "windLevel": 9
    }
  ]
}
```

---

### 4.2.2 获取风力限速阈值

**接口地址**: `GET /api/v1/wind-speed-thresholds`  
**鉴权**: 是

**成功响应 data 示例**
```json
[
  {
    "windLevel": 7,
    "passengerSpeedLimit": 80,
    "freightSpeedLimit": 70,
    "dangerousGoodsSpeedLimit": 60
  }
]
```

---

### 4.2.3 更新风力限速阈值

**接口地址**: `PUT /api/v1/wind-speed-thresholds/{windLevel}`  
**鉴权**: 是

**Path参数**

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| windLevel | int | 是 | 风力等级，范围 `1~12` |

**Body参数**

| 参数名 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| passengerSpeedLimit | int | 是 | `0~150` |
| freightSpeedLimit | int | 是 | `0~150` |
| dangerousGoodsSpeedLimit | int | 是 | `0~150` |

**请求示例**
```json
{
  "passengerSpeedLimit": 55,
  "freightSpeedLimit": 45,
  "dangerousGoodsSpeedLimit": 35
}
```

**业务规则**
- `windLevel` 超出范围会抛出业务异常（`windLevel must be between 1 and 12`）。

---

### 4.2.4 获取风力时空影响判断

**接口地址**: `GET /api/v1/wind-impacts/spatiotemporal`  
**鉴权**: 是

**Query参数**

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| timestamp | long | 是 | 毫秒时间戳 |
| periodType | string | 否 | `real` / `future4h` / `all`（默认 `all`） |
| direction | int | 否 | `1`=下行，`2`=上行 |

**成功响应 data 示例**
```json
{
  "timestamp": 1712140800000,
  "periodType": "all",
  "records": [
    {
      "controlInterval": "主线起点至红山口服务区",
      "stakeRange": "K3010-K3030",
      "direction": 1,
      "baseTime": 1712140800000,
      "periodType": "real",
      "trafficVolumeVehPerHour": 1200,
      "maxWindLevel": 9,
      "recommendedControlLevel": 2,
      "currentControlLevel": 4,
      "needAdjust": true
    }
  ]
}
```

---

### 4.2.5 获取大风观测/预测数据

**接口地址**: `GET /api/v1/wind-observations`  
**鉴权**: 是

**Query参数**

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| timestamp | long | 是 | 毫秒时间戳 |
| period | string | 否 | `real` / `history` / `forecast` |
| direction | int | 否 | `1`=下行，`2`=上行 |

**成功响应 data 示例**
```json
{
  "timestamp": 1712140800000,
  "period": "forecast",
  "records": [
    {
      "time": "2026-04-05 12:00:00",
      "direction": 1,
      "windLevel": 8,
      "windDirection": "NW",
      "durationMin": 60
    }
  ]
}
```

---

### 4.2.6 获取阻断时长预测

**接口地址**: `GET /api/v1/block-duration-forecasts`  
**鉴权**: 是

**Query参数**

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| timestamp | long | 是 | 毫秒时间戳 |

**成功响应 data 示例**
```json
{
  "timestamp": 1712140800000,
  "severeSegmentCount": 2,
  "predictedBlockDurationMin": 50
}
```

---

## 4.3 人员与设备信息库

## 4.3.1 信息发布设施

### A) 查询
**接口地址**: `GET /api/v1/publish-facilities`  
**鉴权**: 是

### B) 新增/更新
**接口地址**: `PUT /api/v1/publish-facilities/{facilityId}`  
**鉴权**: 是

**Path参数**

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| facilityId | string | 是 | 设施ID（主键） |

**Body说明**
- 自由扩展字段，系统会按 `facilityId` 执行 upsert。

### C) 删除
**接口地址**: `DELETE /api/v1/publish-facilities/{facilityId}`  
**鉴权**: 是

**成功响应（消息）**
```json
{
  "code": 200,
  "flag": true,
  "info": "publish facility deleted",
  "msg": "ok"
}
```

---

## 4.3.2 封路设备

### A) 查询
**接口地址**: `GET /api/v1/closure-devices`

### B) 新增/更新
**接口地址**: `PUT /api/v1/closure-devices/{deviceId}`

### C) 删除
**接口地址**: `DELETE /api/v1/closure-devices/{deviceId}`

---

## 4.3.3 执勤人员

### A) 查询
**接口地址**: `GET /api/v1/staff`

### B) 新增/更新
**接口地址**: `PUT /api/v1/staff/{staffId}`

### C) 删除
**接口地址**: `DELETE /api/v1/staff/{staffId}`

---

## 4.3.4 执勤班组

### A) 查询班组
**接口地址**: `GET /api/v1/teams`

### B) 新增/更新班组
**接口地址**: `PUT /api/v1/teams/{teamId}`

**Body字段（常用）**
- `name` 班组名称
- `leaderId` 组长 staffId
- `node` 负责节点
- `dispatchState` 出警状态（`READY`/`DISPATCHED`/`ON_DUTY`）
- `memberIds` 成员 staffId 列表

**业务约束**
- 班组已出警（`DISPATCHED`/`ON_DUTY`）时，不允许编辑成员、组长和基础信息。
- `leaderId` 必须是 `memberIds` 中的成员。

### C) 更新班组成员
**接口地址**: `PUT /api/v1/teams/{teamId}/members`

**Body参数**

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| memberIds | string[] | 是 | 成员 staffId 列表，元素不可为空 |

**业务约束**
- 目标班组已出警时不可编辑成员。
- 已设置组长时，更新后成员列表必须仍包含该组长。
- 同一成员仅允许属于一个班组；调入新班组时会自动从其他未出警班组移除。

---

## 4.4 管控预案库

### 4.4.1 获取管控原则

**接口地址**: `GET /api/v1/control-principles`

**成功响应 data 示例**
```json
[
  "Risk segments use graded control levels.",
  "VMS publishes level-based speed limits by vehicle type."
]
```

---

### 4.4.2 获取管控方案预案列表

**接口地址**: `GET /api/v1/control-plans`

**成功响应 data 示例**
```json
[
  {
    "level": 1,
    "minWindLevel": 11,
    "maxWindLevel": 12,
    "passengerSpeedLimit": 40,
    "freightSpeedLimit": 30,
    "description": "..."
  }
]
```

---

### 4.4.3 更新管控方案预案

**接口地址**: `PUT /api/v1/control-plans/{level}`  
**注意**: 与 `POST /api/v1/control-plans`（4.5.2 方案生成）是同路径不同方法。

**Path参数**

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| level | int | 是 | 管控等级 |

**Body参数（均可选）**

| 参数名 | 类型 | 约束 |
| --- | --- | --- |
| minWindLevel | int | `0~12` |
| maxWindLevel | int | `0~12` |
| passengerSpeedLimit | int | `0~150` |
| freightSpeedLimit | int | `0~150` |
| description | string | 长度 `<=255` |

**业务约束**
- 只允许“更严格”，不允许放宽。
- 若高等级方案收紧，将级联同步到其下低等级方案。
- `level` 不存在会抛业务异常。

---

### 4.4.4 获取VMS发布内容

**接口地址**: `GET /api/v1/vms-contents`

**成功响应 data 示例**
```json
[
  {
    "level": 1,
    "content": "Strong wind warning..."
  }
]
```

---

### 4.4.5 更新VMS发布内容

**接口地址**: `PUT /api/v1/vms-contents/{level}`

**Body参数**

| 参数名 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| content | string | 是 | 非空，长度 `<=255` |

---

### 4.4.6 获取人员设备调用预案

**接口地址**: `GET /api/v1/dispatch-plans`

**成功响应 data 示例**
```json
[
  {
    "segment": "HS-G30 K3010-K3020",
    "contactStaff": "ST-01",
    "teamId": "T-01",
    "warehouse": "Hongshan Exit"
  }
]
```

---

### 4.4.7 更新人员设备调用预案

**接口地址**: `PUT /api/v1/dispatch-plans/{segment}`

**Path参数**

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| segment | string | 是 | 管控区段（不可新建） |

**Body参数（均可选）**

| 参数名 | 类型 | 约束 |
| --- | --- | --- |
| contactStaff | string | 长度 `<=64` |
| teamId | string | 长度 `<=64` |
| warehouse | string | 长度 `<=128` |

**业务约束**
- `segment` 必须是库内既有区段，不能新增。
- `contactStaff` 必须是人员设备库中的班组组长（支持组长ID或姓名）。
- `teamId` 必须来自班组库。
- `warehouse` 必须来自封路设备库。

---

## 4.5 方案自动生成发布

### 4.5.1 获取管控执行流程

**接口地址**: `GET /api/v1/control-flows`

---

### 4.5.2 生成管控方案

**接口地址**: `POST /api/v1/control-plans`  
**注意**: 与 `GET/PUT /api/v1/control-plans`（4.4）是同路径不同方法。

**Body参数**

| 参数名 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| timestamp | long | 是 | 毫秒时间戳 |
| segment | string | 是 | 长度 `1~128` |
| realtimeWindLevel | int | 是 | `0~12` |
| forecastMaxWindLevel | int | 是 | `0~12` |
| direction | int | 否 | `1~2` |
| durationHours | int | 否 | `1~24`，默认4 |

**请求示例**
```json
{
  "timestamp": 1712140800000,
  "segment": "HS-G30 K3020-K3030",
  "direction": 1,
  "durationHours": 4,
  "realtimeWindLevel": 9,
  "forecastMaxWindLevel": 10
}
```

**成功响应 data 关键字段**
- `planId`
- `publishTime` / `publishEndTime`
- `startStake` / `endStake`
- `managementPlan`
- `recommendedControlLevel`
- `dispatch`
- `status`（初始 `DRAFT`）

---

### 4.5.3 更新方案状态（发布/解除）

**接口地址**: `PATCH /api/v1/control-plans/{planId}`

**Path参数**

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| planId | string | 是 | 方案ID |

**Body参数**

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| status | string | 是 | 仅支持 `PUBLISHED` / `CLOSED` |

**请求示例（发布）**
```json
{
  "status": "PUBLISHED"
}
```

**请求示例（解除）**
```json
{
  "status": "CLOSED"
}
```

**业务规则**
- `PUBLISHED`: 方案状态改为已发布，写入运行中的风事件记录。
- `CLOSED`: 仅允许对已发布方案执行，解除后事件转 `FINISHED` 并归档。
- 非法状态值会抛业务异常（`status must be PUBLISHED or CLOSED`）。

---

### 4.5.4 获取自动调级推荐

**接口地址**: `GET /api/v1/control-plan-recommendations`

**Query参数**

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| timestamp | long | 是 | 毫秒时间戳 |

**成功响应 data 示例**
```json
{
  "timestamp": 1712140800000,
  "suggestions": [
    {
      "segment": "HS-G30 K3010-K3020",
      "currentLevel": 4,
      "recommendedLevel": 2,
      "eventType": "UPGRADE_CONTROL",
      "controlStartTime": "2026-04-05 12:00:00",
      "controlDurationMin": 15
    }
  ]
}
```

---

### 4.5.5 查询/导出大风事件记录

**接口地址**: `GET /api/v1/wind-events`

**Query参数**

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| segment | string | 否 | 区段名精确匹配 |
| startStake | string | 否 | 起始桩号 |
| endStake | string | 否 | 结束桩号 |
| direction | int | 否 | `1`=下行，`2`=上行 |
| controlPlan | string | 否 | 如 `LEVEL-1` |
| startTime | string | 否 | `yyyy-MM-dd HH:mm:ss` |
| endTime | string | 否 | `yyyy-MM-dd HH:mm:ss` |
| controlLevel | int | 否 | 管控等级 |
| limit | int | 否 | 返回条数，自动限制在 `10~20`，默认 `20` |
| format | string | 否 | 传 `csv` 时返回CSV字符串 |

**成功响应 data 示例（JSON）**
```json
[
  {
    "eventId": "EVT-98a1b2",
    "startTime": "2026-04-05 12:00:00",
    "endTime": "2026-04-05 16:00:00",
    "segment": "HS-G30 K3010-K3020",
    "startStake": "K3010",
    "endStake": "K3020",
    "direction": 1,
    "controlPlan": "LEVEL-2",
    "maxWindLevel": 10,
    "controlLevel": 2,
    "durationMin": 240,
    "status": "FINISHED"
  }
]
```

**CSV导出字段顺序**
- `eventId,startStake,endStake,startTime,endTime,segment,direction,maxWindLevel,controlLevel,controlPlan,durationMin,status`

---

## 通用数据类型定义

```typescript
interface DefaultDataResp<T = any> {
  code: number      // 200 成功，500 失败
  flag: boolean     // true/false
  info: string      // 业务说明
  data: T
}

interface DefaultMsgResp {
  code: number
  flag: boolean
  info: string
  msg: string
}
```

---

## 更新日志

- 2026-04-05：按模板重写文档结构，补充目录、错误处理、数据类型定义。
- 2026-04-05：补全 4.1-4.5 全部接口的参数约束、示例和业务规则。
- 2026-04-05：同步最新实现规则（班组出警限制、组长约束、方案关闭、事件 limit 10~20）。


