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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class CarInfoServiceImpl extends ServiceImpl<CarInfoMapper, CarInfo> implements CarInfoService {
    private final CarInfoMapper carInfoMapper;
    private final UserAccountMapper userAccountMapper;

    @Override
    public ApiResponse<List<CarInfo>> getMyVehicles(String phone) {
        UserAccount user = loadUserOrThrow(phone);
        List<String> licensePlates = collectUserLicensePlates(user);
        if (licensePlates.isEmpty()) {
            return ApiResponse.success("ok", new ArrayList<>());
        }

        List<CarInfo> carList = list(new LambdaQueryWrapper<CarInfo>()
                .in(CarInfo::getLicensePlate, licensePlates));
        carList.forEach(car -> car.setVin(hideVin(car.getVin())));
        return ApiResponse.success("ok", carList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<?> registerVehicle(VehicleRegisterRequest request, String phone) {
        String licensePlate = normalizeLicensePlate(request.getLicensePlate());

        if (exists(new LambdaQueryWrapper<CarInfo>().eq(CarInfo::getLicensePlate, licensePlate))) {
            throw BusinessException.badRequest("license plate already exists");
        }
        if (request.getVin().length() != 17) {
            throw BusinessException.badRequest("vin must be 17 chars");
        }

        CarInfo carInfo = new CarInfo();
        BeanUtils.copyProperties(request, carInfo);
        carInfo.setLicensePlate(licensePlate);
        try {
            carInfo.setRegisterDate(LocalDate.parse(request.getRegistrationDate()));
        } catch (DateTimeParseException e) {
            throw BusinessException.badRequest("invalid registrationDate");
        }
        carInfo.setAuditStatus("unaudited");
        carInfo.setCurrentPoints(12);
        save(carInfo);

        bindCarToUser(phone, licensePlate);
        return ApiResponse.success("vehicle registered, pending audit", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<?> updateVehicle(String licensePlate, VehicleUpdateRequest request, String phone) {
        String normalizedLicense = normalizeLicensePlate(licensePlate);
        UserAccount user = loadUserOrThrow(phone);
        ensureVehicleBoundToUser(user, normalizedLicense);
        CarInfo carInfo = loadVehicleByLicenseOrThrow(normalizedLicense);

        boolean changed = false;
        changed |= applyVehicleName(carInfo, request.getVehicleName());
        changed |= applyTextField(request.getOwner(), "owner", carInfo.getOwner(), carInfo::setOwner);
        changed |= applyTextField(request.getVehicleType(), "vehicleType", carInfo.getVehicleType(), carInfo::setVehicleType);
        changed |= applyTextField(request.getUsageNature(), "usageNature", carInfo.getUsageNature(), carInfo::setUsageNature);
        changed |= applyTextField(request.getBrandModel(), "brandModel", carInfo.getBrandModel(), carInfo::setBrandModel);
        changed |= applyVin(carInfo, request.getVin());
        changed |= applyTextField(request.getEngineNumber(), "engineNumber", carInfo.getEngineNumber(), carInfo::setEngineNumber);
        changed |= applyRegisterDate(carInfo, request.getRegistrationDate());
        changed |= applyTextField(request.getLicensePhoto(), "licensePhoto", carInfo.getLicensePhoto(), carInfo::setLicensePhoto);

        if (!changed) {
            return ApiResponse.success("no changes detected", null);
        }

        carInfo.setAuditStatus("unaudited");
        carInfo.setRejectReason(null);
        carInfo.setUpdateTime(LocalDateTime.now());
        carInfoMapper.updateById(carInfo);
        return ApiResponse.success("vehicle updated, pending re-audit", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<?> deleteVehicle(String licensePlate, String phone) {
        String normalizedLicense = normalizeLicensePlate(licensePlate);
        UserAccount user = loadUserOrThrow(phone);
        ensureVehicleBoundToUser(user, normalizedLicense);
        loadVehicleByLicenseOrThrow(normalizedLicense);

        unbindCarFromUser(user, normalizedLicense);
        persistUserCarBindings(user);

        carInfoMapper.delete(new LambdaQueryWrapper<CarInfo>()
                .eq(CarInfo::getLicensePlate, normalizedLicense));
        return ApiResponse.success("vehicle deleted", null);
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
            throw BusinessException.badRequest("vehicleName cannot be blank");
        }
        if (vehicleName.length() > 6 || vehicleName.chars().anyMatch(Character::isWhitespace)) {
            throw BusinessException.badRequest("vehicleName must be 1-6 chars without whitespace");
        }
        if (vehicleName.equals(carInfo.getVehicleName())) {
            return false;
        }
        carInfo.setVehicleName(vehicleName);
        return true;
    }

    private boolean applyVin(CarInfo carInfo, String rawVin) {
        if (rawVin == null) {
            return false;
        }
        String vin = rawVin.trim();
        if (!vin.matches("^[A-Z0-9]{17}$")) {
            throw BusinessException.badRequest("vin must be 17 uppercase alphanumeric chars");
        }
        if (vin.equals(carInfo.getVin())) {
            return false;
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
            throw BusinessException.badRequest("registrationDate cannot be blank");
        }
        LocalDate registerDate;
        try {
            registerDate = LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw BusinessException.badRequest("invalid registrationDate");
        }
        if (registerDate.equals(carInfo.getRegisterDate())) {
            return false;
        }
        carInfo.setRegisterDate(registerDate);
        return true;
    }

    private boolean applyTextField(String rawValue, String fieldLabel, String currentValue, Consumer<String> setter) {
        if (rawValue == null) {
            return false;
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            throw BusinessException.badRequest(fieldLabel + " cannot be blank");
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
            throw BusinessException.badRequest("max 3 vehicles per account");
        }
        userAccountMapper.updateById(user);
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
            throw BusinessException.notFound("vehicle not found");
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
            throw BusinessException.notFound("vehicle not found");
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
            throw BusinessException.notFound("vehicle not found");
        }
        return carInfo;
    }

    private UserAccount loadUserOrThrow(String phone) {
        UserAccount user = userAccountMapper.selectById(phone);
        if (user == null) {
            throw BusinessException.notFound("user not found");
        }
        return user;
    }

    private String normalizeLicensePlate(String licensePlate) {
        if (!hasText(licensePlate)) {
            throw BusinessException.badRequest("licensePlate cannot be blank");
        }
        return licensePlate.trim();
    }

    private boolean sameLicense(String left, String right) {
        if (!hasText(left) || !hasText(right)) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
