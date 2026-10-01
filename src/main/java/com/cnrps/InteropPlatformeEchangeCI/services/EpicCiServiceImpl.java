package com.cnrps.InteropPlatformeEchangeCI.services;

import com.cnrps.InteropPlatformeEchangeCI.models.*;
import com.cnrps.InteropPlatformeEchangeCI.utilities.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EpicCiServiceImpl implements EpicCiService {
  private final EpicCiRepository repository;
  private final FileTransferUtility transfer;
  private final Validator validator;
  private final Clock clock;
  private final DateTimeFormatter dateFormatter;

  public EpicCiServiceImpl(
      EpicCiRepository repository,
      FileTransferUtility transfer,
      Validator validator,
      Clock clock,
      @Value("${epic.operation-date-format:dd/MM/yyyy HH:mm:ss}") String dateFormat) {
    this.repository = repository;
    this.transfer = transfer;
    this.validator = validator;
    this.clock = clock;
    this.dateFormatter = DateTimeFormatter.ofPattern(dateFormat);
  }

  @Override
  public EpicCiResponse chargerFichierEpicCI(EpicCiUploadRequest r) {
    // Validation aussi côté service pour protéger les appels internes hors du contrôleur REST.
    if (r == null || !validator.validate(r).isEmpty()) {
      throw new EpicCiException(
          HttpStatus.BAD_REQUEST,
          "INVALID_PARAMETERS",
          "Métadonnées ou fichier manquants/invalides.");
    }
    if (r.getFichier().isEmpty()) {
      throw new EpicCiException(
          HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Le fichier ne doit pas être vide.");
    }
    String original = FileNameUtility.validateOriginal(r.getFichier().getOriginalFilename());
    log.info(
        "Réception EPIC fichier={} etab={} typeSalaire={} annee={} periode={} user={} direction={}",
        original,
        r.getCodeEtab(),
        r.getTypeSalaire(),
        r.getAnnee(),
        r.getPeriode(),
        r.getMatriculeUser(),
        r.getDirection());
    boolean exists = exists(r);
    log.info("Résultat contrôle EPIC_FM: existe={}", exists);
    if (exists) throw alreadyExists(r);
    String remoteName = FileNameUtility.remoteName(r);
    try {
      log.info("Début transfert SCP fichier={}", remoteName);
      transfer.transfer(r.getFichier(), remoteName);
      log.info("Fin transfert SCP fichier={}", remoteName);
    } catch (FileTransferException e) {
      log.warn("Échec transfert SCP fichier={} (procédure non appelée)", remoteName);
      throw new EpicCiException(
          HttpStatus.BAD_GATEWAY,
          "SCP_TRANSFER_FAILED",
          "Le transfert vers le serveur distant a échoué. La procédure Oracle n'a pas été appelée.");
    }
    try {
      String date = LocalDateTime.now(clock).format(dateFormatter);
      log.info("Appel traitement_fichier_epic fichier={}", remoteName);
      repository.traitementFichierEpic(remoteName, r, date);
      log.info("Fin normale appel traitement_fichier_epic fichier={}", remoteName);
    } catch (DataAccessException e) {
      // Un SELECT préalable ne verrouille pas une ligne absente. La PK Oracle est l'arbitre final.
      // Ne pas présenter une violation d'une autre table comme un doublon EPIC sans revérifier la
      // clé.
      if (OracleErrorUtility.isUniqueViolation(e)) {
        try {
          if (exists(r)) {
            log.warn("Conflit concurrent EPIC_FM fichier distant conservé={}", remoteName);
            throw alreadyExists(r);
          }
        } catch (DataAccessException recheckFailure) {
          log.warn("Revérification du doublon Oracle impossible fichier={}", remoteName);
        }
      }
      // Ne pas exposer le message JDBC brut; il peut contenir SQL ou données sensibles.
      log.error(
          "Échec Oracle classe={} fichier distant conservé={}",
          e.getClass().getSimpleName(),
          remoteName);
      Map<String, Object> details = keyDetails(r);
      details.put("fileName", remoteName);
      throw new EpicCiException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "ORACLE_PROCESSING_FAILED",
          "Le fichier a été transféré mais l'appel Oracle a échoué. Le fichier distant est conservé pour analyse; vérifier l'état Oracle avant toute reprise.",
          details);
    }
    return new EpicCiResponse(
        true,
        "FILE_LOADED",
        "Le fichier a été transféré et la procédure Oracle s'est terminée sans exception.",
        remoteName,
        original,
        r.getCodeEtab(),
        r.getTypeSalaire(),
        r.getAnnee(),
        r.getPeriode());
  }

  private boolean exists(EpicCiUploadRequest r) {
    return repository.existsEpicFile(
        r.getCodeEtab(), r.getTypeSalaire(), r.getAnnee(), r.getPeriode());
  }

  private EpicCiException alreadyExists(EpicCiUploadRequest r) {
    return new EpicCiException(
        HttpStatus.CONFLICT,
        "FILE_ALREADY_EXISTS",
        "Le fichier correspondant à l'établissement, au type de salaire, à l'année et à la période indiqués existe déjà au niveau de l'application Compte Individuel.",
        keyDetails(r));
  }

  private Map<String, Object> keyDetails(EpicCiUploadRequest r) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("codeEtab", r.getCodeEtab());
    details.put("typeSalaire", r.getTypeSalaire());
    details.put("annee", r.getAnnee());
    details.put("periode", r.getPeriode());
    return details;
  }
}
