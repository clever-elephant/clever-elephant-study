package com.ce.mvcframework.demo.service.impl;

import com.ce.mvcframework.annotation.Service;
import com.ce.mvcframework.demo.service.IDemoService;

@Service
public class DemoService implements IDemoService {
    public String get(String name) {
        return "My name is " + name;
    }
}
