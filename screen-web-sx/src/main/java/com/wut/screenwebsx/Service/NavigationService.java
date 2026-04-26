package com.wut.screenwebsx.Service;

import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screenwebsx.Controller.NavigationController;

import java.util.List;

/**
 * Realtime navigation service API.
 */
public interface NavigationService {
    ApiResponse<?> resetRealTimeNavigationData();

    ApiResponse<?> getCarRealInfo(String phone);

    ApiResponse<List<NavigationController.WindZoneInfo>> getWindZoneInfo();
}
