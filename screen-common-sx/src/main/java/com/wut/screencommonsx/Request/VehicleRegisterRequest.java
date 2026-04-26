package com.wut.screencommonsx.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VehicleRegisterRequest {
    @NotBlank(message = "车辆名称不能为空")
    @Pattern(regexp = "^[\\p{IsHan}A-Za-z0-9]{1,6}$", message = "车辆名称必须为1-6位中英文或数字")
    private String vehicleName;

    @NotBlank(message = "车牌号不能为空")
    private String licensePlate;

    @NotBlank(message = "所有人不能为空")
    private String owner;

    @NotBlank(message = "车辆类型不能为空")
    @Pattern(regexp = "^(1|2)$", message = "车辆类型必须为1或2")
    private String vehicleType;

    @NotBlank(message = "使用性质不能为空")
    private String usageNature;

    @NotBlank(message = "品牌型号不能为空")
    private String brandModel;

    @NotBlank(message = "车架号不能为空")
    @Pattern(regexp = "^[A-Z0-9]{17}$", message = "车架号必须为17位大写字母或数字")
    private String vin;

    @NotBlank(message = "发动机号不能为空")
    private String engineNumber;

    @NotBlank(message = "注册日期不能为空")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "注册日期格式必须为yyyy-MM-dd")
    private String registrationDate;

    @NotBlank(message = "行驶证照片不能为空")
    private String licensePhoto;
}
