package com.wut.screencommonsx.Request.Wind;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateDispatchPlanReq {
    @Size(max = 64)
    private String contactStaff;

    @Size(max = 64)
    private String teamId;

    @Size(max = 128)
    private String warehouse;
}
