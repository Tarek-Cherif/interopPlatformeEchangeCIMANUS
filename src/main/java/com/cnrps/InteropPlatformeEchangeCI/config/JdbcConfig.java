package com.cnrps.InteropPlatformeEchangeCI.config;

import java.time.Clock;
import java.time.ZoneId;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class JdbcConfig {
  @Bean
  public JdbcTemplate jdbcTemplate(
      DataSource dataSource, @Value("${epic.oracle-query-timeout-seconds:300}") int timeout) {
    JdbcTemplate template = new JdbcTemplate(dataSource);
    template.setQueryTimeout(timeout);
    return template;
  }

  @Bean
  public Clock businessClock(@Value("${epic.time-zone:Africa/Tunis}") String zone) {
    // Horloge injectable: date métier prévisible dans les tests, fuseau explicite en production.
    return Clock.system(ZoneId.of(zone));
  }
}
