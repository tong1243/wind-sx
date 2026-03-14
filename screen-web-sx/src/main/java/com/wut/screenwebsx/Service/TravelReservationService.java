package com.wut.screenwebsx.Service;


import com.wut.screencommonsx.Request.GreenCodeRequest;
import com.wut.screencommonsx.Response.ApiResponse;

/**
 * 4. 出行预约服务接口
 */
public interface TravelReservationService {
    // 4.1 生成绿码
    ApiResponse<?> generateGreenCode(GreenCodeRequest request, String phone);

    // 4.2 创建通行凭证
    ApiResponse<?> createCertificate(GreenCodeRequest request, String phone);

    // 4.3 获取通行凭证
    ApiResponse<?> getCertificate(String phone);
}