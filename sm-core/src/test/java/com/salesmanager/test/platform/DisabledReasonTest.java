package com.salesmanager.test.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class DisabledReasonTest {
    @Test
    void disabledTestsExplainWhyTheyAreDisabled() throws Exception {
        Path root = Paths.get(DisabledReasonTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<Path> classes;
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            classes = paths.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList());
        }
        assertFalse(classes.isEmpty());
        for (Path file : classes) {
            String name = root.relativize(file).toString().replace(java.io.File.separatorChar, '.');
            Class<?> type = Class.forName(name.substring(0, name.length() - 6), false,
                    DisabledReasonTest.class.getClassLoader());
            check(type.getDeclaredAnnotation(Disabled.class), type.getName());
            for (Method method : type.getDeclaredMethods()) {
                check(method.getDeclaredAnnotation(Disabled.class), method.toString());
            }
        }
    }

    private void check(Disabled disabled, String location) {
        if (disabled != null) {
            assertFalse(disabled.value().trim().isEmpty(), location + " needs a disabled reason");
        }
    }
}
