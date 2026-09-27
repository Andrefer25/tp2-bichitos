package org.bichitos.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class Database {
    private Database() { }

    public static Connection open() throws SQLException {
        return DriverManager.getConnection(url(), user(), password());
    }

    public static Connection openServerForDemo() throws SQLException {
        String url = url();
        if (!url.matches("jdbc:mysql://(?:localhost|127\\.0\\.0\\.1)(?::[0-9]+)?/bichitos_tp2(?:\\?.*)?"))
            throw new SQLException("La reinicialización de prueba solo admite el esquema bichitos_tp2 en MySQL local.");
        String serverUrl = url.replaceFirst("/bichitos_tp2(?=\\?|$)", "/");
        return DriverManager.getConnection(serverUrl, user(), password());
    }

    public static String setting(String name, String fallback) throws SQLException {
        return value(loadDotEnv(), name, fallback);
    }

    private static String url() throws SQLException {
        return setting("BICHITOS_DB_URL",
                "jdbc:mysql://localhost:3306/bichitos_tp2?serverTimezone=America%2FArgentina%2FBuenos_Aires");
    }

    private static String user() throws SQLException {
        String user = setting("BICHITOS_DB_USER", null);
        if (user == null || user.isBlank())
            throw new SQLException("Configurar BICHITOS_DB_USER en el entorno o en tp2-bichitos/.env.");
        return user;
    }

    private static String password() throws SQLException {
        String password = setting("BICHITOS_DB_PASSWORD", null);
        if (password == null) {
            throw new SQLException("Configurar BICHITOS_DB_USER y BICHITOS_DB_PASSWORD en el entorno o en tp2-bichitos/.env.");
        }
        return password;
    }

    private static String value(Map<String, String> config, String name, String fallback) {
        String environmentValue = System.getenv(name);
        return environmentValue != null ? environmentValue : config.getOrDefault(name, fallback);
    }

    private static Map<String, String> loadDotEnv() throws SQLException {
        Path file = Path.of(".env");
        Map<String, String> values = new HashMap<>();
        if (!Files.isRegularFile(file)) return values;
        try {
            for (String line : Files.readAllLines(file)) {
                String entry = line.trim();
                if (entry.isEmpty() || entry.startsWith("#")) continue;
                int separator = entry.indexOf('=');
                if (separator <= 0) continue;
                String key = entry.substring(0, separator).trim();
                String value = entry.substring(separator + 1).trim();
                if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
        } catch (IOException e) {
            throw new SQLException("No se pudo leer el archivo .env local.", e);
        }
        return values;
    }
}
