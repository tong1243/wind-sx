package com.wut.screenwebsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wut.screencommonsx.Exception.BusinessException;
import com.wut.screencommonsx.Model.CarInfo;
import com.wut.screencommonsx.Model.TravelReservation;
import com.wut.screencommonsx.Model.UcCarRealTime;
import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screencommonsx.Response.Operation.OperationMaintenanceResp;
import com.wut.screenwebsx.Mapper.CarInfoMapper;
import com.wut.screenwebsx.Mapper.TravelReservationMapper;
import com.wut.screenwebsx.Mapper.UcCarRealTimeMapper;
import com.wut.screenwebsx.Service.OperationMaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 杩愮淮鍚庡彴涓氬姟瀹炵幇銆? * 瑕嗙洊鍦ㄩ€旇溅杈嗐€侀绾﹁褰曘€佽溅杈嗗鏍稿強瀵煎嚭鑳藉姏銆? */
@Service
@RequiredArgsConstructor
public class OperationMaintenanceServiceImpl implements OperationMaintenanceService {
    /** 鍦ㄩ€旇溅杈?Mapper銆?*/
    private final UcCarRealTimeMapper ucCarRealTimeMapper;
    /** 棰勭害璁板綍 Mapper銆?*/
    private final TravelReservationMapper travelReservationMapper;
    /** 杞﹁締妗ｆ Mapper銆?*/
    private final CarInfoMapper carInfoMapper;

    /** 瀹℃牳鎷掔粷椤规槧灏勩€?*/
    private static final Map<String, String> REJECT_ITEM_OPTIONS = buildRejectItemOptions();

    /**
     * 鏌ヨ鍦ㄩ€旇溅杈嗗垎椤佃〃鏍笺€?     */
    @Override
    public ApiResponse<?> getRealtimeVehicleTable(long pageNo, long pageSize, String vehicleId, String licensePlate, String direction) {
        LambdaQueryWrapper<UcCarRealTime> wrapper = new LambdaQueryWrapper<UcCarRealTime>()
                .orderByDesc(UcCarRealTime::getReportTime)
                .eq(hasText(vehicleId), UcCarRealTime::getUserPhone, vehicleId)
                .eq(hasText(licensePlate), UcCarRealTime::getCarLicense, licensePlate);
        Integer directionCode = toDirectionCode(direction);
        if (hasText(direction)) {
            if (directionCode != null) {
                wrapper.eq(UcCarRealTime::getDirection, directionCode);
            } else {
                wrapper.eq(UcCarRealTime::getDrivingDirection, direction);
            }
        }

        Page<UcCarRealTime> page = ucCarRealTimeMapper.selectPage(new Page<>(safePageNo(pageNo), safePageSize(pageSize)), wrapper);

        List<String> licensePlates = page.getRecords().stream()
                .map(UcCarRealTime::getCarLicense)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<String, String> vehicleTypeMap = loadVehicleTypeMap(licensePlates);
        List<OperationMaintenanceResp.RealtimeVehicleRow> rows = page.getRecords().stream().map(item -> {
            OperationMaintenanceResp.RealtimeVehicleRow row = new OperationMaintenanceResp.RealtimeVehicleRow();
            row.setId(item.getUserPhone());
            row.setCurrentLocation(item.getCurrentPile());
            row.setLicensePlate(item.getCarLicense());
            row.setVehicleType(vehicleTypeMap.getOrDefault(item.getCarLicense(), "unknown"));
            row.setSpeedKmh(item.getRealSpeed());
            row.setDirection(resolveDirectionCode(item));
            row.setReportTime(item.getReportTime());
            return row;
        }).toList();

        return ApiResponse.success("realtime vehicle table loaded", buildPage(page, rows));
    }

