package com.wut.screenwebsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screencommonsx.Exception.BusinessException;
import com.wut.screencommonsx.Model.CarInfo;
import com.wut.screencommonsx.Model.UserAccount;
import com.wut.screencommonsx.Request.VehicleRegisterRequest;
import com.wut.screencommonsx.Request.VehicleUpdateRequest;
import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screenwebsx.Mapper.CarInfoMapper;
import com.wut.screenwebsx.Mapper.UserAccountMapper;
import com.wut.screenwebsx.Service.CarInfoService;
import com.wut.screenwebsx.Service.UserNoticePublishService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CarInfoServiceImpl extends ServiceImpl<CarInfoMapper, CarInfo> implements CarInfoService {
    private static final Pattern VEHICLE_NAME_PATTERN = Pattern.compile("^[\\p{IsHan}A-Za-z0-9]{1,6}$");
    private static final Pattern LICENSE_PHOTO_DATA_URL_PATTERN =
            Pattern.compile("^data:(image/[a-zA-Z0-9.+-]+);base64,([A-Za-z0-9+/=\\r\\n]+)$");
    private static final Set<String> SUPPORTED_LICENSE_PHOTO_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/bmp",
            "image/x-ms-bmp"
    );
    private static final int LICENSE_PHOTO_MAX_BYTES = 10 * 1024 * 1024;

    private final CarInfoMapper carInfoMapper;
    private final UserAccountMapper userAccountMapper;
    private final UserNoticePublishService userNoticePublishService;
    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initCarInfoSchema() {
        try {
            Integer tableCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1)
                    FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'car_info'
                    """, Integer.class);
            if (tableCount == null || tableCount <= 0) {
                return;
            }

            Integer columnCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1)
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'car_info'
                      AND COLUMN_NAME = 'submitter_phone'
                    """, Integer.class);
            if (columnCount == null || columnCount <= 0) {
                jdbcTemplate.execute("""
                        ALTER TABLE car_info
                        ADD COLUMN submitter_phone VARCHAR(32) NULL COMMENT '提交登记的用户手机号'
                        """);
            }
        } catch (Exception ex) {
            log.error("初始化 car_info.submitter_phone 字段失败", ex);
        }
    }

    @Override
    public ApiResponse<List<CarInfo>> getMyVehicles(String phone) {
        UserAccount user = loadUserOrThrow(phone);
        List<String> licensePlates = collectUserLicensePlates(user);
        if (licensePlates.isEmpty()) {
            return ApiResponse.success("成功", new ArrayList<>());
        }

        List<CarInfo> carList = list(new LambdaQueryWrapper<CarInfo>()
                .in(CarInfo::getLicensePlate, licensePlates)
                .eq(CarInfo::getAuditStatus, "passed"));
        carList.forEach(car -> car.setVin(hideVin(car.getVin())));
        return ApiResponse.success("成功", carList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<?> registerVehicle(VehicleRegisterRequest request, String phone) {
        String licensePlate = normalizeLicensePlate(request.getLicensePlate());
        String vehicleType = normalizeVehicleType(request.getVehicleType());
        String vin = normalizeVin(request.getVin());
        String licensePhoto = normalizeLicensePhoto(request.getLicensePhoto());

        ensureUserHasAvailableCarSlot(phone);
        if (exists(new LambdaQueryWrapper<CarInfo>().eq(CarInfo::getLicensePlate, licensePlate))) {
            throw BusinessException.badRequest("车牌号已存在");
        }
        if (exists(new LambdaQueryWrapper<CarInfo>().eq(CarInfo::getVin, vin))) {
            throw BusinessException.badRequest("车架号已存在");
        }

        CarInfo carInfo = new CarInfo();
        BeanUtils.copyProperties(request, carInfo);
        carInfo.setLicensePlate(licensePlate);
        carInfo.setVehicleType(vehicleType);
        carInfo.setVin(vin);
        carInfo.setLicensePhoto(licensePhoto);
        try {
            carInfo.setRegisterDate(LocalDate.parse(request.getRegistrationDate()));
        } catch (DateTimeParseException e) {
            throw BusinessException.badRequest("注册日期格式不正确");
        }
        carInfo.setAuditStatus("unaudited");
        carInfo.setSubmitterPhone(phone);
        carInfo.setCurrentPoints(12);
        try {
            save(carInfo);
        } catch (DuplicateKeyException ex) {
            String duplicateField = resolveDuplicateField(ex);
            if ("vin".equals(duplicateField)) {
                throw BusinessException.badRequest("车架号已存在");
            }
            if ("licensePlate".equals(duplicateField)) {
                throw BusinessException.badRequest("车牌号已存在");
            }
            if ("engineNumber".equals(duplicateField)) {
                throw BusinessException.badRequest("发动机号已存在");
            }
            throw BusinessException.badRequest("车辆信息重复，请检查车牌号/车架号是否已存在");
        }

        userNoticePublishService.publishVehicleRegisterSubmitted(phone, carInfo);
        return ApiResponse.success("车辆登记成功，等待审核", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<?> updateVehicle(String licensePlate, VehicleUpdateRequest request, String phone) {
        String normalizedLicense = normalizeLicensePlate(licensePlate);
        UserAccount user = loadUserOrThrow(phone);
        CarInfo carInfo = loadVehicleByLicenseOrThrow(normalizedLicense);
        ensureVehicleOperableByUser(user, carInfo, phone);

        boolean changed = false;
        changed |= applyVehicleName(carInfo, request.getVehicleName());
        changed |= applyTextField(request.getOwner(), "所有人", carInfo.getOwner(), carInfo::setOwner);
        changed |= applyVehicleType(carInfo, request.getVehicleType());
        changed |= applyTextField(request.getUsageNature(), "使用性质", carInfo.getUsageNature(), carInfo::setUsageNature);
        changed |= applyTextField(request.getBrandModel(), "品牌型号", carInfo.getBrandModel(), carInfo::setBrandModel);
        changed |= applyVin(carInfo, request.getVin());
        changed |= applyTextField(request.getEngineNumber(), "发动机号", carInfo.getEngineNumber(), carInfo::setEngineNumber);
        changed |= applyRegisterDate(carInfo, request.getRegistrationDate());
        changed |= applyLicensePhoto(carInfo, request.getLicensePhoto());

        if (!changed) {
            return ApiResponse.success("未检测到变更", null);
        }

        carInfo.setAuditStatus("unaudited");
        carInfo.setRejectReason(null);
        carInfo.setUpdateTime(LocalDateTime.now());
        try {
            carInfoMapper.updateById(carInfo);
        } catch (DuplicateKeyException ex) {
            String duplicateField = resolveDuplicateField(ex);
            if ("vin".equals(duplicateField)) {
                throw BusinessException.badRequest("车架号已存在");
            }
            if ("licensePlate".equals(duplicateField)) {
                throw BusinessException.badRequest("车牌号已存在");
            }
            if ("engineNumber".equals(duplicateField)) {
                throw BusinessException.badRequest("发动机号已存在");
            }
            throw BusinessException.badRequest("车辆信息重复，请检查车牌号/车架号是否已存在");
        }
        return ApiResponse.success("车辆信息更新成功，等待重新审核", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<?> deleteVehicle(String licensePlate, String phone) {
        String normalizedLicense = normalizeLicensePlate(licensePlate);
        UserAccount user = loadUserOrThrow(phone);
        CarInfo carInfo = loadVehicleByLicenseOrThrow(normalizedLicense);
        ensureVehicleOperableByUser(user, carInfo, phone);

        if (isVehicleBoundToUser(user, normalizedLicense)) {
            unbindCarFromUser(user, normalizedLicense);
            persistUserCarBindings(user);
        }

        carInfoMapper.delete(new LambdaQueryWrapper<CarInfo>()
                .eq(CarInfo::getLicensePlate, normalizedLicense));
        return ApiResponse.success("车辆删除成功", null);
    }

    private void persistUserCarBindings(UserAccount user) {
        userAccountMapper.update(null, new LambdaUpdateWrapper<UserAccount>()
                .eq(UserAccount::getPhone, user.getPhone())
                .set(UserAccount::getCar1License, user.getCar1License())
                .set(UserAccount::getCar2License, user.getCar2License())
                .set(UserAccount::getCar3License, user.getCar3License()));
    }

    private boolean applyVehicleName(CarInfo carInfo, String rawVehicleName) {
        if (rawVehicleName == null) {
            return false;
        }
        String vehicleName = rawVehicleName.trim();
        if (vehicleName.isEmpty()) {
            throw BusinessException.badRequest("车辆名称不能为空");
        }
        if (!VEHICLE_NAME_PATTERN.matcher(vehicleName).matches()) {
            throw BusinessException.badRequest("车辆名称必须为1-6位中英文或数字");
        }
        if (vehicleName.equals(carInfo.getVehicleName())) {
            return false;
        }
        carInfo.setVehicleName(vehicleName);
        return true;
    }

    private boolean applyVehicleType(CarInfo carInfo, String rawVehicleType) {
        if (rawVehicleType == null) {
            return false;
        }
        String vehicleType = normalizeVehicleType(rawVehicleType);
        if (vehicleType.equals(carInfo.getVehicleType())) {
            return false;
        }
        carInfo.setVehicleType(vehicleType);
        return true;
    }

    private boolean applyVin(CarInfo carInfo, String rawVin) {
        if (rawVin == null) {
            return false;
        }
        String vin = normalizeVin(rawVin);
        if (vin.equals(carInfo.getVin())) {
            return false;
        }
        boolean duplicated = exists(new LambdaQueryWrapper<CarInfo>()
                .eq(CarInfo::getVin, vin)
                .ne(carInfo.getId() != null, CarInfo::getId, carInfo.getId()));
        if (duplicated) {
            throw BusinessException.badRequest("车架号已存在");
        }
        carInfo.setVin(vin);
        return true;
    }

    private boolean applyRegisterDate(CarInfo carInfo, String rawRegistrationDate) {
        if (rawRegistrationDate == null) {
            return false;
        }
        String value = rawRegistrationDate.trim();
        if (value.isEmpty()) {
            throw BusinessException.badRequest("注册日期不能为空");
        }
        LocalDate registerDate;
        try {
            registerDate = LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw BusinessException.badRequest("注册日期格式不正确");
        }
        if (registerDate.equals(carInfo.getRegisterDate())) {
            return false;
        }
        carInfo.setRegisterDate(registerDate);
        return true;
    }

    private boolean applyLicensePhoto(CarInfo carInfo, String rawLicensePhoto) {
        if (rawLicensePhoto == null) {
            return false;
        }
        String licensePhoto = normalizeLicensePhoto(rawLicensePhoto);
        if (licensePhoto.equals(carInfo.getLicensePhoto())) {
            return false;
        }
        carInfo.setLicensePhoto(licensePhoto);
        return true;
    }

    private boolean applyTextField(String rawValue, String fieldLabel, String currentValue, Consumer<String> setter) {
        if (rawValue == null) {
            return false;
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            throw BusinessException.badRequest(fieldLabel + "不能为空");
        }
        if (value.equals(currentValue)) {
            return false;
        }
        setter.accept(value);
        return true;
    }

    private String hideVin(String vin) {
        if (vin == null || vin.length() != 17) {
            return vin;
        }
        return vin.substring(0, 5) + "******" + vin.substring(11);
    }

    private void bindCarToUser(String phone, String licensePlate) {
        UserAccount user = loadUserOrThrow(phone);
        if (!hasText(user.getCar1License())) {
            user.setCar1License(licensePlate);
        } else if (!hasText(user.getCar2License())) {
            user.setCar2License(licensePlate);
        } else if (!hasText(user.getCar3License())) {
            user.setCar3License(licensePlate);
        } else {
            throw BusinessException.badRequest("每个账号最多绑定3辆车");
        }
        userAccountMapper.updateById(user);
    }

    private void ensureUserHasAvailableCarSlot(String phone) {
        UserAccount user = loadUserOrThrow(phone);
        if (hasText(user.getCar1License())
                && hasText(user.getCar2License())
                && hasText(user.getCar3License())) {
            throw BusinessException.badRequest("每个账号最多绑定3辆车，请先删除一辆已绑定车辆");
        }
    }

    private void unbindCarFromUser(UserAccount user, String licensePlate) {
        List<String> remain = new ArrayList<>();
        boolean removed = false;

        if (hasText(user.getCar1License())) {
            if (sameLicense(user.getCar1License(), licensePlate)) {
                removed = true;
            } else {
                remain.add(user.getCar1License().trim());
            }
        }
        if (hasText(user.getCar2License())) {
            if (sameLicense(user.getCar2License(), licensePlate)) {
                removed = true;
            } else {
                remain.add(user.getCar2License().trim());
            }
        }
        if (hasText(user.getCar3License())) {
            if (sameLicense(user.getCar3License(), licensePlate)) {
                removed = true;
            } else {
                remain.add(user.getCar3License().trim());
            }
        }

        if (!removed) {
            throw BusinessException.notFound("未找到车辆");
        }

        user.setCar1License(remain.size() > 0 ? remain.get(0) : null);
        user.setCar2License(remain.size() > 1 ? remain.get(1) : null);
        user.setCar3License(remain.size() > 2 ? remain.get(2) : null);
    }

    private List<String> collectUserLicensePlates(UserAccount user) {
        List<String> licensePlates = new ArrayList<>();
        if (hasText(user.getCar1License())) {
            licensePlates.add(user.getCar1License().trim());
        }
        if (hasText(user.getCar2License())) {
            licensePlates.add(user.getCar2License().trim());
        }
        if (hasText(user.getCar3License())) {
            licensePlates.add(user.getCar3License().trim());
        }
        return licensePlates;
    }

    private void ensureVehicleBoundToUser(UserAccount user, String licensePlate) {
        if (user == null || !isVehicleBoundToUser(user, licensePlate)) {
            throw BusinessException.notFound("未找到车辆");
        }
    }

    private void ensureVehicleOperableByUser(UserAccount user, CarInfo carInfo, String phone) {
        if (user == null || carInfo == null) {
            throw BusinessException.notFound("未找到车辆");
        }
        boolean boundToUser = isVehicleBoundToUser(user, carInfo.getLicensePlate());
        boolean submittedByUser = samePhone(carInfo.getSubmitterPhone(), phone);
        if (!boundToUser && !submittedByUser) {
            throw BusinessException.notFound("未找到车辆");
        }
    }

    private boolean isVehicleBoundToUser(UserAccount user, String licensePlate) {
        return sameLicense(user.getCar1License(), licensePlate)
                || sameLicense(user.getCar2License(), licensePlate)
                || sameLicense(user.getCar3License(), licensePlate);
    }

    private CarInfo loadVehicleByLicenseOrThrow(String licensePlate) {
        CarInfo carInfo = carInfoMapper.selectOne(new LambdaQueryWrapper<CarInfo>()
                .eq(CarInfo::getLicensePlate, licensePlate)
                .last("LIMIT 1"));
        if (carInfo == null) {
            throw BusinessException.notFound("未找到车辆");
        }
        return carInfo;
    }

    private UserAccount loadUserOrThrow(String phone) {
        UserAccount user = userAccountMapper.selectById(phone);
        if (user == null) {
            throw BusinessException.notFound("未找到用户");
        }
        return user;
    }

    private String normalizeLicensePlate(String licensePlate) {
        if (!hasText(licensePlate)) {
            throw BusinessException.badRequest("车牌号不能为空");
        }
        return licensePlate.trim();
    }

    private String normalizeVehicleType(String vehicleType) {
        if (!hasText(vehicleType)) {
            throw BusinessException.badRequest("车辆类型不能为空");
        }
        String value = vehicleType.trim();
        if (!value.matches("^(1|2)$")) {
            throw BusinessException.badRequest("车辆类型必须为1或2");
        }
        return value;
    }

    private String normalizeVin(String rawVin) {
        if (!hasText(rawVin)) {
            throw BusinessException.badRequest("车架号不能为空");
        }
        String vin = rawVin.trim();
        if (!vin.matches("^[A-Z0-9]{17}$")) {
            throw BusinessException.badRequest("车架号必须为17位大写字母或数字");
        }
        return vin;
    }

    private String normalizeLicensePhoto(String rawLicensePhoto) {
        if (!hasText(rawLicensePhoto)) {
            throw BusinessException.badRequest("行驶证照片不能为空");
        }
        String value = rawLicensePhoto.trim();
        Matcher matcher = LICENSE_PHOTO_DATA_URL_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw BusinessException.badRequest("行驶证照片必须是Base64 Data URL图片");
        }

        String mimeType = matcher.group(1).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_LICENSE_PHOTO_TYPES.contains(mimeType)) {
            throw BusinessException.badRequest("行驶证照片图片类型不支持");
        }

        String base64Data = matcher.group(2).replaceAll("\\s+", "");
        if (base64Data.isEmpty()) {
            throw BusinessException.badRequest("行驶证照片数据不能为空");
        }

        byte[] decodedBytes;
        try {
            decodedBytes = Base64.getDecoder().decode(base64Data);
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("行驶证照片Base64数据不合法");
        }
        if (decodedBytes.length > LICENSE_PHOTO_MAX_BYTES) {
            throw BusinessException.badRequest("行驶证原图大小不能超过10MB");
        }
        return "data:" + mimeType + ";base64," + base64Data;
    }

    private boolean sameLicense(String left, String right) {
        if (!hasText(left) || !hasText(right)) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private boolean samePhone(String left, String right) {
        if (!hasText(left) || !hasText(right)) {
            return false;
        }
        return left.trim().equals(right.trim());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String resolveDuplicateField(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("uk_vin")
                        || lower.contains("car_info.uk_vin")
                        || (lower.contains("duplicate entry") && lower.contains("vin"))) {
                    return "vin";
                }
                if (lower.contains("uk_license")
                        || lower.contains("uk_license_plate")
                        || lower.contains("license_plate")
                        || (lower.contains("duplicate entry") && lower.contains("plate"))) {
                    return "licensePlate";
                }
                if (lower.contains("uk_engine")
                        || lower.contains("engine_number")
                        || (lower.contains("duplicate entry") && lower.contains("engine"))) {
                    return "engineNumber";
                }
            }
            current = current.getCause();
        }
        return "unknown";
    }
}
