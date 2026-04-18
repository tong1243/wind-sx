package com.wut.screencommonsx.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReservationAuditRejectRequest {
    @NotBlank(message = "rejectReason cannot be blank")
    private String rejectReason;
}
