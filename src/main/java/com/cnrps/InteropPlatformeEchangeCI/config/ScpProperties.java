package com.cnrps.InteropPlatformeEchangeCI.config;

import javax.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "scp")
public class ScpProperties {
  @NotBlank private String host;

  @Min(1)
  @Max(65535)
  private int port = 22;

  @NotBlank private String username = "oracle";
  @NotBlank private String password;
  // Le chemin administrateur entre dans une commande SCP: alphabet volontairement limité.
  @NotBlank
  @Pattern(regexp = "^/(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_-]+$")
  private String remoteDirectory = "/oracle/traitement";

  @Min(1)
  private int timeout = 30000;

  @NotBlank private String knownHosts;
  // Aucun toString Lombok: ne jamais exposer le mot de passe dans les journaux.
}
