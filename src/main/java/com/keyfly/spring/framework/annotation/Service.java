package com.keyfly.spring.framework.annotation;

import java.lang.annotation.*;

/**
 * @author keyfly
 * @since 2021/3/6 23:10
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Service {
    String value() default "";
}
