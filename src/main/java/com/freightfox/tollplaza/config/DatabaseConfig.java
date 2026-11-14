package com.freightfox.tollplaza.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
@Slf4j
public class DatabaseConfig {

    @Value("${spring.datasource.url:jdbc:h2:mem:tollplaza_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL}")
    private String rawUrl;

    @Value("${spring.datasource.username:sa}")
    private String username;

    @Value("${spring.datasource.password:}")
    private String password;

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        String jdbcUrl = rawUrl;

        // Auto-handle cloud URI formats like postgres://user:pass@host/db or jdbc:postgresql://user:pass@host/db
        if (jdbcUrl.startsWith("postgres://") || jdbcUrl.startsWith("postgresql://")) {
            jdbcUrl = "jdbc:" + jdbcUrl;
        }

        if (jdbcUrl.startsWith("jdbc:postgresql://") && jdbcUrl.contains("@")) {
            try {
                String uriString = jdbcUrl.substring(5); // strip "jdbc:"
                URI uri = new URI(uriString);
                String userInfo = uri.getUserInfo();
                if (userInfo != null && userInfo.contains(":")) {
                    String[] parts = userInfo.split(":", 2);
                    username = parts[0];
                    password = parts[1];
                }
                String host = uri.getHost();
                int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                String path = uri.getPath();
                jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path;
                log.info("Sanitized cloud PostgreSQL JDBC URL for host: {}, port: {}, database: {}", host, port, path);
            } catch (Exception e) {
                log.warn("Could not parse cloud PostgreSQL URI, falling back to raw URL: {}", e.getMessage());
            }
        }

        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(5);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(20000);

        return new HikariDataSource(config);
    }
}
