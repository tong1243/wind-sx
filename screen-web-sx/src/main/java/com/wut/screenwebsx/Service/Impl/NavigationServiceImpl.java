package com.wut.screenwebsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wut.screencommonsx.Exception.BusinessException;
import com.wut.screencommonsx.Model.CarInfo;
import com.wut.screencommonsx.Model.NavigationSettlement;
import com.wut.screencommonsx.Model.TravelReservation;
import com.wut.screencommonsx.Model.UcCarRealTime;
import com.wut.screencommonsx.Model.UserAccount;
import com.wut.screencommonsx.Request.NavigationSettlementRequest;
import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screencommonsx.Response.NavigationSettlementResponse;
import com.wut.screenwebsx.Controller.NavigationController;
import com.wut.screenwebsx.Mapper.CarInfoMapper;
import com.wut.screenwebsx.Mapper.NavigationSettlementMapper;
import com.wut.screenwebsx.Mapper.TravelReservationMapper;
import com.wut.screenwebsx.Mapper.UcCarRealTimeMapper;
import com.wut.screenwebsx.Mapper.UserAccountMapper;
import com.wut.screenwebsx.Service.NavigationService;
import com.wut.screenwebsx.Service.UserNoticePublishService;
import com.wut.screenwebsx.Service.WindControlWindImpactService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class NavigationServiceImpl implements NavigationService {
    private static final int RESERVATION_REJECTED = 0;
    private static final int RESERVATION_APPROVED = 1;
    private static final int RESERVATION_PENDING = 2;
    private static final int RESERVATION_FINISHED = 3;
    private static final Set<String> SUPPORTED_END_REASONS = Set.of("manual", "route_completed", "ramp_timeout");
    private static final Map<String, Integer> DEDUCTION_POINTS = Map.of(
            "ZERO_SPEED_TIMEOUT", 2,
            "OVERSPEED_TIMEOUT", 2,
            "MANUAL_END_IN_RESERVATION", 1
    );
    private static final DateTimeFormatter NOTICE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern PILE_PATTERN = Pattern.compile("K?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:\\+\\s*(\\d+(?:\\.\\d+)?))?", Pattern.CASE_INSENSITIVE);

    private final UcCarRealTimeMapper ucCarRealTimeMapper;
    private final NavigationSettlementMapper navigationSettlementMapper;
    private final CarInfoMapper carInfoMapper;
    private final TravelReservationMapper travelReservationMapper;
    private final UserAccountMapper userAccountMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final UserNoticePublishService userNoticePublishService;
    private final WindControlWindImpactService windControlWindImpactService;
    private final ConcurrentHashMap<String, Long> realtimeNavLastVisitMs = new ConcurrentHashMap<>();
    private final Object realtimeResetLock = new Object();

    @Value("${app.realtime-navigation.data-timeout-seconds:120}")
    private long dataTimeoutSeconds;

    @Value("${app.realtime-navigation.auto-reset-entry-gap-seconds:30}")
    private long autoResetEntryGapSeconds;

    @PostConstruct
    public void initNavigationSettlementSchema() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS navigation_settlement (
                      id BIGINT NOT NULL AUTO_INCREMENT,
                      settlement_id VARCHAR(64) NULL,
                      navigation_session_id VARCHAR(96) NULL,
                      user_phone VARCHAR(32) NULL,
                      car_license VARCHAR(32) NULL,
                      reservation_id VARCHAR(64) NULL,
                      end_reason VARCHAR(32) NULL,
                      ended_at BIGINT NULL,
                      car_snapshot_json TEXT NULL,
                      items_json TEXT NULL,
                      total_deduction INT NULL DEFAULT 0,
                      deducted_points INT NULL DEFAULT 0,
                      remaining_points INT NULL DEFAULT 0,
                      start_pile VARCHAR(64) NULL,
                      end_pile VARCHAR(64) NULL,
                      event_info TEXT NULL,
                      overspeed_count INT NULL DEFAULT 0,
                      speed_record_count INT NULL DEFAULT 0,
                      park_count INT NULL DEFAULT 0,
                      deduct_points INT NULL DEFAULT 0,
                      navigation_start_time DATETIME NULL,
                      navigation_end_time DATETIME NULL,
                      create_time DATETIME NULL,
                      PRIMARY KEY (id)
                    )
                    """);
            addColumnIfMissing("settlement_id", "VARCHAR(64) NULL");
            addColumnIfMissing("navigation_session_id", "VARCHAR(96) NULL");
            addColumnIfMissing("reservation_id", "VARCHAR(64) NULL");
            addColumnIfMissing("end_reason", "VARCHAR(32) NULL");
            addColumnIfMissing("ended_at", "BIGINT NULL");
            addColumnIfMissing("car_snapshot_json", "TEXT NULL");
            addColumnIfMissing("items_json", "TEXT NULL");
            addColumnIfMissing("total_deduction", "INT NULL DEFAULT 0");
            addColumnIfMissing("deducted_points", "INT NULL DEFAULT 0");
            addColumnIfMissing("remaining_points", "INT NULL DEFAULT 0");
            addColumnIfMissing("start_pile", "VARCHAR(64) NULL");
            addColumnIfMissing("end_pile", "VARCHAR(64) NULL");
            addColumnIfMissing("event_info", "TEXT NULL");
            addColumnIfMissing("overspeed_count", "INT NULL DEFAULT 0");
            addColumnIfMissing("speed_record_count", "INT NULL DEFAULT 0");
            addColumnIfMissing("park_count", "INT NULL DEFAULT 0");
            addColumnIfMissing("deduct_points", "INT NULL DEFAULT 0");
            addColumnIfMissing("navigation_start_time", "DATETIME NULL");
            addColumnIfMissing("navigation_end_time", "DATETIME NULL");
            addColumnIfMissing("create_time", "DATETIME NULL");
            makeColumnNullableIfNeeded("start_pile", "VARCHAR(64)");
            makeColumnNullableIfNeeded("end_pile", "VARCHAR(64)");
            makeColumnNullableIfNeeded("navigation_start_time", "DATETIME");
            addUniqueIndexIfMissing("uk_navigation_settlement_session", "navigation_session_id");
        } catch (Exception ex) {
            log.error("初始化 navigation_settlement 表结构失败", ex);
        }
    }

    @Override
    public ApiResponse<?> resetRealTimeNavigationData() {
        int deleted = ucCarRealTimeMapper.clearAll();
        int currentDeleted = ucCarRealTimeMapper.clearCurrentAll();
        log.info("Cleared uc_car_real_time rows: {}, current rows: {}", deleted, currentDeleted);
        return ApiResponse.success("重置成功", deleted + currentDeleted);
    }

    @Override
    public ApiResponse<?> getCarRealInfo(String phone) {
        if (phone == null || phone.isBlank()) {
            return ApiResponse.badRequest("用户信息不存在");
        }
        maybeResetRealtimeDataOnEntry(phone);

        UcCarRealTime carRealTime = ucCarRealTimeMapper.selectLatestByPhoneFromCurrent(phone);
        if (carRealTime == null) {
            carRealTime = ucCarRealTimeMapper.selectLatestByPhone(phone);
        }
        if (carRealTime == null) {
            return ApiResponse.badRequest("暂无车辆实时数据");
        }
        if (isRealtimeDataExpired(carRealTime.getReportTime())) {
            return ApiResponse.badRequest("导航已结束或暂无车辆实时数据");
        }
        if (isNavigationFinished(phone, carRealTime.getCarLicense(), carRealTime.getReportTime())) {
            return ApiResponse.badRequest("导航已结束或暂无车辆实时数据");
        }

        CarInfoResponse response = new CarInfoResponse();
        response.setSpeed(carRealTime.getRealSpeed());
        response.setLane(carRealTime.getLaneNumber());
        response.setLine(carRealTime.getLaneNumber());
        response.setRoad(resolveRoadCode(carRealTime));
        response.setPile(carRealTime.getCurrentPile());
        response.setDirection(carRealTime.getDirection());
        response.setVehicleType(getCarType(carRealTime.getCarLicense()));

        return ApiResponse.success("获取成功", response);
    }

    @Override
    public ApiResponse<List<NavigationController.WindZoneInfo>> getWindZoneInfo() {
        List<NavigationController.WindZoneInfo> windZones = List.of(
                new NavigationController.WindZoneInfo(1000, 1500),
                new NavigationController.WindZoneInfo(2000, 2500),
                new NavigationController.WindZoneInfo(3000, 3500)
        );
        return ApiResponse.success("获取成功", windZones);
    }

    @Override
    public ApiResponse<NavigationController.OverviewInfo> getOverview() {
        LocalDateTime now = LocalDateTime.now();
        long reservedVehicleCount = travelReservationMapper.countActiveReservedVehicles(now);
        long riskSectionVehicleCount = countRiskSectionVehicles(now);
        return ApiResponse.success("获取成功",
                new NavigationController.OverviewInfo(reservedVehicleCount, riskSectionVehicleCount));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<?> settleNavigation(NavigationSettlementRequest request, String phone) {
        if (phone == null || phone.isBlank()) {
            return ApiResponse.unauthorized("未登录或登录已失效");
        }
        validateSettlementRequest(request);

        NavigationSettlement existing = navigationSettlementMapper
                .selectByNavigationSessionId(request.getNavigationSessionId());
        if (existing != null) {
            finishReservationAfterSettlement(firstNonBlank(existing.getReservationId(), request.getReservationId()), phone);
            return ApiResponse.success("结算成功", toSettlementResponse(existing));
        }

        TravelReservation reservation = resolveReservation(request.getReservationId(), phone);
        String carLicense = resolveCarLicense(phone, reservation);
        if (carLicense == null || carLicense.isBlank()) {
            throw BusinessException.badRequest("结算请求参数错误");
        }

        CarInfo carInfo = carInfoMapper.selectOne(new LambdaQueryWrapper<CarInfo>()
                .eq(CarInfo::getLicensePlate, carLicense)
                .last("LIMIT 1"));
        if (carInfo == null || !canUserOperateVehicle(phone, carInfo)) {
            throw BusinessException.badRequest("结算请求参数错误");
        }

        NavigationSettlement settlement = buildSettlement(request, phone, carLicense, 0,
                carInfo.getCurrentPoints() == null ? 12 : Math.max(carInfo.getCurrentPoints(), 0));
        try {
            navigationSettlementMapper.insert(settlement);
        } catch (DuplicateKeyException ex) {
            NavigationSettlement duplicated = navigationSettlementMapper
                    .selectByNavigationSessionId(request.getNavigationSessionId());
            if (duplicated != null) {
                finishReservationAfterSettlement(firstNonBlank(duplicated.getReservationId(), request.getReservationId()), phone);
                return ApiResponse.success("结算成功", toSettlementResponse(duplicated));
            }
            throw ex;
        }

        carInfo = carInfoMapper.selectByLicensePlateForUpdate(carLicense);
        if (carInfo == null || !canUserOperateVehicle(phone, carInfo)) {
            throw BusinessException.badRequest("结算请求参数错误");
        }

        int totalDeduction = request.getTotalDeduction() == null ? 0 : request.getTotalDeduction();
        int currentPoints = carInfo.getCurrentPoints() == null ? 12 : Math.max(carInfo.getCurrentPoints(), 0);
        int deductedPoints = Math.min(currentPoints, totalDeduction);
        int remainingPoints = Math.max(0, currentPoints - deductedPoints);

        carInfo.setCurrentPoints(remainingPoints);
        carInfo.setUpdateTime(LocalDateTime.now());
        carInfoMapper.updateById(carInfo);

        settlement.setDeductedPoints(deductedPoints);
        settlement.setRemainingPoints(remainingPoints);
        settlement.setDeductPoints(-deductedPoints);
        navigationSettlementMapper.updateById(settlement);
        finishReservationAfterSettlement(reservation);
        userNoticePublishService.publishNavigationSettlement(
                phone,
                carLicense,
                request.getNavigationSessionId(),
                deductedPoints,
                remainingPoints,
                request.getEndReason(),
                formatTravelMileage(request, phone, carLicense),
                formatTravelDuration(request),
                formatEndedAt(request.getEndedAt())
        );
        return ApiResponse.success("结算成功", toSettlementResponse(settlement));
    }

    private void finishReservationAfterSettlement(String reservationId, String phone) {
        TravelReservation reservation = resolveReservation(reservationId, phone);
        finishReservationAfterSettlement(reservation);
    }

    private void finishReservationAfterSettlement(TravelReservation reservation) {
        if (reservation == null) {
            return;
        }
        Integer status = reservation.getIsPassed();
        if (status != null && status != RESERVATION_PENDING && status != RESERVATION_APPROVED) {
            return;
        }
        reservation.setIsPassed(RESERVATION_FINISHED);
        reservation.setUpdateTime(LocalDateTime.now());
        travelReservationMapper.updateById(reservation);
    }

    private void validateSettlementRequest(NavigationSettlementRequest request) {
        if (request == null
                || isBlank(request.getNavigationSessionId())
                || isBlank(request.getEndReason())
                || request.getEndedAt() == null
                || request.getTotalDeduction() == null
                || request.getItems() == null) {
            throw BusinessException.badRequest("结算请求参数错误");
        }
        if (!SUPPORTED_END_REASONS.contains(request.getEndReason())) {
            throw BusinessException.badRequest("结算请求参数错误");
        }
        if (request.getTotalDeduction() < 0) {
            throw BusinessException.badRequest("结算请求参数错误");
        }

        int sum = 0;
        for (NavigationSettlementRequest.DeductionItem item : request.getItems()) {
            if (item == null || isBlank(item.getCode())
                    || item.getCount() == null || item.getCount() < 1
                    || item.getPointsPerOccurrence() == null
                    || item.getDeductionPoints() == null) {
                throw BusinessException.badRequest("结算请求参数错误");
            }
            Integer expectedPoints = DEDUCTION_POINTS.get(item.getCode());
            if (expectedPoints == null
                    || !expectedPoints.equals(item.getPointsPerOccurrence())
                    || item.getDeductionPoints() != item.getCount() * item.getPointsPerOccurrence()) {
                throw BusinessException.badRequest("结算请求参数错误");
            }
            sum += item.getDeductionPoints();
        }
        if (sum != request.getTotalDeduction()) {
            throw BusinessException.badRequest("结算请求参数错误");
        }
    }

    private TravelReservation resolveReservation(String reservationId, String phone) {
        if (isBlank(reservationId)) {
            return null;
        }
        Long reservationDbId = parseReservationDbId(reservationId);
        if (reservationDbId == null) {
            throw BusinessException.badRequest("结算请求参数错误");
        }
        TravelReservation reservation = travelReservationMapper.selectById(reservationDbId);
        if (reservation == null || !sameText(reservation.getUserPhone(), phone)) {
            throw BusinessException.badRequest("结算请求参数错误");
        }
        return reservation;
    }

    private Long parseReservationDbId(String reservationId) {
        String s = reservationId == null ? null : reservationId.trim();
        if (isBlank(s)) {
            return null;
        }
        if (s.matches("\\d+")) {
            return Long.parseLong(s);
        }
        int idx = s.length() - 1;
        while (idx >= 0 && Character.isDigit(s.charAt(idx))) {
            idx--;
        }
        if (idx == s.length() - 1) {
            return null;
        }
        return Long.parseLong(s.substring(idx + 1));
    }

    private String resolveCarLicense(String phone, TravelReservation reservation) {
        if (reservation != null && !isBlank(reservation.getCarLicense())) {
            return reservation.getCarLicense().trim();
        }
        UcCarRealTime latest = ucCarRealTimeMapper.selectLatestByPhoneFromCurrent(phone);
        if (latest == null) {
            latest = ucCarRealTimeMapper.selectLatestByPhone(phone);
        }
        return latest == null ? null : latest.getCarLicense();
    }

    private boolean canUserOperateVehicle(String phone, CarInfo carInfo) {
        if (carInfo == null) {
            return false;
        }
        if (sameText(carInfo.getSubmitterPhone(), phone)) {
            return true;
        }
        UserAccount user = userAccountMapper.selectById(phone);
        if (user == null) {
            return false;
        }
        String licensePlate = carInfo.getLicensePlate();
        return sameText(user.getCar1License(), licensePlate)
                || sameText(user.getCar2License(), licensePlate)
                || sameText(user.getCar3License(), licensePlate);
    }

    private NavigationSettlement buildSettlement(NavigationSettlementRequest request,
                                                 String phone,
                                                 String carLicense,
                                                 int deductedPoints,
                                                 int remainingPoints) {
        NavigationSettlement settlement = new NavigationSettlement();
        settlement.setSettlementId("SETTLEMENT_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8));
        settlement.setNavigationSessionId(request.getNavigationSessionId());
        settlement.setUserPhone(phone);
        settlement.setCarLicense(carLicense);
        settlement.setReservationId(request.getReservationId());
        settlement.setEndReason(request.getEndReason());
        settlement.setEndedAt(request.getEndedAt());
        settlement.setCarSnapshotJson(toJson(request.getCarSnapshot()));
        settlement.setItemsJson(toJson(request.getItems()));
        settlement.setTotalDeduction(request.getTotalDeduction());
        settlement.setDeductedPoints(deductedPoints);
        settlement.setRemainingPoints(remainingPoints);
        settlement.setDeductPoints(-deductedPoints);
        settlement.setEventInfo(toJson(request.getItems()));
        settlement.setOverspeedCount(countItem(request, "OVERSPEED_TIMEOUT"));
        settlement.setParkCount(countItem(request, "ZERO_SPEED_TIMEOUT"));
        settlement.setSpeedRecordCount(0);
        settlement.setNavigationEndTime(toLocalDateTime(request.getEndedAt()));
        settlement.setCreateTime(LocalDateTime.now());
        settlement.setStartPile(blankToNull(request.getStartPile()));
        settlement.setEndPile(blankToNull(request.getEndPile()));
        if (request.getCarSnapshot() != null) {
            settlement.setEndPile(firstNonBlank(settlement.getEndPile(), request.getCarSnapshot().getPile()));
        }
        return settlement;
    }

    private NavigationSettlementResponse toSettlementResponse(NavigationSettlement settlement) {
        NavigationSettlementResponse response = new NavigationSettlementResponse();
        response.setSettlementId(settlement.getSettlementId() == null
                ? "SETTLEMENT_" + settlement.getId()
                : settlement.getSettlementId());
        response.setNavigationSessionId(settlement.getNavigationSessionId());
        response.setDeductedPoints(settlement.getDeductedPoints() == null
                ? Math.max(0, Math.abs(settlement.getDeductPoints() == null ? 0 : settlement.getDeductPoints()))
                : settlement.getDeductedPoints());
        response.setRemainingPoints(settlement.getRemainingPoints());
        return response;
    }

    private int countItem(NavigationSettlementRequest request, String code) {
        return request.getItems().stream()
                .filter(item -> code.equals(item.getCode()))
                .map(NavigationSettlementRequest.DeductionItem::getCount)
                .filter(count -> count != null)
                .findFirst()
                .orElse(0);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw BusinessException.badRequest("结算请求参数错误");
        }
    }

    private LocalDateTime toLocalDateTime(Long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private String formatTravelMileage(NavigationSettlementRequest request, String phone, String carLicense) {
        Double mileage = request.getTravelMileageKm() != null
                ? request.getTravelMileageKm()
                : request.getDrivingMileageKm();
        if (mileage == null) {
            mileage = calculateMileageFromRequestPiles(request);
        }
        if (mileage == null) {
            mileage = calculateMileageFromRealtimeHistory(request, phone, carLicense);
        }
        if (mileage == null || mileage < 0) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.2f km", mileage);
    }

    private Double calculateMileageFromRequestPiles(NavigationSettlementRequest request) {
        Double startKm = parsePileKm(request.getStartPile());
        String endPile = request.getEndPile();
        if (isBlank(endPile) && request.getCarSnapshot() != null) {
            endPile = request.getCarSnapshot().getPile();
        }
        Double endKm = parsePileKm(endPile);
        if (startKm == null || endKm == null) {
            return null;
        }
        return Math.abs(endKm - startKm);
    }

    private Double calculateMileageFromRealtimeHistory(NavigationSettlementRequest request, String phone, String carLicense) {
        Long startedAt = parseStartedAtFromSessionId(request.getNavigationSessionId());
        if (startedAt == null || request.getEndedAt() == null || isBlank(phone) || isBlank(carLicense)) {
            return null;
        }
        LocalDateTime startTime = toLocalDateTime(startedAt);
        LocalDateTime endTime = toLocalDateTime(request.getEndedAt());
        if (endTime.isBefore(startTime)) {
            return null;
        }
        List<UcCarRealTime> rows = ucCarRealTimeMapper.selectByPhoneAndCarBetween(phone, carLicense, startTime, endTime);
        if (rows == null || rows.size() < 2) {
            return null;
        }
        double mileageKm = 0.0D;
        Double previousKm = null;
        for (UcCarRealTime row : rows) {
            Double currentKm = parsePileKm(row.getCurrentPile());
            if (currentKm == null) {
                continue;
            }
            if (previousKm != null) {
                mileageKm += Math.abs(currentKm - previousKm);
            }
            previousKm = currentKm;
        }
        return mileageKm > 0.0D ? mileageKm : null;
    }

    private Double parsePileKm(String pile) {
        if (isBlank(pile)) {
            return null;
        }
        Matcher matcher = PILE_PATTERN.matcher(pile.trim());
        if (!matcher.find()) {
            return null;
        }
        double km = Double.parseDouble(matcher.group(1));
        String meterText = matcher.group(2);
        if (!isBlank(meterText)) {
            km += Double.parseDouble(meterText) / 1000.0D;
        }
        return km;
    }

    private long countRiskSectionVehicles(LocalDateTime now) {
        List<RiskRange> riskRanges = resolveCurrentRiskRanges();
        if (riskRanges.isEmpty()) {
            return 0L;
        }

        long safeTimeout = Math.max(dataTimeoutSeconds, 30L);
        List<UcCarRealTime> rows = ucCarRealTimeMapper.selectCurrentForOverview(now.minusSeconds(safeTimeout));
        if (rows == null || rows.isEmpty()) {
            return 0L;
        }

        Set<String> vehicleKeys = new HashSet<>();
        for (UcCarRealTime row : rows) {
            Double pileKm = parsePileKm(row.getCurrentPile());
            Integer direction = resolveDirectionCode(row);
            if (pileKm == null || direction == null) {
                continue;
            }
            for (RiskRange range : riskRanges) {
                if (range.contains(direction, pileKm)) {
                    vehicleKeys.add(vehicleKey(row));
                    break;
                }
            }
        }
        return vehicleKeys.size();
    }

    private List<RiskRange> resolveCurrentRiskRanges() {
        try {
            Map<String, Object> data = windControlWindImpactService.evaluateSpatiotemporalImpact(
                    System.currentTimeMillis(), "real", null);
            Object rawRecords = data == null ? null : data.get("records");
            if (!(rawRecords instanceof List<?> records)) {
                return List.of();
            }

            List<RiskRange> ranges = new ArrayList<>();
            for (Object item : records) {
                if (!(item instanceof Map<?, ?> record)) {
                    continue;
                }
                Integer controlLevel = toInteger(record.get("recommendedControlLevel"));
                if (controlLevel == null || controlLevel >= 5) {
                    continue;
                }
                Integer direction = toInteger(record.get("direction"));
                String stakeRange = stringValue(record.get("stakeRange"));
                RiskRange range = parseRiskRange(direction, stakeRange);
                if (range != null) {
                    ranges.add(range);
                }
            }
            return ranges;
        } catch (Exception ex) {
            log.warn("resolve app navigation risk ranges failed", ex);
            return List.of();
        }
    }

    private RiskRange parseRiskRange(Integer direction, String stakeRange) {
        if (direction == null || direction <= 0 || isBlank(stakeRange)) {
            return null;
        }
        String[] parts = stakeRange.split("-", 2);
        if (parts.length != 2) {
            return null;
        }
        Double startKm = parsePileKm(parts[0]);
        Double endKm = parsePileKm(parts[1]);
        if (startKm == null || endKm == null) {
            return null;
        }
        return new RiskRange(direction, Math.min(startKm, endKm), Math.max(startKm, endKm));
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return (int) Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String vehicleKey(UcCarRealTime row) {
        if (!isBlank(row.getCarLicense())) {
            return row.getCarLicense().trim().toUpperCase(Locale.ROOT);
        }
        if (!isBlank(row.getUserPhone())) {
            return "PHONE:" + row.getUserPhone().trim();
        }
        return "ID:" + row.getId();
    }

    private record RiskRange(int direction, double startKm, double endKm) {
        boolean contains(int targetDirection, double pileKm) {
            return direction == targetDirection && pileKm >= startKm && pileKm <= endKm;
        }
    }

    private String formatTravelDuration(NavigationSettlementRequest request) {
        Long durationSeconds = request.getTravelDurationSeconds() != null
                ? request.getTravelDurationSeconds()
                : request.getDrivingDurationSeconds();
        if (durationSeconds == null) {
            durationSeconds = inferDurationSeconds(request.getNavigationSessionId(), request.getEndedAt());
        }
        if (durationSeconds == null || durationSeconds < 0) {
            return "-";
        }
        long hours = durationSeconds / 3600;
        long minutes = (durationSeconds % 3600) / 60;
        long seconds = durationSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d小时%d分%d秒", hours, minutes, seconds);
        }
        if (minutes > 0) {
            return String.format(Locale.ROOT, "%d分%d秒", minutes, seconds);
        }
        return seconds + "秒";
    }

    private Long inferDurationSeconds(String navigationSessionId, Long endedAt) {
        Long startedAt = parseStartedAtFromSessionId(navigationSessionId);
        if (startedAt == null || endedAt == null) {
            return null;
        }
        if (endedAt < startedAt) {
            return null;
        }
        return (endedAt - startedAt) / 1000;
    }

    private Long parseStartedAtFromSessionId(String navigationSessionId) {
        if (isBlank(navigationSessionId)) {
            return null;
        }
        String[] parts = navigationSessionId.split("_");
        if (parts.length < 2 || !parts[1].matches("\\d+")) {
            return null;
        }
        return Long.parseLong(parts[1]);
    }

    private String formatEndedAt(Long endedAt) {
        if (endedAt == null) {
            return "-";
        }
        return toLocalDateTime(endedAt).format(NOTICE_TIME_FORMATTER);
    }

    private void addColumnIfMissing(String columnName, String columnDefinition) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'navigation_settlement'
                  AND COLUMN_NAME = ?
                """, Integer.class, columnName);
        if (count == null || count <= 0) {
            jdbcTemplate.execute("ALTER TABLE navigation_settlement ADD COLUMN " + columnName + " " + columnDefinition);
        }
    }

    private void makeColumnNullableIfNeeded(String columnName, String columnType) {
        String nullable = jdbcTemplate.queryForObject("""
                SELECT IS_NULLABLE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'navigation_settlement'
                  AND COLUMN_NAME = ?
                """, String.class, columnName);
        if (!"YES".equalsIgnoreCase(nullable)) {
            jdbcTemplate.execute("ALTER TABLE navigation_settlement MODIFY COLUMN " + columnName + " " + columnType + " NULL");
        }
    }

    private void addUniqueIndexIfMissing(String indexName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'navigation_settlement'
                  AND INDEX_NAME = ?
                """, Integer.class, indexName);
        if (count == null || count <= 0) {
            jdbcTemplate.execute("ALTER TABLE navigation_settlement ADD UNIQUE KEY " + indexName + " (" + columnName + ")");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String firstNonBlank(String preferred, String fallback) {
        return !isBlank(preferred) ? preferred : blankToNull(fallback);
    }

    private boolean sameText(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private String getCarType(String licensePlate) {
        String fromCarInfo = resolveVehicleTypeFromCarInfo(licensePlate);
        return "2".equals(fromCarInfo) ? "2" : "1";
    }

    private String resolveVehicleTypeFromCarInfo(String licensePlate) {
        if (licensePlate == null || licensePlate.isBlank()) {
            return null;
        }
        CarInfo carInfo = carInfoMapper.selectOne(new LambdaQueryWrapper<CarInfo>()
                .eq(CarInfo::getLicensePlate, licensePlate.trim())
                .last("LIMIT 1"));
        if (carInfo == null || carInfo.getVehicleType() == null || carInfo.getVehicleType().isBlank()) {
            return null;
        }
        String s = carInfo.getVehicleType().trim();
        if ("1".equals(s) || "2".equals(s)) {
            return s;
        }
        return null;
    }

    private Integer resolveRoadCode(UcCarRealTime carRealTime) {
        if (carRealTime != null && carRealTime.getRoad() != null && carRealTime.getRoad() > 0) {
            return carRealTime.getRoad();
        }
        Integer direction = resolveDirectionCode(carRealTime);
        if (direction == null) {
            return null;
        }
        // 主线道路编号映射（按内部静态表）：
        // direction=1(去往哈密/下行) -> road=2(左幅)
        // direction=2(去往吐鲁番/上行) -> road=1(右幅)
        if (direction == 1) {
            return 2;
        }
        if (direction == 2) {
            return 1;
        }
        return null;
    }

    private void maybeResetRealtimeDataOnEntry(String phone) {
        long now = System.currentTimeMillis();
        long minGapMs = Math.max(autoResetEntryGapSeconds, 5L) * 1000L;
        Long lastVisit = realtimeNavLastVisitMs.put(phone, now);
        if (lastVisit != null && now - lastVisit < minGapMs) {
            return;
        }
        synchronized (realtimeResetLock) {
            int deleted = ucCarRealTimeMapper.clearAll();
            int currentDeleted = ucCarRealTimeMapper.clearCurrentAll();
            log.info("Auto reset uc_car_real_time on navigation entry, phone={}, deleted={}, currentDeleted={}",
                    phone, deleted, currentDeleted);
        }
    }

    private boolean isRealtimeDataExpired(LocalDateTime reportTime) {
        if (reportTime == null) {
            return true;
        }
        long safeTimeout = Math.max(dataTimeoutSeconds, 30L);
        return reportTime.isBefore(LocalDateTime.now().minusSeconds(safeTimeout));
    }

    private boolean isNavigationFinished(String phone, String carLicense, LocalDateTime reportTime) {
        if (phone == null || phone.isBlank() || carLicense == null || carLicense.isBlank()) {
            return false;
        }
        NavigationSettlement latestSettlement = navigationSettlementMapper.selectLatestByPhoneAndCar(phone, carLicense);
        if (latestSettlement == null || latestSettlement.getNavigationEndTime() == null) {
            return false;
        }
        if (reportTime == null) {
            return true;
        }
        return !reportTime.isAfter(latestSettlement.getNavigationEndTime());
    }

    private Integer resolveDirectionCode(UcCarRealTime carRealTime) {
        if (carRealTime == null) {
            return null;
        }
        Integer normalizedFromText = toDirectionCode(carRealTime.getDrivingDirection());
        if (normalizedFromText != null) {
            return normalizedFromText;
        }
        Integer code = carRealTime.getDirection();
        if (code != null && (code == 1 || code == 2)) {
            return code;
        }
        return null;
    }

    private Integer toDirectionCode(String rawDirection) {
        if (rawDirection == null || rawDirection.isBlank()) {
            return null;
        }
        String s = rawDirection.trim().toLowerCase(Locale.ROOT);
        if ("1".equals(s)
                || "哈密".equals(s)
                || "下行".equals(s)
                || "hami".equals(s)
                || "towh".equals(s)
                || "to_wh".equals(s)
                || "tuyugou_to_hamimi".equals(s)
                || "turpan_to_hami".equals(s)
                || "to_hami".equals(s)) {
            return 1;
        }
        if ("2".equals(s)
                || "吐鲁番".equals(s)
                || "上行".equals(s)
                || "turpan".equals(s)
                || "tulufan".equals(s)
                || "toez".equals(s)
                || "to_ez".equals(s)
                || "hamimi_to_tuyugou".equals(s)
                || "hami_to_turpan".equals(s)
                || "to_turpan".equals(s)) {
            return 2;
        }
        return null;
    }

    public static class CarInfoResponse {
        private Integer speed;
        private Integer lane;
        private Integer line;
        private Integer road;
        private String pile;
        private Integer direction;
        private String vehicleType;

        public Integer getSpeed() {
            return speed;
        }

        public void setSpeed(Integer speed) {
            this.speed = speed;
        }

        public Integer getLane() {
            return lane;
        }

        public void setLane(Integer lane) {
            this.lane = lane;
        }

        public Integer getLine() {
            return line;
        }

        public void setLine(Integer line) {
            this.line = line;
        }

        public Integer getRoad() {
            return road;
        }

        public void setRoad(Integer road) {
            this.road = road;
        }

        public String getPile() {
            return pile;
        }

        public void setPile(String pile) {
            this.pile = pile;
        }

        public Integer getDirection() {
            return direction;
        }

        public void setDirection(Integer direction) {
            this.direction = direction;
        }

        public String getVehicleType() {
            return vehicleType;
        }

        public void setVehicleType(String vehicleType) {
            this.vehicleType = vehicleType;
        }
    }
}
