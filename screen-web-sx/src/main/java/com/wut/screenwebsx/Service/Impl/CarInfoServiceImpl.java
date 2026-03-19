package com.wut.screenwebsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screencommonsx.Exception.BusinessException;
import com.wut.screencommonsx.Model.CarInfo;
import com.wut.screencommonsx.Model.UserAccount;

import com.wut.screencommonsx.Request.VehicleRegisterRequest;
import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screenwebsx.Service.CarInfoService;
import com.wut.screenwebsx.Mapper.CarInfoMapper;
import com.wut.screenwebsx.Mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CarInfoServiceImpl extends ServiceImpl<CarInfoMapper, CarInfo> implements CarInfoService {
    private final CarInfoMapper carInfoMapper;
    private final UserAccountMapper userAccountMapper;

    @Override
    public ApiResponse<List<CarInfo>> getMyVehicles(String phone) {
        UserAccount user = userAccountMapper.selectById(phone);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }

        // 获取用户绑定的所有车牌号
        List<String> licensePlates = new java.util.ArrayList<>();
        if (user.getCar1License() != null) {
            licensePlates.add(user.getCar1License());
        }
        if (user.getCar2License() != null) {
            licensePlates.add(user.getCar2License());
        }
        if (user.getCar3License() != null) {
            licensePlates.add(user.getCar3License());
        }

        // 如果没有绑定的车辆，返回空列表
        if (licensePlates.isEmpty()) {
            return ApiResponse.success("获取成功", new java.util.ArrayList<>());
        }

        // 查询绑定的车辆信息
        List<CarInfo> carList = list(new LambdaQueryWrapper<CarInfo>()
                .in(CarInfo::getLicensePlate, licensePlates));

        // 隐藏 VIN 码中间 6 位
        carList.forEach(car -> car.setVin(hideVin(car.getVin())));
        return ApiResponse.success("获取成功", carList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<?> registerVehicle(VehicleRegisterRequest request, String phone) {
        // 验证车牌号是否已存在
        if (exists(new LambdaQueryWrapper<CarInfo>().eq(CarInfo::getLicensePlate, request.getLicensePlate()))) {
            throw BusinessException.badRequest("车牌号已被登记");
        }
        // 验证VIN码（17位）
        if (request.getVin().length() != 17) {
            throw BusinessException.badRequest("VIN码必须为17位");
        }
        // 构建车辆信息
        CarInfo carInfo = new CarInfo();
        BeanUtils.copyProperties(request, carInfo);
        try {
            carInfo.setRegisterDate(LocalDate.parse(request.getRegistrationDate()));
        } catch (DateTimeParseException e) {
            throw BusinessException.badRequest("注册日期格式无效");
        }
        carInfo.setAuditStatus("unaudited"); // 初始未审核
        carInfo.setCurrentPoints(12); // 初始12分
        save(carInfo);
        // 绑定到用户账号（最多3辆）
        bindCarToUser(phone, request.getLicensePlate());
        return ApiResponse.success("车辆登记成功，等待审核", null);
    }

    // 隐藏VIN码中间6位
    private String hideVin(String vin) {
        if (vin == null || vin.length() != 17) return vin;
        return vin.substring(0,5) + "******" + vin.substring(11);
    }

    // 绑定车辆到用户（按car1/car2/car3顺序）
    private void bindCarToUser(String phone, String licensePlate) {
        UserAccount user = userAccountMapper.selectById(phone);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (user.getCar1License() == null) {
            user.setCar1License(licensePlate);
        } else if (user.getCar2License() == null) {
            user.setCar2License(licensePlate);
        } else if (user.getCar3License() == null) {
            user.setCar3License(licensePlate);
        } else {
            throw BusinessException.badRequest("单个账号最多绑定3辆车辆");
        }
        userAccountMapper.updateById(user);
    }
}
