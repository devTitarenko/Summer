package com.titarenko.di;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Summer {

    private static final Summer SUMMER = new Summer();
    private static final String POST_CONSTRUCT = "init";
    private static final String EXTENSION = ".java";
    private static final String SEPARATOR = File.separator;
    private static final String SRC_MAIN_JAVA = "src" + SEPARATOR + "main" + SEPARATOR + "java" + SEPARATOR;
    private static Map<Class<?>, Object> singletons;
    private static Set<Class<?>> allAnnotatedClasses;

    private Summer() {
    }

    /**
     * Scanning 'src' path and creating Brick instances of this package
     *
     * @param src - path to .java files, starts from "src/main/java/"
     * @return instance of this class
     */
    public static Summer go(String src) {
        if (allAnnotatedClasses == null) {
            allAnnotatedClasses = getPathsStream(src).stream()
                    .map(Summer::mapToClass)
                    .filter(cls -> cls.isAnnotationPresent(Brick.class))
                    .collect(Collectors.toSet());
            singletons = allAnnotatedClasses.stream()
                    .filter(cls -> !cls.getAnnotation(Brick.class).isMultiple())
                    .map(cls -> new HashMap.SimpleEntry<>(cls, createInstance(cls)))                    // phase 1
                    .collect(Collectors.toMap(Map.Entry::getKey, AbstractMap.SimpleEntry::getValue));
            singletons.entrySet().stream()
                    .flatMap(entry -> injectFields(entry.getKey(), entry.getValue()).stream())          // phase 2
                    .collect(Collectors.toList())
                    .forEach(Summer::invokeInit);                                                       // phase 3
        }
        return SUMMER;
    }

    /**
     * Obtain singleton instance by Class.
     * Only for classes annotated {@link Brick}
     * with {@link Brick#isMultiple() = false} option.
     *
     * @param cls - the class of desirable instance
     * @return instance of cls
     * @throws RuntimeException in case an instance was not found
     */
    public <T> T giveMeInstance(Class<T> cls) {
        Object instance = singletons.get(cls);
        if (instance != null) {
            return (T) instance;
        } else if (allAnnotatedClasses.contains(cls)) {
            instance = createInstance(cls);
            injectFields(cls, instance)
                    .forEach(Summer::invokeInit);
            return (T) instance;
        }
        throw new RuntimeException("no such class");
    }

    private static Collection<String> getPathsStream(String src) {
        try (Stream<Path> paths = Files.walk(Paths.get(src))) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(EXTENSION))
                    .map(path -> path.toString().replaceAll(EXTENSION + "|" + SRC_MAIN_JAVA, "").replace(SEPARATOR, "."))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Class<?> mapToClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object createInstance(Class<?> cls) {
        try {
            return cls.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Object> injectFields(Class<?> aClass, Object instance) {
        List<Object> result = new LinkedList<>();
        result.add(instance);
        for (Field field : aClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(InsertPlease.class)) {
                field.setAccessible(true);
                Class<?> obtainedClass = getInstance(field);
                try {
                    if (obtainedClass.getAnnotation(Brick.class).isMultiple()) {
                        Object prototype = createInstance(obtainedClass);
                        field.set(instance, prototype);
                        result.addAll(injectFields(obtainedClass, prototype));
                    } else {
                        field.set(instance, singletons.get(obtainedClass));
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if (aClass.getSuperclass() != Object.class) {
            result.addAll(injectFields(aClass.getSuperclass(), instance));
        }
        return result;
    }

    private static Class<?> getInstance(Field field) {
        Class<?> fieldType = field.getType();
        Class<?> what = field.getAnnotation(InsertPlease.class).what();
        return fieldType.isInterface() ? what.equals(Object.class) ? obtainByInterface(fieldType) : what : fieldType;
    }

    private static Class<?> obtainByInterface(Class<?> type) {
        return allAnnotatedClasses.stream()
                .filter(cls ->
                        Stream.of(cls.getInterfaces(), cls.getSuperclass().getInterfaces())
                                .flatMap(Stream::of)
                                .anyMatch(anInterface -> anInterface.equals(type))
                )
                .findFirst()
                .orElseThrow(() -> new RuntimeException("no instance"));
    }

    private static void invokeInit(Object instance) {
        for (Method method : instance.getClass().getDeclaredMethods()) {
            if (POST_CONSTRUCT.equals(method.getName())) {
                try {
                    method.invoke(instance);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
