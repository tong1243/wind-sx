package com.wut.screendbmysqlsx.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wut.screendbmysqlsx.Model.WindDetectionEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 4.1 事件检测持久化 Mapper。
 */
public interface WindDetectionEventMapper extends BaseMapper<WindDetectionEvent> {
}
