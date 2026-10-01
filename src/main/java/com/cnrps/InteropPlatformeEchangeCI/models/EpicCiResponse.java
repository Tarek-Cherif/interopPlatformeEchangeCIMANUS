package com.cnrps.InteropPlatformeEchangeCI.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EpicCiResponse {
  private final boolean success;
  private final String code;
  private final String message;
  private final String fileName;
  private final String originalFileName;
  private final Integer codeEtab;
  private final Integer typeSalaire;
  private final Integer annee;
  private final Integer periode;
}
