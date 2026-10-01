package com.cnrps.InteropPlatformeEchangeCI.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSourceConfig {
  // DataSourceProperties est fourni par Spring Boot; un seul pool partagé par JDBC et JPA.
  @Bean
  @ConfigurationProperties("spring.datasource.hikari")
  public HikariDataSource dataSource(DataSourceProperties properties) {
    return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
  }
}
