package com.wut.screenwebsx.Service;

import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screencommonsx.Request.NavigationSettlementRequest;
import com.wut.screenwebsx.Controller.NavigationController;

import java.util.List;

/**
 * Realtime navigation service API.
 */
public interface NavigationService {
    ApiResponse<?> resetRealTimeNavigationData();

    ApiResponse<?> getCarRealInfo(String phone);

    ApiResponse<List<NavigationController.WindZoneInfo>> getWindZoneInfo();

    ApiResponse<NavigationController.OverviewInfo> getOverview();

    ApiResponse<?> settleNavigation(NavigationSettlementRequest request, String phone);
}