    /**
     * 鏌ヨ棰勭害鍒嗛〉琛ㄦ牸銆?     */
    @Override
    public ApiResponse<?> getReservationTable(long pageNo, long pageSize, String licensePlate, String startTime, String endTime, Boolean deductedOnly) {
        LambdaQueryWrapper<TravelReservation> wrapper = new LambdaQueryWrapper<TravelReservation>()
                .orderByDesc(TravelReservation::getCreateTime)
                .eq(hasText(licensePlate), TravelReservation::getCarLicense, licensePlate);

        LocalDateTime start = parseDateTime(startTime, "startTime");
        LocalDateTime end = parseDateTime(endTime, "endTime");
        if (start != null) {
            wrapper.ge(TravelReservation::getCreateTime, start);
        }
        if (end != null) {
            wrapper.le(TravelReservation::getCreateTime, end);
        }

        if (Boolean.TRUE.equals(deductedOnly)) {
            List<String> deductedLicenses = carInfoMapper.selectList(new LambdaQueryWrapper<CarInfo>()
                            .lt(CarInfo::getCurrentPoints, 12)
                            .isNotNull(CarInfo::getCurrentPoints))
                    .stream()
                    .map(CarInfo::getLicensePlate)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (deductedLicenses.isEmpty()) {
                return ApiResponse.success("reservation table loaded", emptyPage(pageNo, pageSize));
            }
            wrapper.in(TravelReservation::getCarLicense, deductedLicenses);
        }

        Page<TravelReservation> page = travelReservationMapper.selectPage(new Page<>(safePageNo(pageNo), safePageSize(pageSize)), wrapper);

        List<String> licensePlates = page.getRecords().stream()
                .map(TravelReservation::getCarLicense)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<String, CarInfo> carInfoMap = loadCarInfoMap(licensePlates);
        List<OperationMaintenanceResp.ReservationRow> rows = page.getRecords().stream().map(item -> {
            OperationMaintenanceResp.ReservationRow row = new OperationMaintenanceResp.ReservationRow();
            CarInfo car = carInfoMap.get(item.getCarLicense());
            row.setId(item.getId());
            row.setLicensePlate(item.getCarLicense());
            row.setTravelTimeSlot(item.getTravelTimeSlot());
            row.setVehicleType(hasText(item.getCarType()) ? item.getCarType() : (car == null ? "unknown" : car.getVehicleType()));
            row.setDeductionRecord(buildDeductionRecord(car));
            row.setCreateTime(item.getCreateTime());
            return row;
        }).toList();

        return ApiResponse.success("reservation table loaded", buildPage(page, rows));
    }

    /**
     * 鏌ヨ杞﹁締瀹℃牳鍒嗛〉琛ㄦ牸銆?     */
    @Override
    public ApiResponse<?> getVehicleAuditTable(long pageNo, long pageSize, String keyword, String auditStatus) {
        LambdaQueryWrapper<CarInfo> wrapper = new LambdaQueryWrapper<CarInfo>()
                .orderByDesc(CarInfo::getCreateTime)
                .eq(hasText(auditStatus), CarInfo::getAuditStatus, auditStatus)
                .and(hasText(keyword), w -> w.like(CarInfo::getVehicleName, keyword).or().like(CarInfo::getLicensePlate, keyword));

        Page<CarInfo> page = carInfoMapper.selectPage(new Page<>(safePageNo(pageNo), safePageSize(pageSize)), wrapper);
        List<OperationMaintenanceResp.VehicleAuditRow> rows = page.getRecords().stream().map(this::toAuditRow).toList();

        OperationMaintenanceResp.VehicleAuditTableData data = new OperationMaintenanceResp.VehicleAuditTableData();
        data.setPendingCount(carInfoMapper.selectCount(new LambdaQueryWrapper<CarInfo>()
                .eq(CarInfo::getAuditStatus, "unaudited")));
        data.setPage(buildPage(page, rows));
        return ApiResponse.success("vehicle audit table loaded", data);
    }

    /**
     * 鏌ヨ杞﹁締瀹℃牳鏄庣粏銆?     */
    @Override
    public ApiResponse<?> getVehicleAuditDetail(String licensePlate) {
        CarInfo carInfo = getVehicleByLicenseOrThrow(licensePlate);

        OperationMaintenanceResp.VehicleAuditDetail detail = new OperationMaintenanceResp.VehicleAuditDetail();
        detail.setId(carInfo.getId());
        detail.setVehicleName(carInfo.getVehicleName());
        detail.setLicensePlate(carInfo.getLicensePlate());
        detail.setAuditStatus(carInfo.getAuditStatus());
        detail.setAuditStatusText(toAuditStatusText(carInfo.getAuditStatus()));
        detail.setVehicleType(carInfo.getVehicleType());
        detail.setOwner(carInfo.getOwner());
        detail.setUsageNature(carInfo.getUsageNature());
        detail.setBrandModel(carInfo.getBrandModel());
        detail.setVin(carInfo.getVin());
        detail.setEngineNumber(carInfo.getEngineNumber());
        detail.setRegisterDate(carInfo.getRegisterDate());
        detail.setLicensePhoto(carInfo.getLicensePhoto());
        detail.setCurrentPoints(carInfo.getCurrentPoints());
        detail.setRejectReason(carInfo.getRejectReason());
        detail.setCheckItems(buildCheckItems(carInfo.getRejectReason()));
        detail.setReservationStats(buildReservationStats(licensePlate));
        detail.setLatestReservation(buildLatestReservation(licensePlate));

        return ApiResponse.success("vehicle audit detail loaded", detail);
    }

