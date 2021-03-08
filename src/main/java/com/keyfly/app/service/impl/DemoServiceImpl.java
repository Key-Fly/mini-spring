package com.keyfly.app.service.impl;

import com.keyfly.app.service.DemoService;
import com.keyfly.spring.framework.annotation.Service;

/**
 * @author keyfly
 * @since 2021/3/6 23:16
 */
@Service
public class DemoServiceImpl implements DemoService {
    public String get(String name) {
        return "Core Spring";
    }
}
