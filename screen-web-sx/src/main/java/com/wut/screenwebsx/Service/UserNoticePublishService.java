package com.wut.screenwebsx.Service;

import com.wut.screencommonsx.Model.CarInfo;

public interface UserNoticePublishService {
    void publishVehicleRegisterSubmitted(String phone, CarInfo carInfo);

    void publishVehicleAuditPassed(CarInfo carInfo);

    void publishVehicleAuditRejected(CarInfo carInfo, String rejectReason);

    void publishReservationSubmitSuccess(String phone, String licensePlate, String travelTimeSlot, String startPoint, String endPoint);

    void publishReservationSubmitFailed(String phone, String licensePlate, String travelTimeSlot, String startPoint, String endPoint, String failReason);

    void publishReservationAuditPassed(String phone, String licensePlate, String travelTimeSlot, String startPoint, String endPoint);

    void publishReservationAuditRejected(String phone, String licensePlate, String travelTimeSlot, String startPoint, String endPoint, String rejectReason);
}
