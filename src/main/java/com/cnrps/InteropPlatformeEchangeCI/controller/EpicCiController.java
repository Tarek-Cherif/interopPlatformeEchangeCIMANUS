package com.cnrps.InteropPlatformeEchangeCI.controller;

import com.cnrps.InteropPlatformeEchangeCI.models.*;
import com.cnrps.InteropPlatformeEchangeCI.services.EpicCiService;
import com.cnrps.InteropPlatformeEchangeCI.utilities.NumericPropertyEditor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import javax.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/epic-ci")
public class EpicCiController {
  private final EpicCiService service;

  public EpicCiController(EpicCiService service) {
    this.service = service;
  }

  @InitBinder
  public void configureBinding(WebDataBinder binder) {
    binder.setAllowedFields(
        "fichier", "periode", "annee", "typeSalaire", "codeEtab", "matriculeUser", "direction");
    binder.registerCustomEditor(
        Integer.class, "periode", new NumericPropertyEditor("[0-9]{1,2}", false));
    binder.registerCustomEditor(
        Integer.class, "annee", new NumericPropertyEditor("[1-9][0-9]{3}", false));
    binder.registerCustomEditor(
        Integer.class, "typeSalaire", new NumericPropertyEditor("[0-9]{1,2}", false));
    binder.registerCustomEditor(
        Integer.class, "codeEtab", new NumericPropertyEditor("[0-9]{1,4}", false));
    binder.registerCustomEditor(
        Long.class, "matriculeUser", new NumericPropertyEditor("[0-9]{1,10}", true));
    binder.registerCustomEditor(
        Integer.class, "direction", new NumericPropertyEditor("[0-9]{1,4}", false));
  }

  @Operation(
      operationId = "chargerFichierEpicCI",
      summary = "Charger une déclaration EPIC dans Compte Individuel",
      description =
          "Valide le multipart, recherche la clé établissement/typeSalaire/année/période dans EPIC_FM, "
              + "retourne 409 si présente sans transfert ni appel Oracle. Sinon: SCP puis appel synchrone de traitement_fichier_epic. "
              + "Une période supérieure à 12 est acceptée dans la limite de 99.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Transfert réussi et retour normal de la procédure",
        content = @Content(schema = @Schema(implementation = EpicCiResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Multipart, fichier ou paramètres invalides",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "Clé déjà présente dans EPIC_FM",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "413",
        description = "Taille multipart dépassée",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "415",
        description = "Type de contenu non pris en charge",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "500",
        description = "Erreur Oracle ou inattendue",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "502",
        description = "Échec du transfert SCP",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping(
      value = "/chargerFichierEpic_CI",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public EpicCiResponse chargerFichierEpicCI(@Valid @ModelAttribute EpicCiUploadRequest request) {
    return service.chargerFichierEpicCI(request);
  }
}
