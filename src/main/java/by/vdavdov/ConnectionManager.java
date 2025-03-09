package by.vdavdov;

import by.vdavdov.utils.YamlUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class ConnectionManager {
    private static final Logger log = LogManager.getLogger(ConnectionManager.class);
    private static volatile DataSource dataSource;

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL JDBC Driver not found", e);
        }
    }

    public static DataSource getDataSource() {
        if (dataSource == null) {
            synchronized (ConnectionManager.class) {
                if (dataSource == null) {
                    HikariConfig config = getHikariConfig();
                    config.addDataSourceProperty("tcpKeepAlive", "true");

                    dataSource = new HikariDataSource(config);
                    testConnection();
                }
            }
        }
        return dataSource;
    }

    private static HikariConfig getHikariConfig() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(YamlUtil.dbUrl);
        config.setUsername(YamlUtil.dbUser);
        config.setPassword(YamlUtil.dbPassword);

        config.setMaximumPoolSize(5);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setConnectionTestQuery("SELECT 1");
        return config;
    }

    private static void testConnection() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT version()")) {

            if (rs.next()) {
                log.info("Successfully connected to PostgreSQL. Version: {}", rs.getString(1));
            }
        } catch (SQLException e) {
            log.error("Connection test failed: {}", e.getMessage());
            throw new RuntimeException("Database connection failed", e);
        }
    }

    private ConnectionManager() {
    }
}
