package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Laneline {
    @TableField("frenetx")
    @JsonProperty("frenetX")
    private Double frenetX;
    private Double longitude;
    private Double latitude;
    private Integer lane;
    @JsonProperty("height")
    private Double height;
}
