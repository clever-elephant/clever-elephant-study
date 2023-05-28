package com.ce.mvcframework.v3.servlet;

import com.ce.mvcframework.annotation.*;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

public class DispatcherServlet extends HttpServlet {
    //保存application.properties配置文件中的内容
    private final Properties contextConfig = new Properties();
    //所有扫描到的类名
    private final List<String> classNames = new ArrayList<>();
    /**
     * IoC容器
     * 为了简化程序，暂时不考虑ConcurrentHashMap
     * 主要关注设计思想和原理
     */
    private final Map<String, Object> ioc = new HashMap<>();
    //保存url和Method的对应关系
    private final List<Handler> handlerMapping = new ArrayList<>();

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
        Handler handler = null;
        try {
            handler = getHandler(req);
        } catch (Exception e) {
            resp.getWriter().write("404 Not Found");
            return;
        }
        Method method = handler.method;
        //获取request穿来的参数
        Map<String, String[]> params = req.getParameterMap();
        //获取方法形参列表
        Class<?>[] parameterTypes = method.getParameterTypes();
        //传给方法的参数
        Object[] paramValues = new Object[parameterTypes.length];
        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            if (!handler.paramIndexMapping.containsKey(entry.getKey())) continue;
            Integer index = handler.paramIndexMapping.get(entry.getKey());
            paramValues[index] = entry.getValue();
        }
        if (handler.paramIndexMapping.containsKey(HttpServletRequest.class.getName())) {
            Integer index = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[index] = req;
        }
        if (handler.paramIndexMapping.containsKey(HttpServletResponse.class.getName())) {
            Integer index = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[index] = resp;
        }

        Object invoke = method.invoke(handler.controller, paramValues);
        if (invoke == null) return;
        resp.getWriter().write(invoke.toString());
    }

    @Override
    public void init(ServletConfig config) {

        //1.加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        //2.扫描相关的类
        doScanner(contextConfig.getProperty("scanPackage"));

        //3.初始化扫描到的类，放入Ioc容器中
        doInstance();

        //4.完成依赖注入
        doAutowired();

        //5.初始化HandlerMapping
        initHandlerMapping();
    }


    private void doLoadConfig(String contextConfigLocation) {
        try (InputStream in = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);) {
            this.contextConfig.load(in);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource(scanPackage.replaceAll("\\.", "/"));
        File classDir = new File(url.getFile());
        for (File file : classDir.listFiles()) {
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {
                String clazzName = scanPackage + "." + file.getName().replace(".class", "");
                classNames.add(clazzName);
            }
        }
    }

    private void doInstance() {
        for (String className : classNames) {
            if (!className.contains(".")) continue;
            try {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(Controller.class)) {
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName, clazz.getConstructor().newInstance());
                } else if (clazz.isAnnotationPresent(Service.class)) {
                    Service service = clazz.getAnnotation(Service.class);
                    String beanName = service.value();
                    if ("".equals(service.value().trim())) {
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.getConstructor().newInstance();
                    ioc.put(beanName, instance);
                    for (Class<?> i : clazz.getInterfaces()) {
                        if (ioc.containsKey(i.getName())) {
                            throw new Exception("The '" + i.getName() + "' is exists!!");
                        }
                        ioc.put(i.getName(), instance);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    private void doAutowired() {
        for (Object bean : ioc.values()) {
            Field[] fields = bean.getClass().getFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(Autowired.class)) {
                    Autowired autowired = field.getAnnotation(Autowired.class);
                    String beanName = autowired.value();
                    if ("".equals(beanName)) {
                        beanName = toLowerFirstCase(field.getType().getSimpleName());
                    }
                    Object o = ioc.get(beanName);
                    field.setAccessible(true);
                    try {
                        field.set(bean, o);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void initHandlerMapping() {
        for (Object bean : ioc.values()) {
            Class<?> clazz = bean.getClass();
            if (!clazz.isAnnotationPresent(Controller.class)) continue;
            String baseUrl = "";
            if (clazz.isAnnotationPresent(RequestMapping.class)) {
                baseUrl = clazz.getAnnotation(RequestMapping.class).value();
            }
            for (Method method : clazz.getMethods()) {
                if (!method.isAnnotationPresent(RequestMapping.class)) continue;
                RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                String url = baseUrl + requestMapping.value();
                handlerMapping.add(new Handler(bean, method, Pattern.compile(url)));
            }
        }
    }

    /**
     * 首字母小写
     *
     * @param str
     * @return
     */
    private String toLowerFirstCase(String str) {
        char[] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    private Handler getHandler(HttpServletRequest req) throws Exception {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replace("/+", "");
        for (Handler handler : handlerMapping) {
            if (handler.pattern.matcher(url).matches()) {
                return handler;
            }
        }
        throw new Exception("404 Not Found!!");
    }

    private static class Handler {
        protected Object controller;

        protected Method method;

        protected Pattern pattern;

        protected Map<String, Integer> paramIndexMapping;

        public Handler(Object controller, Method method, Pattern pattern) {
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;
            paramIndexMapping = new HashMap<>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method) {
            Annotation[][] annotations = method.getParameterAnnotations();
            for (int i = 0; i < annotations.length; i++) {
                for (Annotation annotation : annotations[i]) {
                    if (annotation instanceof RequestParam param) {
                        String value = param.value();
                        if (!"".equals(value.trim())) {
                            paramIndexMapping.put(value, i);
                        }
                    }
                }
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> type = parameterTypes[i];
                if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
                    paramIndexMapping.put(type.getName(), i);
                }
            }

        }
    }

}
