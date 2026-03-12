package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
// 事件预警信息
// 对应表名:carevent_{time}
@TableName("carevent")
public class CarEvent {
    @TableId
    private Long uuid;
    @TableField("startTimeStamp")
    private Long startTimestamp;
    @TableField("endTimeStamp")
    private Long endTimestamp;
    private String id;
    @TableField("startMileage")
    private String startMileage;
    @TableField("endMileage")
    private String endMileage;
    private Integer lane;
    @TableField("eventType")
    private Integer eventType;
    @TableField("trajId")
    private Long trajId;
    private Integer status;
    private Integer process;
    @TableField("queueLength")
    private Double queueLength;
}
