package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Mapper.WindControlKvMapper;
import com.wut.screendbmysqlsx.Model.WindControlKv;
import com.wut.screendbmysqlsx.Service.WindControlKvService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
/**
 * KV 快照服务实现。
 */
public class WindControlKvServiceImpl extends ServiceImpl<WindControlKvMapper, WindControlKv> implements WindControlKvService {
    private final WindControlKvMapper windControlKvMapper;

    public WindControlKvServiceImpl(WindControlKvMapper windControlKvMapper) {
        this.windControlKvMapper = windControlKvMapper;
    }

    @Override
    public List<WindControlKv> getByCategory(String category) {
        LambdaQueryWrapper<WindControlKv> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WindControlKv::getCategory, category).orderByAsc(WindControlKv::getItemKey);
        return windControlKvMapper.selectList(wrapper);
    }

    @Override
    public WindControlKv getByCategoryAndKey(String category, String itemKey) {
        LambdaQueryWrapper<WindControlKv> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WindControlKv::getCategory, category).eq(WindControlKv::getItemKey, itemKey);
        return windControlKvMapper.selectOne(wrapper);
    }

    @Override
    public void upsert(String category, String itemKey, String contentJson, long updatedAt) {
        // 走“查存在再 insert/update”策略，兼容当前通用 MyBatis-Plus 配置。
        WindControlKv existing = getByCategoryAndKey(category, itemKey);
        if (existing == null) {
            WindControlKv insert = new WindControlKv();
            insert.setCategory(category);
            insert.setItemKey(itemKey);
            insert.setContentJson(contentJson);
            insert.setUpdatedAt(updatedAt);
            windControlKvMapper.insert(insert);
            return;
        }
        existing.setContentJson(contentJson);
        existing.setUpdatedAt(updatedAt);
        windControlKvMapper.updateById(existing);
    }

    @Override
    public boolean deleteByCategoryAndKey(String category, String itemKey) {
        LambdaQueryWrapper<WindControlKv> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WindControlKv::getCategory, category).eq(WindControlKv::getItemKey, itemKey);
        return windControlKvMapper.delete(wrapper) > 0;
    }
}
