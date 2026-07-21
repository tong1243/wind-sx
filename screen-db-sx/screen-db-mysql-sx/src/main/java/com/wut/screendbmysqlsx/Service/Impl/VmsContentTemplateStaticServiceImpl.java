package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Mapper.VmsContentTemplateStaticMapper;
import com.wut.screendbmysqlsx.Model.VmsContentTemplateStatic;
import com.wut.screendbmysqlsx.Service.VmsContentTemplateStaticService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 可变信息发布内容模板静态表服务实现。
 */
@Service
public class VmsContentTemplateStaticServiceImpl extends ServiceImpl<VmsContentTemplateStaticMapper, VmsContentTemplateStatic>
        implements VmsContentTemplateStaticService {
    private static final Pattern PASSENGER_SPEED_PATTERN = Pattern.compile(
            "((?:小客车|小型车|轻型车)[^，。；;、\\d{]*?限速\\s*)(\\d+)(\\s*(?:km/h|公里/小时|公里每小时)?)"
    );
    private static final Pattern FREIGHT_SPEED_PATTERN = Pattern.compile(
            "((?:客货车|大型车|货车|重型车|危化品车)[^，。；;、\\d{]*?限速\\s*)(\\d+)(\\s*(?:km/h|公里/小时|公里每小时)?)"
    );
    private static final Pattern GENERIC_SPEED_PATTERN = Pattern.compile(
            "(限速\\s*)(\\d+)(\\s*(?:km/h|公里/小时|公里每小时)?)"
    );

    private final VmsContentTemplateStaticMapper mapper;

    public VmsContentTemplateStaticServiceImpl(VmsContentTemplateStaticMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<VmsContentTemplateStatic> listByControlLevel(String controlLevel, Integer isEnabled) {
        LambdaQueryWrapper<VmsContentTemplateStatic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VmsContentTemplateStatic::getControlLevel, controlLevel);
        if (isEnabled != null) {
            wrapper.eq(VmsContentTemplateStatic::getIsEnabled, isEnabled);
        }
        wrapper.orderByAsc(VmsContentTemplateStatic::getSortNo)
                .orderByAsc(VmsContentTemplateStatic::getTemplateCode)
                .orderByAsc(VmsContentTemplateStatic::getId);
        return mapper.selectList(wrapper);
    }

    @Override
    public VmsContentTemplateStatic getByTemplateCode(String templateCode) {
        LambdaQueryWrapper<VmsContentTemplateStatic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VmsContentTemplateStatic::getTemplateCode, templateCode).last("LIMIT 1");
        return mapper.selectOne(wrapper);
    }

    @Override
    public VmsContentTemplateStatic matchTemplate(String controlLevel, String publishPosition, String vehicleType) {
        LambdaQueryWrapper<VmsContentTemplateStatic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VmsContentTemplateStatic::getControlLevel, controlLevel)
                .eq(VmsContentTemplateStatic::getPublishPosition, publishPosition)
                .eq(VmsContentTemplateStatic::getVehicleType, vehicleType)
                .eq(VmsContentTemplateStatic::getIsEnabled, 1)
                .orderByAsc(VmsContentTemplateStatic::getSortNo)
                .orderByAsc(VmsContentTemplateStatic::getId)
                .last("LIMIT 1");
        return mapper.selectOne(wrapper);
    }

    @Override
    public int syncSpeedLimitsByControlLevel(String controlLevel, Integer passengerSpeedLimit, Integer freightSpeedLimit) {
        if ((passengerSpeedLimit == null && freightSpeedLimit == null)
                || controlLevel == null
                || controlLevel.isBlank()) {
            return 0;
        }

        LambdaQueryWrapper<VmsContentTemplateStatic> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(VmsContentTemplateStatic::getControlLevel, levelNameCandidates(controlLevel));
        List<VmsContentTemplateStatic> rows = mapper.selectList(wrapper);

        int updated = 0;
        for (VmsContentTemplateStatic row : rows) {
            String oldText = row.getTemplateText();
            String newText = syncTemplateSpeedText(oldText, passengerSpeedLimit, freightSpeedLimit);
            if (oldText == null ? newText == null : oldText.equals(newText)) {
                continue;
            }
            row.setTemplateText(newText);
            row.setUpdateTime(LocalDateTime.now());
            updated += mapper.updateById(row) > 0 ? 1 : 0;
        }
        return updated;
    }

    @Override
    public boolean removeByTemplateCode(String templateCode) {
        LambdaQueryWrapper<VmsContentTemplateStatic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VmsContentTemplateStatic::getTemplateCode, templateCode);
        return mapper.delete(wrapper) > 0;
    }

    private String syncTemplateSpeedText(String text, Integer passengerSpeedLimit, Integer freightSpeedLimit) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String result = text;
        boolean changedVehicleSpecific = false;
        if (passengerSpeedLimit != null) {
            String next = replaceSpeed(PASSENGER_SPEED_PATTERN, result, passengerSpeedLimit);
            changedVehicleSpecific = changedVehicleSpecific || !next.equals(result);
            result = next;
        }
        if (freightSpeedLimit != null) {
            String next = replaceSpeed(FREIGHT_SPEED_PATTERN, result, freightSpeedLimit);
            changedVehicleSpecific = changedVehicleSpecific || !next.equals(result);
            result = next;
        }
        if (!changedVehicleSpecific) {
            Integer genericLimit = passengerSpeedLimit == null ? freightSpeedLimit : passengerSpeedLimit;
            if (genericLimit != null) {
                result = replaceSpeed(GENERIC_SPEED_PATTERN, result, genericLimit);
            }
        }
        return result;
    }

    private String replaceSpeed(Pattern pattern, String text, int speedLimit) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + speedLimit + matcher.group(3)
            ));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private List<String> levelNameCandidates(String controlLevelName) {
        String name = controlLevelName == null ? "" : controlLevelName.trim();
        if (name.contains("一") || name.contains("红")) {
            return List.of("一级管控", "一级", "红色警戒");
        }
        if (name.contains("二") || name.contains("橙")) {
            return List.of("二级管控", "二级", "橙色警戒");
        }
        if (name.contains("三") || name.contains("黄")) {
            return List.of("三级管控", "三级", "黄色警戒");
        }
        if (name.contains("四") || name.contains("蓝")) {
            return List.of("四级管控", "四级", "蓝色警戒");
        }
        if (name.contains("五") || name.contains("绿") || name.contains("正常")) {
            return List.of("五级管控", "五级", "绿色警戒", "正常通行");
        }
        return List.of(name);
    }
}
