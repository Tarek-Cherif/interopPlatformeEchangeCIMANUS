package com.cnrps.InteropPlatformeEchangeCI.models;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.*;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class EpicCiUploadRequest {
  @NotNull(message = "Le fichier est obligatoire")
  @Schema(type = "string", format = "binary", description = "Déclaration EPIC .txt non vide")
  private MultipartFile fichier;

  @NotNull
  @Min(0)
  @Max(99)
  @Schema(description = "Période compatible NUMBER(2), y compris au-delà de 12", example = "13")
  private Integer periode;

  @NotNull
  @Min(1000)
  @Max(9999)
  @Schema(description = "Année sur exactement quatre chiffres", example = "2026")
  private Integer annee;

  @NotNull
  @Min(0)
  @Max(99)
  @Schema(description = "EPIC_FM.CODE_TYPE_SALAIRE NUMBER(2)", example = "1")
  private Integer typeSalaire;

  @NotNull
  @Min(0)
  @Max(9999)
  @Schema(description = "Code établissement de 1 à 4 chiffres: EPIC_FM.MATRICULE", example = "3012")
  private Integer codeEtab;

  @NotNull
  @Min(0)
  @Max(9999999999L)
  @Schema(description = "Matricule utilisateur de 1 à 10 chiffres", example = "12345")
  private Long matriculeUser;

  @NotNull
  @Min(0)
  @Max(9999)
  @Schema(description = "Direction compatible NUMBER(4)", example = "5600")
  private Integer direction;
}
