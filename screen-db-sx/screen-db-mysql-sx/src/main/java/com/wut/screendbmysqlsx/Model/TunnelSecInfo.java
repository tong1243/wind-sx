package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("tunnelsecinfo")
// 断面选择支
public class TunnelSecInfo {
    @TableId(type = IdType.INPUT)
    private Integer sid;
    @TableField("start")
    private int start;
    @TableField("end")
    private int end;
    @TableField("road")
    private String road;
}
