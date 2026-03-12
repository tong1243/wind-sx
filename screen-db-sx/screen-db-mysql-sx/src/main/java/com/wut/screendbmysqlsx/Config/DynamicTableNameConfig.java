package com.wut.screendbmysqlsx.Config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.wut.screendbmysqlsx.Context.TableTimeContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.wut.screencommonsx.Static.DbModuleStatic.*;

@Configuration
public class DynamicTableNameConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor = new DynamicTableNameInnerInterceptor();
        dynamicTableNameInnerInterceptor.setTableNameHandler(
            (sql, tableName) -> {
                if (!DYNAMIC_TABLE_NAMES.contains(tableName)) { return tableName; }
                String timestamp = TableTimeContext.getTime(TABLE_SUFFIX_KEY);
                return tableName + TABLE_SUFFIX_SEPARATOR + timestamp;
            }
        );
        interceptor.addInnerInterceptor(dynamicTableNameInnerInterceptor);
        return interceptor;
    }

}
