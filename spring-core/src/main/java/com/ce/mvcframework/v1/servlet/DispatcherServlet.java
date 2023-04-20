package com.ce.mvcframework.v1.servlet;

import com.ce.mvcframework.annotation.Autowired;
import com.ce.mvcframework.annotation.Controller;
import com.ce.mvcframework.annotation.RequestMapping;
import com.ce.mvcframework.annotation.Service;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class DispatcherServlet extends HttpServlet {

    private final Map<String, Object> mapping = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            resp.getWriter().write("500 Exception " + Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "");
        if (!this.mapping.containsKey(url)) {
            resp.getWriter().write("404 Not Found!!");
            return;
        }
        Method method = (Method) this.mapping.get(url);
        Map<String, String[]> params = req.getParameterMap();
        method.invoke(this.mapping.get(method.getName()), req, resp, params.get("name")[0]);
    }

    @Override
    public void init(ServletConfig config) {
        System.out.println("init");
        Map<String, Object> temp = new HashMap<>();
        try (InputStream is = this.getClass().getClassLoader().getResourceAsStream(config.getInitParameter("contextConfigLocation"))) {
            Properties configContext = new Properties();
            configContext.load(is);
            String scanPackage = configContext.getProperty("scanPackage");
            doScanner(scanPackage);
            for (String clazzName : mapping.keySet()) {
                if (!clazzName.contains(".")) continue;
                Class<?> clazz = Class.forName(clazzName);
                if (clazz.isAnnotationPresent(Controller.class)) {
                    temp.put(clazzName, clazz.getConstructor().newInstance());
                    String baseUrl = "";
                    if (clazz.isAnnotationPresent(RequestMapping.class)) {
                        RequestMapping requestMapping = clazz.getAnnotation(RequestMapping.class);
                        baseUrl = requestMapping.value();
                    }
                    Method[] methods = clazz.getMethods();
                    for (Method method : methods) {
                        if (!method.isAnnotationPresent(RequestMapping.class)) continue;
                        RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                        String url = baseUrl + requestMapping.value();
                        temp.put(url, method);
                    }
                } else if (clazz.isAnnotationPresent(Service.class)) {
                    Service service = clazz.getAnnotation(Service.class);
                    String baseName = service.value();
                    //如果没有指定service名称，则默认为类名
                    if ("".equals(baseName)) baseName = clazz.getName();
                    Object instance = clazz.getConstructor().newInstance();
                    temp.put(baseName, instance);
                    for (Class<?> i : clazz.getInterfaces()) {
                        temp.put(i.getName(), instance);
                    }
                }
            }
            mapping.putAll(temp);
            for (Object obj : mapping.values()) {
                if (obj == null) continue;
                Class<?> clazz = obj.getClass();
                if (clazz.isAnnotationPresent(Controller.class)) {
                    Field[] fields = clazz.getFields();
                    for (Field field : fields) {
                        if (!field.isAnnotationPresent(Autowired.class)) continue;
                        Autowired autowired = field.getAnnotation(Autowired.class);
                        String beanName = autowired.value();
                        if ("".equals(beanName)) beanName = field.getType().getName();
                        field.setAccessible(true);
                        try {
                            field.set(mapping.get(clazz.getName()), mapping.get(beanName));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(mapping);
//        super.init();
    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource(scanPackage.replaceAll("\\.", "/"));
        File classDir = new File(url.getFile());
        for (File file : classDir.listFiles()) {
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {
                String clazzName = scanPackage + "." + file.getName().replace(".class", "");
                mapping.put(clazzName, null);
            }
        }
    }
}
