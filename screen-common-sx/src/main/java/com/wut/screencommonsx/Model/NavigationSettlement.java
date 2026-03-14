package com.wut.screencommonsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("navigation_settlement")
public class NavigationSettlement {
    @TableId(type = IdType.AUTO)
    private Long id;             // 结算ID
    private String userPhone;    // 用户手机号
    private String carLicense;   // 车辆号牌
    private String startPile;    // 起点桩号
    private String endPile;      // 终点桩号
    private String eventInfo;    // 事件记录（JSON）
    private Integer overspeedCount; // 超速次数
    private Integer speedRecordCount; // 速度记录次数
    private Integer parkCount;   // 停车次数
    private Integer deductPoints; // 扣分（负为扣，0为无）
    private LocalDateTime navigationStartTime; // 导航开始时间
    private LocalDateTime navigationEndTime;   // 导航结束时间
    private LocalDateTime createTime; // 结算上传时间
}