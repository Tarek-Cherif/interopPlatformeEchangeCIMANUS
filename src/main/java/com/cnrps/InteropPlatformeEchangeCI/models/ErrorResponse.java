package com.cnrps.InteropPlatformeEchangeCI.models;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorResponse {
  private final boolean success;
  private final String timestamp;
  private final int status;
  private final String code;
  private final String message;
  private final String path;
  private final String correlationId;
  private final Map<String, Object> details;
}
