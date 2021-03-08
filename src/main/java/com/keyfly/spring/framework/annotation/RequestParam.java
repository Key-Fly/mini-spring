package com.keyfly.spring.framework.annotation;

import java.lang.annotation.*;

/**
 * @author keyfly
 * @since 2021/3/6 23:14
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestParam {
    String value() default "";
}
