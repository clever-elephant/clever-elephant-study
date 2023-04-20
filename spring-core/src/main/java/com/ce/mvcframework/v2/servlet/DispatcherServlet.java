package com.ce.mvcframework.v2.servlet;

import com.ce.mvcframework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

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
    private final Map<String, Method> handlerMapping = new HashMap<>();

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
        url = url.replace(contextPath, "").replace("/+", "/");
        if (!this.handlerMapping.containsKey(url)) {
            resp.getWriter().write("404 Not Found!!");
            return;
        }
        Method method = this.handlerMapping.get(url);
        //获取request穿来的参数
        Map<String, String[]> params = req.getParameterMap();
        //获取方法形参列表
        Class<?>[] parameterTypes = method.getParameterTypes();
        //传给方法的参数
        Object[] paramValues = new Object[parameterTypes.length];
        //从request提取method需要的参数
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (parameterType == HttpServletRequest.class) {
                paramValues[i] = req;
            } else if (parameterType == HttpServletResponse.class) {
                paramValues[i] = resp;
            } else if (parameterType == String.class) {
                Annotation[][] annotations = method.getParameterAnnotations();
                for (Annotation[] item : annotations) {
                    for (Annotation annotation : item) {
                        if (annotation instanceof RequestParam paramAnnotation) {
                            String paramName = paramAnnotation.value();
                            if (!"".equals(paramName)) {
                                String value = Arrays.toString(params.get(paramName));
                                paramValues[i] = value;
                            }
                        }
                    }
                }
            }
        }
        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        method.invoke(ioc.get(beanName), paramValues);
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
        try (InputStream in = this.getClass().getResourceAsStream(contextConfigLocation);) {
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
                String url = baseUrl + method.getAnnotation(RequestMapping.class);
                handlerMapping.put(url, method);
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
}
