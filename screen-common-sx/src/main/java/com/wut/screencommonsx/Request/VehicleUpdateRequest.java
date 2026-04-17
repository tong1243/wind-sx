package com.wut.screencommonsx.Request;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VehicleUpdateRequest {
    @Pattern(regexp = "^[^\\s]{1,6}$", message = "vehicleName must be 1-6 chars without whitespace")
    private String vehicleName;

    private String owner;
    private String vehicleType;
    private String usageNature;
    private String brandModel;

    @Pattern(regexp = "^[A-Z0-9]{17}$", message = "vin must be 17 uppercase alphanumeric chars")
    private String vin;

    private String engineNumber;

    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "registrationDate must match yyyy-MM-dd")
    private String registrationDate;

    private String licensePhoto;
}
