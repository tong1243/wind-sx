package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Mapper.LanelineMapper;
import com.wut.screendbmysqlsx.Model.Laneline;
import com.wut.screendbmysqlsx.Service.LanelineService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class LanelineServiceImpl extends ServiceImpl<LanelineMapper, Laneline>implements LanelineService {

    private final LanelineMapper lanelineMapper;

    public LanelineServiceImpl(LanelineMapper lanelineMapper) {
        this.lanelineMapper = lanelineMapper;
    }

    @Override
    public List<Laneline> getListByFrenetXAndLane(double frenetX, int lane) {
        // 创建 LambdaQueryWrapper 对象
        LambdaQueryWrapper<Laneline> queryWrapper = new LambdaQueryWrapper<>();
        // 添加查询条件，frenetX >= frenetX - 150 且 frenetX <= frenetX
        queryWrapper.ge(Laneline::getFrenetX, frenetX - 150);
        queryWrapper.le(Laneline::getFrenetX, frenetX -50);
        // 添加查询条件，lane 等于传入的 lane 值
        queryWrapper.eq(Laneline::getLane, lane);
        // 执行查询并返回结果
        List<Laneline> lanelineList = lanelineMapper.selectList(queryWrapper);

        // 筛选出 frenetX 间隔为 5 米的数据点
        List<Laneline> filteredList = new ArrayList<>();
        if (!lanelineList.isEmpty()) {
            // 对列表按 frenetX 排序（假设查询结果已经按顺序返回，但为了安全起见还是排序一下）
            lanelineList.sort(Comparator.comparingDouble(Laneline::getFrenetX));

            // 获取第一个数据点作为起始点
            double prevFrenetX = lanelineList.get(0).getFrenetX();
            filteredList.add(lanelineList.get(0));

            // 遍历剩余数据点，筛选出间隔大于等于 5 米的点
            for (Laneline laneline : lanelineList) {
                double currentFrenetX = laneline.getFrenetX();
                if (currentFrenetX - prevFrenetX >= 5) {
                    filteredList.add(laneline);
                    prevFrenetX = currentFrenetX;
                }
            }
        }

        return filteredList;
    }

}
