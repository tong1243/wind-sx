package com.wut.screenwebsx.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wut.screendbmysqlsx.Model.WindData;
import com.wut.screendbmysqlsx.Service.WindDataService;
import com.wut.screenwebsx.Mapper.WindRiskSectionHourlyMapper;
import com.wut.screenwebsx.Model.WindRiskSectionHourly;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * 按 wind_data 时间戳增量计算风险区段和限速结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WindRiskSpeedScheduleService {
    private final WindDataService windDataService;
    private final WindRiskSpeedService windRiskSpeedService;
    private final WindRiskSectionHourlyMapper windRiskSectionHourlyMapper;

    @Value("${wind.risk-speed.schedule.enabled:true}")
    private boolean enabled;

    @Value("${wind.risk-speed.schedule.cron:0 * * * * *}")
    private String cronExpr;

    @Value("${wind.risk-speed.schedule.input-data-source:}")
    private String inputDataSource;

    @Value("${wind.risk-speed.schedule.output-data-source:MATLAB_RULE_V1}")
    private String outputDataSource;

    @Value("${wind.risk-speed.schedule.recompute-hours:0}")
    private int recomputeHours;

    @Scheduled(cron = "${wind.risk-speed.schedule.cron:0 * * * * *}")
    public void updateByWindDataTimestamp() {
        if (!enabled) {
            return;
        }

        String finalOutputSource = hasText(outputDataSource) ? outputDataSource.trim() : "MATLAB_RULE_V1";
        LocalDateTime firstSourceHour = findFirstSourceHour();
        LocalDateTime lastSourceHour = findLastSourceHour();
        if (firstSourceHour == null || lastSourceHour == null) {
            log.debug("wind-risk-speed schedule skipped: no source rows, cron={}", cronExpr);
            return;
        }

        LocalDateTime latestCalculatedHour = findLatestCalculatedHour(finalOutputSource);
        LocalDateTime startHour = latestCalculatedHour == null ? firstSourceHour : latestCalculatedHour.plusHours(1);
        if (recomputeHours > 0) {
            long rewind = Math.max(1, recomputeHours);
            LocalDateTime rewindStart = lastSourceHour.minusHours(rewind - 1);
            if (rewindStart.isBefore(firstSourceHour)) {
                rewindStart = firstSourceHour;
            }
            if (startHour.isAfter(rewindStart)) {
                startHour = rewindStart;
            }
        }
        if (startHour.isBefore(firstSourceHour)) {
            startHour = firstSourceHour;
        }
        if (startHour.isAfter(lastSourceHour)) {
            log.debug("wind-risk-speed schedule skipped: no new hour, latestCalculated={}, latestSource={}, cron={}",
                    latestCalculatedHour, lastSourceHour, cronExpr);
            return;
        }

        long startTs = toEpochMilli(startHour);
        long endTs = toEpochMilli(lastSourceHour);
        String finalInputSource = hasText(inputDataSource) ? inputDataSource.trim() : null;

        Map<String, Object> result = windRiskSpeedService.calculateAndPersist(
                startTs,
                endTs,
                finalInputSource,
                finalOutputSource
        );
        log.info("wind-risk-speed schedule done: cron={}, result={}", cronExpr, result);
    }

    private LocalDateTime findFirstSourceHour() {
        LambdaQueryWrapper<WindData> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(WindData::getTimeStamp)
                .isNotNull(WindData::getTimeStamp)
                .orderByAsc(WindData::getTimeStamp)
                .last("limit 1");
        applyInputSourceFilter(wrapper);
        WindData row = windDataService.getOne(wrapper, false);
        return row == null ? null : truncateToHour(row.getTimeStamp());
    }

    private LocalDateTime findLastSourceHour() {
        LambdaQueryWrapper<WindData> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(WindData::getTimeStamp)
                .isNotNull(WindData::getTimeStamp)
                .orderByDesc(WindData::getTimeStamp)
                .last("limit 1");
        applyInputSourceFilter(wrapper);
        WindData row = windDataService.getOne(wrapper, false);
        return row == null ? null : truncateToHour(row.getTimeStamp());
    }

    private LocalDateTime findLatestCalculatedHour(String finalOutputSource) {
        LambdaQueryWrapper<WindRiskSectionHourly> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(WindRiskSectionHourly::getTimeStamp)
                .eq(WindRiskSectionHourly::getDataSource, finalOutputSource)
                .orderByDesc(WindRiskSectionHourly::getTimeStamp)
                .last("limit 1");
        WindRiskSectionHourly row = windRiskSectionHourlyMapper.selectOne(wrapper);
        return row == null ? null : truncateToHour(row.getTimeStamp());
    }

    private void applyInputSourceFilter(LambdaQueryWrapper<WindData> wrapper) {
        if (!hasText(inputDataSource)) {
            return;
        }
        String filter = inputDataSource.trim();
        if (filter.endsWith("*")) {
            String prefix = filter.substring(0, filter.length() - 1);
            if (hasText(prefix)) {
                wrapper.likeRight(WindData::getDataSource, prefix);
            }
            return;
        }
        wrapper.eq(WindData::getDataSource, filter);
    }

    private LocalDateTime truncateToHour(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.withMinute(0).withSecond(0).withNano(0);
    }

    private long toEpochMilli(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
