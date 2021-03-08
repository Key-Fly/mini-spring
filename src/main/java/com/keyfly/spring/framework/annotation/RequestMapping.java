package com.keyfly.spring.framework.annotation;

import java.lang.annotation.*;

/**
 * @author keyfly
 * @since 2021/3/6 23:13
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestMapping {
    String value() default "";
}
