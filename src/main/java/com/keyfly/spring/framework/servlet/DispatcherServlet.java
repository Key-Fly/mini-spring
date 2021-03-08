package com.keyfly.spring.framework.servlet;

import com.keyfly.spring.framework.annotation.*;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DispatcherServlet核心类
 *
 * @author keyfly
 * @since 2021/3/6 23:10
 */
public class DispatcherServlet extends HttpServlet {

    /** 保存配置中读取的内容 */
    private Properties contextConfig = new Properties();

    /** 保存扫描的所有类名 */
    private List<String> classNames = new ArrayList<>();

    /** IOC容器，暂不考虑ConcurrentHashMap并发 */
    private Map<String, Object> ioc = new HashMap<>();

    /** 保存所有的url和method的映射关系 */
    private List<Handler> handlerMapping = new ArrayList<>();

    /** 内部类保存请求路径与method的映射关系 */
    private class Handler {
        // 保存Controller实例
        protected Object controller;
        // 保存映射到method
        protected Method method;
        // 保存正则模式
        protected Pattern pattern;
        // 保存参数顺序
        protected Map<String, Integer> paramIndexMapping;

        // 构造方法
        protected Handler(Pattern pattern, Object controller, Method method) {
            this.pattern = pattern;
            this.controller = controller;
            this.method = method;
            paramIndexMapping = new HashMap<>();
            putParamIndexMapping(method);

        }

