package com.cnrps.InteropPlatformeEchangeCI.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
  @Bean
  public OpenAPI epicOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Interop EPIC / Compte Individuel CNRPS")
                .version("1.0.0")
                .description(
                    "Validation, contrôle EPIC_FM, transfert SCP puis appel synchrone de traitement_fichier_epic."));
  }
}
