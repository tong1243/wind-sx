package com.wut.screencommonsx.Request;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VehicleUpdateRequest {
    @Pattern(regexp = "^[\\p{IsHan}A-Za-z0-9]{1,6}$", message = "车辆名称必须为1-6位中英文或数字")
    private String vehicleName;

    private String owner;

    @Pattern(regexp = "^(1|2)$", message = "车辆类型必须为1或2")
    private String vehicleType;
    private String usageNature;
    private String brandModel;

    @Pattern(regexp = "^[A-Z0-9]{17}$", message = "车架号必须为17位大写字母或数字")
    private String vin;

    private String engineNumber;

    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "注册日期格式必须为yyyy-MM-dd")
    private String registrationDate;

    private String licensePhoto;
}
