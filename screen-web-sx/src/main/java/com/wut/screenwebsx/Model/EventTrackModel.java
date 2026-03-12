package com.wut.screenwebsx.Model;

import com.wut.screendbmysqlsx.Model.CarEvent;
import com.wut.screendbmysqlsx.Model.Traj;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventTrackModel {
    private CarEvent event;         // 目标事件信息
    private List<Traj> trajList;    // 事件相关的轨迹信息
}
