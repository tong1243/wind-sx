package com.wut.screencommonsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_notice")
public class UserNotice {
    @TableId(type = IdType.AUTO)
    private Long id;             // 通知ID
    private String userPhone;    // 接收用户手机号
    private String noticeType;   // 通知类型：reservation_pass/reservation_reject等
    private String title;        // 通知标题
    private String content;      // 通知内容
    private Integer isRead;      // 是否已读：1-是 0-否
    private Long relatedId;      // 关联业务ID
    private LocalDateTime createTime; // 发送时间
    private LocalDateTime readTime;   // 已读时间
}