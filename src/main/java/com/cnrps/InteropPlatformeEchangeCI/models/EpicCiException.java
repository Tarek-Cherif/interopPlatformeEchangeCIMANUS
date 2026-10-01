package com.cnrps.InteropPlatformeEchangeCI.models;

import java.util.Collections;
import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class EpicCiException extends RuntimeException {
  private final HttpStatus status;
  private final String code;
  private final Map<String, Object> details;

  public EpicCiException(HttpStatus status, String code, String message) {
    this(status, code, message, Collections.emptyMap());
  }

  public EpicCiException(
      HttpStatus status, String code, String message, Map<String, Object> details) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }
}