    /**
     * 鎻愪氦杞﹁締瀹℃牳閫氳繃銆?     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<?> approveVehicleAudit(String licensePlate) {
        CarInfo carInfo = getVehicleByLicenseOrThrow(licensePlate);
        carInfo.setAuditStatus("passed");
        carInfo.setRejectReason(null);
        carInfo.setUpdateTime(LocalDateTime.now());
        carInfoMapper.updateById(carInfo);
        return ApiResponse.success("vehicle audit approved", null);
    }

    /**
     * 鎻愪氦杞﹁締瀹℃牳鎷掔粷銆?     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<?> rejectVehicleAudit(String licensePlate, List<String> rejectItems, String rejectRemark) {
        CarInfo carInfo = getVehicleByLicenseOrThrow(licensePlate);

        List<String> normalizedRejectItems = normalizeRejectItems(rejectItems);
        if (normalizedRejectItems.isEmpty()) {
            throw BusinessException.badRequest("rejectItems contains no valid value");
        }

        String rejectReason = String.join(";", normalizedRejectItems);
        if (hasText(rejectRemark)) {
            rejectReason = rejectReason + ";remark:" + rejectRemark.trim();
        }

        carInfo.setAuditStatus("rejected");
        carInfo.setRejectReason(rejectReason);
        carInfo.setUpdateTime(LocalDateTime.now());
        carInfoMapper.updateById(carInfo);
        return ApiResponse.success("vehicle audit rejected", null);
    }

    /**
     * 瀵煎嚭杞﹁締瀹℃牳鍒楄〃涓?CSV銆?     */
    @Override
    public byte[] exportVehicleAudit(String keyword, String auditStatus) {
        LambdaQueryWrapper<CarInfo> wrapper = new LambdaQueryWrapper<CarInfo>()
                .orderByDesc(CarInfo::getCreateTime)
                .eq(hasText(auditStatus), CarInfo::getAuditStatus, auditStatus)
                .and(hasText(keyword), w -> w.like(CarInfo::getVehicleName, keyword).or().like(CarInfo::getLicensePlate, keyword));

        List<CarInfo> records = carInfoMapper.selectList(wrapper);

        StringBuilder csv = new StringBuilder();
        csv.append("vehicleName,licensePlate,auditStatus,auditStatusText,currentPoints,createTime,rejectReason\n");
        for (CarInfo item : records) {
            csv.append(csvCell(item.getVehicleName())).append(',')
                    .append(csvCell(item.getLicensePlate())).append(',')
                    .append(csvCell(item.getAuditStatus())).append(',')
                    .append(csvCell(toAuditStatusText(item.getAuditStatus()))).append(',')
                    .append(item.getCurrentPoints() == null ? "" : item.getCurrentPoints()).append(',')
                    .append(csvCell(item.getCreateTime() == null ? "" : item.getCreateTime().toString())).append(',')
                    .append(csvCell(item.getRejectReason()))
                    .append('\n');
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 姹囨€婚绾︾粺璁°€?     */
    private OperationMaintenanceResp.ReservationStats buildReservationStats(String licensePlate) {
        long total = travelReservationMapper.selectCount(new LambdaQueryWrapper<TravelReservation>()
                .eq(TravelReservation::getCarLicense, licensePlate));
        long passed = travelReservationMapper.selectCount(new LambdaQueryWrapper<TravelReservation>()
                .eq(TravelReservation::getCarLicense, licensePlate)
                .eq(TravelReservation::getIsPassed, 1));
        long rejected = travelReservationMapper.selectCount(new LambdaQueryWrapper<TravelReservation>()
                .eq(TravelReservation::getCarLicense, licensePlate)
                .eq(TravelReservation::getIsPassed, 0));

        OperationMaintenanceResp.ReservationStats stats = new OperationMaintenanceResp.ReservationStats();
        stats.setTotalCount(total);
        stats.setPassedCount(passed);
        stats.setRejectedCount(rejected);
        return stats;
    }

    /**
     * 鏌ヨ鏈€杩戜竴娆￠绾﹁褰曘€?     */
    private OperationMaintenanceResp.ReservationBrief buildLatestReservation(String licensePlate) {
        TravelReservation latest = travelReservationMapper.selectOne(new LambdaQueryWrapper<TravelReservation>()
                .eq(TravelReservation::getCarLicense, licensePlate)
                .orderByDesc(TravelReservation::getCreateTime)
                .last("LIMIT 1"));
        if (latest == null) {
            return null;
        }

        OperationMaintenanceResp.ReservationBrief brief = new OperationMaintenanceResp.ReservationBrief();
        brief.setId(latest.getId());
        brief.setTravelTimeSlot(latest.getTravelTimeSlot());
        brief.setStartPoint(latest.getStartPoint());
        brief.setEndPoint(latest.getEndPoint());
        brief.setVehicleType(latest.getCarType());
        brief.setIsPassed(latest.getIsPassed());
        brief.setRejectReason(latest.getRejectReason());
        brief.setCreateTime(latest.getCreateTime());
        return brief;
    }

    /**
     * 鎸夎溅鐗屾煡璇㈣溅杈嗕俊鎭紝涓嶅瓨鍦ㄥ垯鎶涗笟鍔″紓甯搞€?     */
    private CarInfo getVehicleByLicenseOrThrow(String licensePlate) {
        CarInfo carInfo = carInfoMapper.selectOne(new LambdaQueryWrapper<CarInfo>()
                .eq(CarInfo::getLicensePlate, licensePlate)
                .last("LIMIT 1"));
        if (carInfo == null) {
            throw BusinessException.notFound("vehicle not found");
        }
        return carInfo;
    }

    /**
     * 鏍囧噯鍖栧鏍告嫆缁濋」銆?     */
    private List<String> normalizeRejectItems(List<String> rejectItems) {
        if (rejectItems == null || rejectItems.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String raw : rejectItems) {
            if (!hasText(raw)) {
                continue;
            }
            String trimmed = raw.trim();
            String label = REJECT_ITEM_OPTIONS.get(trimmed);
            if (label != null) {
                result.add(label);
                continue;
            }
            if (REJECT_ITEM_OPTIONS.containsValue(trimmed)) {
                result.add(trimmed);
            }
        }
        return result.stream().distinct().toList();
    }

    private OperationMaintenanceResp.VehicleAuditRow toAuditRow(CarInfo item) {
        OperationMaintenanceResp.VehicleAuditRow row = new OperationMaintenanceResp.VehicleAuditRow();
        row.setId(item.getId());
        row.setVehicleName(item.getVehicleName());
        row.setLicensePlate(item.getLicensePlate());
        row.setAuditStatus(item.getAuditStatus());
        row.setAuditStatusText(toAuditStatusText(item.getAuditStatus()));
        row.setCurrentPoints(item.getCurrentPoints());
        row.setCreateTime(item.getCreateTime());
        return row;
    }

    private <T> OperationMaintenanceResp.PageData<T> buildPage(Page<?> src, List<T> rows) {
        OperationMaintenanceResp.PageData<T> pageData = new OperationMaintenanceResp.PageData<>();
        pageData.setPageNo(src.getCurrent());
        pageData.setPageSize(src.getSize());
        pageData.setTotal(src.getTotal());
        pageData.setRecords(rows);
        return pageData;
    }

    private <T> OperationMaintenanceResp.PageData<T> emptyPage(long pageNo, long pageSize) {
        OperationMaintenanceResp.PageData<T> pageData = new OperationMaintenanceResp.PageData<>();
        pageData.setPageNo(safePageNo(pageNo));
        pageData.setPageSize(safePageSize(pageSize));
        pageData.setTotal(0);
        pageData.setRecords(Collections.emptyList());
        return pageData;
    }

    private Map<String, String> loadVehicleTypeMap(List<String> licensePlates) {
        if (licensePlates == null || licensePlates.isEmpty()) {
            return Collections.emptyMap();
        }
        return carInfoMapper.selectList(new LambdaQueryWrapper<CarInfo>()
                        .in(CarInfo::getLicensePlate, licensePlates))
                .stream()
                .collect(Collectors.toMap(CarInfo::getLicensePlate, CarInfo::getVehicleType, (a, b) -> a));
    }

    private Map<String, CarInfo> loadCarInfoMap(List<String> licensePlates) {
        if (licensePlates == null || licensePlates.isEmpty()) {
            return Collections.emptyMap();
        }
        return carInfoMapper.selectList(new LambdaQueryWrapper<CarInfo>()
                        .in(CarInfo::getLicensePlate, licensePlates))
                .stream()
                .collect(Collectors.toMap(CarInfo::getLicensePlate, item -> item, (a, b) -> a));
    }

    private String buildDeductionRecord(CarInfo carInfo) {
        if (carInfo == null || carInfo.getCurrentPoints() == null) {
            return "none";
        }
        int remain = carInfo.getCurrentPoints();
        int deducted = Math.max(0, 12 - remain);
        if (deducted == 0) {
            return "none";
        }
        return "deduct " + deducted + " points, remain " + remain;
    }

    private List<OperationMaintenanceResp.AuditCheckItem> buildCheckItems(String rejectReason) {
        Set<String> reasonWords = splitRejectReason(rejectReason);
        List<OperationMaintenanceResp.AuditCheckItem> result = new ArrayList<>();

        for (Map.Entry<String, String> entry : REJECT_ITEM_OPTIONS.entrySet()) {
            OperationMaintenanceResp.AuditCheckItem item = new OperationMaintenanceResp.AuditCheckItem();
            item.setKey(entry.getKey());
            item.setLabel(entry.getValue());
            item.setChecked(isChecked(entry.getValue(), reasonWords));
            result.add(item);
        }
        return result;
    }

    private boolean isChecked(String label, Set<String> reasonWords) {
        if (reasonWords.isEmpty()) {
            return false;
        }
        if (reasonWords.contains(label)) {
            return true;
        }
        for (String word : reasonWords) {
            if (label.contains(word) || word.contains(label)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> splitRejectReason(String rejectReason) {
        if (!hasText(rejectReason)) {
            return Collections.emptySet();
        }
        return Arrays.stream(rejectReason.split("[,;|]"))
                .map(String::trim)
                .filter(this::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 瑙ｆ瀽 ISO-8601 鏃堕棿鍙傛暟銆?     */
    private LocalDateTime parseDateTime(String value, String fieldName) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            throw BusinessException.badRequest(fieldName + " must be ISO-8601, e.g. 2026-04-01T08:30:00");
        }
    }

    /**
     * 瀹℃牳鐘舵€佺紪鐮佽浆鏂囨湰銆?     */
    private String toAuditStatusText(String auditStatus) {
        if (!hasText(auditStatus)) {
            return "unknown";
        }
        return switch (auditStatus) {
            case "unaudited" -> "pending";
            case "passed" -> "approved";
            case "rejected" -> "rejected";
            default -> "unknown";
        };
    }

    private String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
    private Integer resolveDirectionCode(UcCarRealTime carRealTime) {
        if (carRealTime == null) {
            return null;
        }
        Integer code = carRealTime.getDirection();
        if (code != null && (code == 1 || code == 2)) {
            return code;
        }
        return toDirectionCode(carRealTime.getDrivingDirection());
    }

    private Integer toDirectionCode(String rawDirection) {
        if (!hasText(rawDirection)) {
            return null;
        }
        String s = rawDirection.trim().toLowerCase();
        if ("1".equals(s)
                || "\u5410\u9c81\u756a".equals(s)
                || "\u4e0a\u884c".equals(s)
                || "turpan".equals(s)
                || "tulufan".equals(s)
                || "toez".equals(s)
                || "to_ez".equals(s)
                || "hamimi_to_tuyugou".equals(s)
                || "hami_to_turpan".equals(s)
                || "to_turpan".equals(s)) {
            return 1;
        }
        if ("2".equals(s)
                || "\u54c8\u5bc6".equals(s)
                || "\u4e0b\u884c".equals(s)
                || "hami".equals(s)
                || "towh".equals(s)
                || "to_wh".equals(s)
                || "tuyugou_to_hamimi".equals(s)
                || "turpan_to_hami".equals(s)
                || "to_hami".equals(s)) {
            return 2;
        }
        return null;
    }
    private long safePageNo(long pageNo) {
        return pageNo <= 0 ? 1 : pageNo;
    }

    private long safePageSize(long pageSize) {
        if (pageSize <= 0) {
            return 10;
        }
        return Math.min(pageSize, 200);
    }

    /**
     * 鍒濆鍖栧鏍告嫆缁濋」閰嶇疆銆?     */
    private static Map<String, String> buildRejectItemOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("plate", "plate number error");
        options.put("vehicleType", "vehicle type error");
        options.put("owner", "owner error");
        options.put("usageNature", "usage nature error");
        options.put("brandModel", "brand model error");
        options.put("vin", "vin error");
        options.put("engineNumber", "engine number error");
        options.put("registerDate", "register date error");
        options.put("other", "other");
        return options;
    }
}

