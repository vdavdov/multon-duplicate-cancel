package by.vdavdov.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class YamlUtil {
    private static final Logger log = LogManager.getLogger(YamlUtil.class);

    public static String host;
    public static String authLogin;
    public static String authPassword;

    public static void loadInternalConfig() throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream inputStream = YamlUtil.class.getClassLoader().getResourceAsStream("config.yaml")) {
            if (inputStream == null) {
                throw new FileNotFoundException("Встроенный конфигурационный файл не найден");
            }
            parseConfig(yaml.load(inputStream));
        }
    }

    public static void loadExternalConfig(String path) throws IOException {
        Yaml yaml = new Yaml();
        Path configPath = Paths.get(path);
        if (!Files.exists(configPath)) {
            log.error("Внешний конфигурационный файл {} не найден", path);
            throw new FileNotFoundException("Внешний конфигурационный файл не найден");
        }
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            parseConfig(yaml.load(inputStream));
        }
    }

    private static void parseConfig(Map<String, Object> config) {
        if (config == null) {
            throw new IllegalArgumentException("Конфигурация не может быть null");
        }

        host = getStringValue(config, "host");

        Map<String, Object> authConfig = getMapValue(config, "auth");
        if (authConfig != null) {
            authLogin = getStringValue(authConfig, "login");
            authPassword = getStringValue(authConfig, "password");
        }

        logConfig();
    }

    private static void logConfig() {
        log.info("Конфигурация загружена \n" +
                        "host: {} \n" +
                        "login: {} \n" +
                        "password: {} \n"
                , host, authLogin, authPassword);
    }

    private static String getStringValue(Map<String, Object> config, String key) {
        Object value = config.get(key);
        return value != null ? value.toString() : null;
    }

    private static Map<String, Object> getMapValue(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }
}
