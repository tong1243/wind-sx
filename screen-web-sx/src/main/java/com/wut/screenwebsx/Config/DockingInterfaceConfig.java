package com.wut.screenwebsx.Config;

import org.springframework.context.annotation.Configuration;

import java.lang.annotation.*;

// 在Service中标记有Docking注解的方法为直接连接Controller的方法
@Configuration
public class DockingInterfaceConfig {
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    @Inherited
    @Documented
    public @interface Docking {
        String value() default "";
    }
}
