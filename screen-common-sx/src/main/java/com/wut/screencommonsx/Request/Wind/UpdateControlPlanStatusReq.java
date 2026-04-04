package com.wut.screencommonsx.Request.Wind;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateControlPlanStatusReq {
    @NotBlank
    private String status;
}
