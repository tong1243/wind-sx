package com.wut.screenwebsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wut.screencommonsx.Model.CarInfo;
import com.wut.screencommonsx.Model.UserNotice;
import com.wut.screenwebsx.Mapper.CarInfoMapper;
import com.wut.screenwebsx.Mapper.UserAccountMapper;
import com.wut.screenwebsx.Mapper.UserNoticeMapper;
import com.wut.screenwebsx.Service.UserNoticePublishService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserNoticePublishServiceImpl implements UserNoticePublishService {
    private static final String NOTICE_TYPE_VEHICLE_REGISTER_PENDING = "vehicle_register_pending";
    private static final String NOTICE_TYPE_VEHICLE_AUDIT_PASSED = "vehicle_audit_passed";
    private static final String NOTICE_TYPE_VEHICLE_AUDIT_REJECTED = "vehicle_audit_rejected";
    private static final String NOTICE_TYPE_RESERVATION_SUBMIT_SUCCESS = "reservation_submit_success";
    private static final String NOTICE_TYPE_RESERVATION_SUBMIT_FAILED = "reservation_submit_failed";
    private static final String NOTICE_TYPE_RESERVATION_PASSED = "reservation_audit_passed";
    private static final String NOTICE_TYPE_RESERVATION_REJECTED = "reservation_audit_rejected";

    private static final Map<String, NoticeTemplate> NOTICE_TEMPLATE_MAP = buildNoticeTemplateMap();

    private final UserNoticeMapper userNoticeMapper;
    private final UserAccountMapper userAccountMapper;
    private final CarInfoMapper carInfoMapper;

    @Override
    public void publishVehicleRegisterSubmitted(String phone, CarInfo carInfo) {
        if (!hasText(phone) || carInfo == null) {
            return;
        }
        Map<String, String> params = new HashMap<>();
        params.put("licensePlate", valueOrDash(carInfo.getLicensePlate()));
        params.put("vehicleName", valueOrDash(carInfo.getVehicleName()));
        saveNoticeByTemplate(phone, NOTICE_TYPE_VEHICLE_REGISTER_PENDING, params, null);
    }

    @Override
    public void publishVehicleAuditPassed(CarInfo carInfo) {
        if (carInfo == null) {
            return;
        }
        String phone = resolvePhoneByLicensePlate(carInfo.getLicensePlate());
        if (!hasText(phone)) {
            return;
        }
        Map<String, String> params = new HashMap<>();
        params.put("licensePlate", valueOrDash(carInfo.getLicensePlate()));
        params.put("vehicleName", valueOrDash(carInfo.getVehicleName()));
        params.put("currentPoints", String.valueOf(carInfo.getCurrentPoints() == null ? 12 : carInfo.getCurrentPoints()));
        saveNoticeByTemplate(phone, NOTICE_TYPE_VEHICLE_AUDIT_PASSED, params, null);
    }

    @Override
    public void publishVehicleAuditRejected(CarInfo carInfo, String rejectReason) {
        if (carInfo == null) {
            return;
        }
        String phone = resolvePhoneByLicensePlate(carInfo.getLicensePlate());
        if (!hasText(phone)) {
            return;
        }
        Map<String, String> params = new HashMap<>();
        params.put("licensePlate", valueOrDash(carInfo.getLicensePlate()));
        params.put("vehicleName", valueOrDash(carInfo.getVehicleName()));
        params.put("rejectReason", valueOrDash(rejectReason));
        saveNoticeByTemplate(phone, NOTICE_TYPE_VEHICLE_AUDIT_REJECTED, params, null);
    }

    @Override
    public void publishReservationSubmitSuccess(String phone, String licensePlate, String travelTimeSlot, String startPoint, String endPoint) {
        if (!hasText(phone)) {
            return;
        }
        Map<String, String> params = new HashMap<>();
        params.put("licensePlate", valueOrDash(licensePlate));
        params.put("travelTimeSlot", valueOrDash(travelTimeSlot));
        params.put("startPoint", valueOrDash(startPoint));
        params.put("endPoint", valueOrDash(endPoint));
        saveNoticeByTemplate(phone, NOTICE_TYPE_RESERVATION_SUBMIT_SUCCESS, params, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishReservationSubmitFailed(String phone, String licensePlate, String travelTimeSlot, String startPoint, String endPoint, String failReason) {
        if (!hasText(phone)) {
            return;
        }
        Map<String, String> params = new HashMap<>();
        params.put("licensePlate", valueOrDash(licensePlate));
        params.put("travelTimeSlot", valueOrDash(travelTimeSlot));
        params.put("startPoint", valueOrDash(startPoint));
        params.put("endPoint", valueOrDash(endPoint));
        params.put("failReason", valueOrDash(failReason));
        saveNoticeByTemplate(phone, NOTICE_TYPE_RESERVATION_SUBMIT_FAILED, params, null);
    }

    @Override
    public void publishReservationAuditPassed(String phone, String licensePlate, String travelTimeSlot, String startPoint, String endPoint) {
        if (!hasText(phone)) {
            return;
        }
        Map<String, String> params = new HashMap<>();
        params.put("licensePlate", valueOrDash(licensePlate));
        params.put("travelTimeSlot", valueOrDash(travelTimeSlot));
        params.put("startPoint", valueOrDash(startPoint));
        params.put("endPoint", valueOrDash(endPoint));
        saveNoticeByTemplate(phone, NOTICE_TYPE_RESERVATION_PASSED, params, null);
    }

    @Override
    public void publishReservationAuditRejected(String phone, String licensePlate, String travelTimeSlot, String startPoint, String endPoint, String rejectReason) {
        if (!hasText(phone)) {
            return;
        }
        Map<String, String> params = new HashMap<>();
        params.put("licensePlate", valueOrDash(licensePlate));
        params.put("travelTimeSlot", valueOrDash(travelTimeSlot));
        params.put("startPoint", valueOrDash(startPoint));
        params.put("endPoint", valueOrDash(endPoint));
        params.put("rejectReason", valueOrDash(rejectReason));
        saveNoticeByTemplate(phone, NOTICE_TYPE_RESERVATION_REJECTED, params, null);
    }

    private void saveNotice(String phone, String noticeType, String title, String content, Long relatedId) {
        UserNotice notice = new UserNotice();
        notice.setUserPhone(phone);
        notice.setNoticeType(limitLength(noticeType, 64));
        notice.setTitle(limitLength(title, 128));
        notice.setContent(content);
        notice.setIsRead(0);
        // 兼容历史库结构差异（related_id 可能为极小整数类型），通知先不强依赖 related_id。
        notice.setRelatedId(null);
        notice.setCreateTime(LocalDateTime.now());
        notice.setReadTime(null);
        userNoticeMapper.insert(notice);
    }

    private void saveNoticeByTemplate(String phone, String noticeType, Map<String, String> params, Long relatedId) {
        NoticeTemplate template = NOTICE_TEMPLATE_MAP.get(noticeType);
        if (template == null) {
            return;
        }
        String title = renderTemplate(template.title(), params);
        String content = renderTemplate(template.content(), params);
        try {
            saveNotice(phone, noticeType, title, content, relatedId);
        } catch (Exception ex) {
            // 通知是辅助能力，失败时不应中断主业务流程（车辆登记、预约提交、审核等）。
            log.error("消息通知写入失败：phone={}, noticeType={}, title={}, relatedId={}",
                    phone, noticeType, title, relatedId, ex);
        }
    }

    private String resolvePhoneByLicensePlate(String licensePlate) {
        if (!hasText(licensePlate)) {
            return null;
        }
        String normalized = licensePlate.trim();
        var user = userAccountMapper.selectByAnyLicense(normalized);
        if (user != null && hasText(user.getPhone())) {
            return user.getPhone();
        }
        CarInfo carInfo = carInfoMapper.selectOne(new LambdaQueryWrapper<CarInfo>()
                .eq(CarInfo::getLicensePlate, normalized)
                .last("LIMIT 1"));
        return carInfo == null ? null : carInfo.getSubmitterPhone();
    }

    private String valueOrDash(String value) {
        return hasText(value) ? value.trim() : "-";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String limitLength(String value, int maxLength) {
        if (!hasText(value)) {
            return value;
        }
        String text = value.trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private String renderTemplate(String template, Map<String, String> params) {
        String result = template;
        if (result == null || params == null || params.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();
            result = result.replace("{" + key + "}", valueOrDash(val));
        }
        return result;
    }

    private static Map<String, NoticeTemplate> buildNoticeTemplateMap() {
        Map<String, NoticeTemplate> map = new HashMap<>();
        map.put(NOTICE_TYPE_VEHICLE_REGISTER_PENDING, new NoticeTemplate(
                "车辆登记提交成功",
                "您的车辆登记信息已提交审核。\n车牌号：{licensePlate}\n车辆名称：{vehicleName}\n审核状态：待审核"
        ));
        map.put(NOTICE_TYPE_VEHICLE_AUDIT_PASSED, new NoticeTemplate(
                "车辆审核通过通知",
                "您的车辆审核已通过。\n车牌号：{licensePlate}\n车辆名称：{vehicleName}\n当前积分：{currentPoints}\n您可以在车辆管理页面查看该车辆。"
        ));
        map.put(NOTICE_TYPE_VEHICLE_AUDIT_REJECTED, new NoticeTemplate(
                "车辆审核驳回通知",
                "您的车辆审核未通过。\n车牌号：{licensePlate}\n车辆名称：{vehicleName}\n驳回原因：{rejectReason}\n请修改后重新提交。"
        ));
        map.put(NOTICE_TYPE_RESERVATION_SUBMIT_SUCCESS, new NoticeTemplate(
                "出行预约成功通知",
                "您的出行预约已成功提交。\n出行时间：{travelTimeSlot}\n起点：{startPoint}\n终点：{endPoint}\n车牌号：{licensePlate}"
        ));
        map.put(NOTICE_TYPE_RESERVATION_SUBMIT_FAILED, new NoticeTemplate(
                "出行预约失败通知",
                "您的出行预约提交失败。\n出行时间：{travelTimeSlot}\n起点：{startPoint}\n终点：{endPoint}\n车牌号：{licensePlate}\n失败原因：{failReason}"
        ));
        map.put(NOTICE_TYPE_RESERVATION_PASSED, new NoticeTemplate(
                "出行预约审核通过通知",
                "您的出行预约审核已通过！\n出行时间：{travelTimeSlot}\n起点：{startPoint}\n终点：{endPoint}\n车牌号：{licensePlate}\n出行时请打开驾驶引导，谨慎驾驶！"
        ));
        map.put(NOTICE_TYPE_RESERVATION_REJECTED, new NoticeTemplate(
                "出行预约审核驳回通知",
                "您的出行预约未通过审核。\n出行时间：{travelTimeSlot}\n起点：{startPoint}\n终点：{endPoint}\n车牌号：{licensePlate}\n驳回原因：{rejectReason}"
        ));
        return map;
    }

    private record NoticeTemplate(String title, String content) {}
}
