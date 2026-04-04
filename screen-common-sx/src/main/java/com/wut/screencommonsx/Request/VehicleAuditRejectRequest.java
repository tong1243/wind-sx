package com.wut.screencommonsx.Request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 车辆审核拒绝请求参数。
 */
@Data
public class VehicleAuditRejectRequest {
    /** 拒绝项编码列表。 */
    @NotEmpty(message = "rejectItems cannot be empty")
    private List<String> rejectItems;

    /** 补充备注。 */
    private String rejectRemark;
}
