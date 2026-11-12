package com.salesmanager.test.shop.container;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.HashSet;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import static org.junit.jupiter.api.Assertions.*;

class ContainerImageContractTest {
    private final Path module = Path.of("").toAbsolutePath();

    private String dockerfile() throws Exception {
        return Files.readString(module.resolve("Dockerfile"));
    }

    private List<String[]> copies() throws Exception {
        List<String[]> result = new ArrayList<>();
        for (String line : dockerfile().split("\\R")) {
            if (line.startsWith("COPY ")) {
                String[] words = line.replaceFirst("^COPY\\s+(--\\S+\\s+)*", "").trim().split("\\s+");
                assertTrue(words.length >= 2, line);
                for (int i = 0; i < words.length - 1; i++) {
                    result.add(new String[]{words[i], words[words.length - 1]});
                }
            }
        }
        assertFalse(result.isEmpty());
        return result;
    }

    @Test
    void imageUsesJava21NonRootAndHealthcheckedPropertiesLauncher() throws Exception {
        String docker = dockerfile();
        assertTrue(Pattern.compile("(?m)^FROM eclipse-temurin:21-jre\\s*$").matcher(docker).find());
        Matcher user = Pattern.compile("(?m)^USER\\s+(\\S+)").matcher(docker);
        assertTrue(user.find(), "Container must declare USER");
        String userName = user.group(1).split(":")[0];
        assertFalse(Set.of("root", "0").contains(userName));
        assertTrue(Pattern.compile("(?m)^EXPOSE 8080\\s*$").matcher(docker).find());
        assertTrue(Pattern.compile("HEALTHCHECK[^\\n]*(?:\\\\\\R\\s*)?CMD curl -fsS http://localhost:8080/actuator/health").matcher(docker).find());
        String entrypoint = docker.lines().filter(line -> line.startsWith("ENTRYPOINT ")).findFirst().orElseThrow();
        assertTrue(entrypoint.contains("org.springframework.boot.loader.launch.PropertiesLauncher"));
        Matcher loader = Pattern.compile("-Dloader.path=([^\"]+)").matcher(entrypoint);
        assertTrue(loader.find());
        String destination = copies().stream().filter(copy -> copy[0].endsWith("/database.properties"))
                .map(copy -> copy[1]).findFirst().orElseThrow();
        String workdir = docker.lines().filter(line -> line.startsWith("WORKDIR "))
                .map(line -> line.substring(8).trim()).findFirst().orElseThrow();
        assertEquals(Path.of(loader.group(1)).normalize(), Path.of(workdir).resolve(destination).getParent().normalize());
    }

    @Test
    void copySourcesExistAndAreAllowedIntoBuildContext() throws Exception {
        List<String> rules = Files.readAllLines(module.resolve(".dockerignore"));
        assertEquals("*", rules.stream().filter(line -> !line.isBlank() && !line.startsWith("#")).findFirst().orElse(""),
                "Docker context must use an explicit allow-list");
        for (String[] copy : copies()) {
            String source = copy[0].replaceFirst("^\\./", "");
            if (!source.equals("target/shopizer.jar")) assertTrue(Files.exists(module.resolve(source)), source);
            boolean allowed = false;
            for (String rule : rules) {
                if (rule.isBlank() || rule.startsWith("#")) continue;
                boolean include = rule.startsWith("!");
                String pattern = include ? rule.substring(1) : rule;
                if (pattern.endsWith("/")) {
                    String directory = pattern.substring(0, pattern.length() - 1);
                    if (source.equals(directory) || source.startsWith(pattern)) allowed = include;
                } else if (module.getFileSystem().getPathMatcher("glob:" + pattern).matches(Path.of(source))) {
                    allowed = include;
                }
            }
            assertTrue(allowed, source + " is excluded from the Docker build context");
        }
    }

    @Test
    void dockerDatabaseProvidesConsumedKeysAndUsesCopiedH2File() throws Exception {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(module.resolve("src/main/resources/profiles/docker/database.properties"))) {
            properties.load(in);
        }
        Path core = module.resolve("../sm-core/src/main");
        String xml = Files.readString(core.resolve("resources/spring/shopizer-core-config.xml"));
        assertTrue(xml.contains("classpath:database.properties"));
        String configuration = Files.readString(core.resolve("java/com/salesmanager/core/business/configuration/DataConfiguration.java"));
        Matcher values = Pattern.compile("@Value\\(\"\\$\\{((?:db|hibernate)\\.[^}:]+)}\"\\)").matcher(configuration);
        Set<String> keys = new HashSet<>();
        while (values.find()) keys.add(values.group(1));
        assertFalse(keys.isEmpty());
        for (String key : keys) assertNotNull(properties.getProperty(key), "Missing required database key: " + key);
        Matcher file = Pattern.compile("jdbc:h2:file:\\./([^;]+)").matcher(properties.getProperty("db.jdbcUrl"));
        assertTrue(file.find());
        String filename = file.group(1) + ".mv.db";
        String workdir = dockerfile().lines().filter(line -> line.startsWith("WORKDIR "))
                .map(line -> line.substring(8).trim()).findFirst().orElse("/");
        Path expected = Path.of(workdir).resolve(filename).normalize();
        assertTrue(copies().stream().filter(copy -> copy[0].equals(filename)).anyMatch(copy -> {
            Path destination = Path.of(workdir).resolve(copy[1]);
            if (copy[1].endsWith("/") || copy[1].equals(".")) destination = destination.resolve(filename);
            return expected.equals(destination.normalize());
        }),
                "H2 database must be copied relative to WORKDIR: " + filename);
    }

    @Test
    void builtJarContainsPropertiesLauncher() throws Exception {
        Path jar = module.resolve("target/shopizer.jar");
        Assumptions.assumeTrue(Files.exists(jar), "Boot jar is produced during package");
        try (JarFile archive = new JarFile(jar.toFile())) {
            assertNotNull(archive.getJarEntry("org/springframework/boot/loader/launch/PropertiesLauncher.class"));
        }
    }
}