        /** 解析method参数列表，记录参数顺序 */
        private void putParamIndexMapping(Method method) {
            // 1.提取方法中加了RequestParam注解的参数
            Annotation[][] pa = method.getParameterAnnotations();
            // 遍历参数列表
            for (int i = 0; i < pa.length; i++) {
                // 遍历参数注解列表
                for (Annotation a : pa[i]) {
                    // 其中，RequestParam注解实例指定了参数名
                    if (a instanceof RequestParam) {
                        String paramName = ((RequestParam) a).value();
                        // 若存在自定义指定参数名
                        if (!"".equals(paramName)) {
                            paramIndexMapping.put(paramName, i);
                        }
                    }
                }
            }
            // 2.提取方法中的request和response参数
            // 获取形参列表
            Class<?>[] paramsTypes = method.getParameterTypes();
            // 遍历形参
            for (int i = 0; i < paramsTypes.length; i++) {
                Class<?> type = paramsTypes[i];
                if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
                    paramIndexMapping.put(type.getName(), i);
                }
            }
        }
    }

    /** get请求 */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    /** post请求 */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            resp.getWriter().write("500 Exception " + Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {
        // 获取请求对应的处理器
        Handler handler = getHandler(req);
        if (handler == null) {
            resp.getWriter().write("404 Not Found!");
            return;
        }

        // 形参列表
        Class<?>[] paramTypes = handler.method.getParameterTypes();

        // 实参列表
        Object[] paramValues = new Object[paramTypes.length];

        // 获取url携带参数
        Map<String, String[]> params = req.getParameterMap();

        // 遍历参数列表，根据参数名称，把对应的参数放到对应的位置
        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            // 清除多余字符
            String value = Arrays.toString(entry.getValue())
                    .replaceAll("\\[|\\]", "")
                    .replaceAll("\\s", ",");
            // 若handler中不包含该参数值，直接跳过
            if (!handler.paramIndexMapping.containsKey(entry.getKey())) {continue;}
            // 若包含，则获取其索引
            Integer index = handler.paramIndexMapping.get(entry.getKey());
            // 根据对应的形参类型，将类型转换后的实参值放到实参列表对应位置
            paramValues[index] = convert(paramTypes[index], value);
        }

        // 请求
        if (handler.paramIndexMapping.containsKey(HttpServletRequest.class.getName())) {
            Integer reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = req;
        }

        // 响应
        if (handler.paramIndexMapping.containsKey(HttpServletResponse.class.getName())) {
            Integer respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[respIndex] = resp;
        }

        // 反射调用处理
        Object returnValue = handler.method.invoke(handler.controller, paramValues);
        // 返回
        if (returnValue == null) {return;}
        resp.getWriter().write(returnValue.toString());
    }

    /** 根据url获取处理器，负责url的正则匹配 */
    private Handler getHandler(HttpServletRequest req) {
        // 若为空，直接返回
        if (handlerMapping.isEmpty()) {return null;}
        // 若不为空
        // 获取uri
        String url = req.getRequestURI();
        // 获取根路径
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");
        // 拼接匹配处理器
        for (Handler handler : handlerMapping) {
            Matcher matcher = handler.pattern.matcher(url);
            // 如果没有匹配到，则跳过
            if (!matcher.matches()) {continue;}
            // 如果匹配到了，则返回该处理器
            return handler;
        }
        return null;
    }

    /** url参数的强制类型转换 */
    private Object convert(Class<?> type, String value) {
        // 整型类型转换
        if (Integer.class == type) {
            return Integer.valueOf(value);
        }
        // 浮点型类型转换
        if (Double.class == type) {
            return Double.valueOf(value);
        }
        // 添加其他的类型转换
        return value;
    }

    /** 初始化 */
    @Override
    public void init(ServletConfig config) throws ServletException {
        // 1.加载配置文件，参数对应web.xml配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        // 2.扫描相关的类
        doScanner(contextConfig.getProperty("scanPackage"));

        // 3.初始化扫描到的类，并将其放入IOC容器中
        doInstance();

        // 4.完成依赖注入
        doAutowired();

        // 5.初始化HandLerMapping
        initHandlerMapping();

    }

    /** 加载配置文件 */
    private void doLoadConfig(String contextConfigLocation) {
        // 获取配置文件信息
//        InputStream fis = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        InputStream fis = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation.replace("classpath:", ""));

        try {
            // 加载到properties对象中
            contextConfig.load(fis);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 关闭输入流
            if (null != fis) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /** 扫描包路径，收集class文件全类名 */
    private void doScanner(String scanPackage) {
        // 获取待扫描包路径
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        // 读取文件目录
        File classDir = new File(Objects.requireNonNull(url).getFile());
        // 遍历文件
        for (File file : Objects.requireNonNull(classDir.listFiles())) {
            if (file.isDirectory()) {
                // 若为文件夹，递归遍历
                doScanner(scanPackage + "." + file.getName());
            } else {
                // 若为文件，判断是否是.class结尾的java文件
                // 不属于class文件，跳过
                if (!file.getName().endsWith(".class")) {continue;}
                // 属于class文件，全类名添加到容器中
                String clazzName = scanPackage + "." + file.getName().replace(".class", "");
                classNames.add(clazzName);
            }
        }
    }

    /** 初始化扫描到的类，带注解的类实例化并添加到IOC容器中，为DI做准备 */
    private void doInstance() {
        // 若为空，直接返回
        if (classNames.isEmpty()) {return;}
        // 若不为空，遍历
        try {
            // 遍历中，只有加了注解的类需要初始化，以Controller和Service两个注解为例
            for (String clazzName : classNames) {
                // 根据类名获取类
                Class<?> clazz = Class.forName(clazzName);
                // 首先，判断是否添加了Controller注解
                if (clazz.isAnnotationPresent(Controller.class)) {
                    // 创建实例
                    Object instance = clazz.getConstructor().newInstance();
                    // 添加到ioc容器中，类名首字母小写作为beanName
                    ioc.put(toLowerFirstCase(clazz.getSimpleName()), instance);
                // 其次，判断是否添加了Service注解
                } else if (clazz.isAnnotationPresent(Service.class)) {
                    // 获取注解实例
                    Service service = clazz.getAnnotation(Service.class);
                    // 自定义beanName获取
                    String beanName = service.value();
                    // 若无自定义beanName，则默认类名首字母小写
                    if ("".equals(beanName.trim())) {
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }
                    // 创建实例
                    Object instance = clazz.getConstructor().newInstance();
                    ioc.put(beanName, instance);
                    // 注入时使用的是Service接口而非实现类，需要添加以接口首字母小写的beanName及实例到IOC容器中
                    for (Class<?> clazzInterface : clazz.getInterfaces()) {
                        // 如果该接口已经注入一个实例了，也就是该接口有多个实现类，则抛出已存在异常
                        // 此外，Spring框架通过Primary注解实现了在多个实现类条件下，优先注入指定实现类的功能
                        if (ioc.containsKey(clazzInterface.getName())) {
                            throw new Exception("The “" + clazzInterface.getName() + "” is exists!");
                        }
                        // 把接口类型直接作为beanName，全类名而非simpleName
                        ioc.put(clazzInterface.getName(), instance);
                    }
                } else {
                    // 没有加注解的类，不进行初始化
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /** 自动进行依赖注入 */
    private void doAutowired() {
        // 若为空，直接返回
        if (ioc.isEmpty()) { return; }
        // 若不为空，遍历，给ioc容器中已经实例化，但是字段需要自动注入的实例，注入实例化对象
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            // 获取所有字段，private、protected、public、default类型
            // 反射获得所有字段属性
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            // 遍历所有属性，判断有无自动注入Autowired注解
            for (Field field : fields) {
                // 若没有添加Autowired注解，直接跳过
                if (!field.isAnnotationPresent(Autowired.class)) {continue;}
                // 若添加了Autowired注解
                // 获取注解实例
                Autowired autowired = field.getAnnotation(Autowired.class);
                // 若用户在注解指定了beanName
                String beanName = autowired.value().trim();
                // 若用户没有指定，则默认按类型注入
                if ("".equals(beanName)) {
                    // 获取接口类型，作为beanName，并据此ioc容器取值
                    beanName = field.getType().getName();
                }
                // 暴力反射，所有加了Autowired注解的都要强制赋值
                field.setAccessible(true);
                try {
                    // 利用反射机制，给字段赋值
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /** 初始化映射处理器 */
    private void initHandlerMapping() {
        // 若为空，直接返回
        if (ioc.isEmpty()) {return;}
        // 若不为空，遍历
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            // 1.获取类
            Class<?> clazz = entry.getValue().getClass();
            // 1.1 若没有Controller注解，直接跳过
            if (!clazz.isAnnotationPresent(Controller.class)) {continue;}
            // 1.2 若存在Controller注解
            String url = "";
            // 2.根据RequestMapping注解拼接url
            // 2.1 若类上存在RequestMapping注解
            if (clazz.isAnnotationPresent(RequestMapping.class)) {
                // 获取该类的RequestMapping注解实例值，拼接到url
                RequestMapping requestMapping = clazz.getAnnotation(RequestMapping.class);
                url = requestMapping.value();
            }
            // 2.2 若方法上存在RequestMapping注解
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                // 若没有加RequestMapping注解，直接跳过
                if (!method.isAnnotationPresent(RequestMapping.class)) {continue;}
                // 若添加了RequestMapping注解，获取url
                RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                // 拼接url，并正则替换多余斜杠
                String regex = ("/" + url + requestMapping.value()).replaceAll("/+", "/");
                Pattern pattern = Pattern.compile(regex);
                handlerMapping.add(new Handler(pattern, entry.getValue(), method));
                System.out.println("mapping " + regex + "==>" + method);
            }
        }
    }

    /** 首字母小写转换 */
    private String toLowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }
}
