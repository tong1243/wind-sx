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
@TableName("wind_control_kv")
/**
 * KV 快照模型。
 * category + itemKey 唯一定位一条业务快照。
 */
public class WindControlKv {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("category")
    private String category;

    @TableField("item_key")
    private String itemKey;

    @TableField("content_json")
    private String contentJson;

    @TableField("updated_at")
    private Long updatedAt;
}
