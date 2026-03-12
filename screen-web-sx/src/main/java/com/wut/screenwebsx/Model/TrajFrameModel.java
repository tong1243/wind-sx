package com.wut.screenwebsx.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wut.screendbmysqlsx.Model.Traj;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrajFrameModel {
    private long timestamp;
    @JsonProperty("carToWH")
    private int carToWH;
    @JsonProperty("carToEZ")
    private int carToEZ;
    @JsonProperty("trajList")
    private List<Traj> trajList;
}
