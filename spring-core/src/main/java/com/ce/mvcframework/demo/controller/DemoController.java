package com.ce.mvcframework.demo.controller;

import com.ce.mvcframework.annotation.Autowired;
import com.ce.mvcframework.annotation.Controller;
import com.ce.mvcframework.annotation.RequestMapping;
import com.ce.mvcframework.demo.service.IDemoService;

@Controller
@RequestMapping("/demo")
public class DemoController {
    @Autowired
    private IDemoService demoService;

    @RequestMapping("/query")
    public String query(String name) {
        return demoService.get(name);
    }
}
