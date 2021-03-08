package com.keyfly.app.controller;

import com.keyfly.app.service.DemoService;
import com.keyfly.spring.framework.annotation.Autowired;
import com.keyfly.spring.framework.annotation.Controller;
import com.keyfly.spring.framework.annotation.RequestMapping;
import com.keyfly.spring.framework.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author keyfly
 * @since 2021/3/6 23:18
 */
@Controller
@RequestMapping("/demo")
public class DemoController {

    @Autowired
    DemoService demoService;

    @RequestMapping("/query")
    public void query(HttpServletRequest request, HttpServletResponse response,
                      @RequestParam("name") String name) {
        String result = demoService.get(name);
        result = name + " " + result;
        try {
            response.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @RequestMapping("/add")
    public void add(HttpServletRequest request, HttpServletResponse response,
                    @RequestParam("a") Integer a, @RequestParam("b") Integer b) {
        try {
            response.getWriter().write(a + "+" + b + "=" + (a + b));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @RequestMapping("/remove")
    public void remove(HttpServletRequest request, HttpServletResponse response,
                       @RequestParam("id") Integer id) {
    }
}
