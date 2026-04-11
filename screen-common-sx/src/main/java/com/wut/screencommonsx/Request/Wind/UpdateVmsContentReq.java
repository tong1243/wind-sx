package com.wut.screencommonsx.Request.Wind;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateVmsContentReq {
    @NotBlank
    @Size(max = 255)
    private String content;
}
