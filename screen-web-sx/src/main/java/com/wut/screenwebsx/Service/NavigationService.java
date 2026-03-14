package com.wut.screenwebsx.Service;


import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screenwebsx.Controller.NavigationController;

import java.util.List;

/**
 * 3. 实时导航服务接口
 */
public interface NavigationService {
    // 3.1 获取车辆实时信息
    ApiResponse<?> getCarRealInfo(String phone);

    // 3.2 获取风区信息集合
    ApiResponse<List<NavigationController.WindZoneInfo>> getWindZoneInfo();
}