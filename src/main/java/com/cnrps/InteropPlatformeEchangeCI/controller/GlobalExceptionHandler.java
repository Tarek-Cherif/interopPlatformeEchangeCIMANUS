package com.cnrps.InteropPlatformeEchangeCI.controller;

import com.cnrps.InteropPlatformeEchangeCI.models.*;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(EpicCiException.class)
  public ResponseEntity<ErrorResponse> business(EpicCiException e, HttpServletRequest request) {
    return error(e.getStatus(), e.getCode(), e.getMessage(), e.getDetails(), request);
  }

  @ExceptionHandler(BindException.class)
  public ResponseEntity<ErrorResponse> binding(BindException e, HttpServletRequest request) {
    Map<String, Object> details = new LinkedHashMap<>();
    // Ne pas réafficher les valeurs rejetées du client (contenu, contrôles, données personnelles).
    e.getBindingResult()
        .getFieldErrors()
        .forEach(fe -> details.put(fe.getField(), "Valeur obligatoire ou format/plage invalide"));
    return error(
        HttpStatus.BAD_REQUEST,
        "INVALID_PARAMETERS",
        "Paramètres obligatoires manquants ou invalides.",
        details,
        request);
  }

  @ExceptionHandler({ConstraintViolationException.class, MultipartException.class})
  public ResponseEntity<ErrorResponse> invalid(Exception e, HttpServletRequest request) {
    return error(
        HttpStatus.BAD_REQUEST,
        "INVALID_REQUEST",
        "Requête multipart invalide.",
        Collections.emptyMap(),
        request);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> tooLarge(Exception e, HttpServletRequest request) {
    return error(
        HttpStatus.PAYLOAD_TOO_LARGE,
        "FILE_TOO_LARGE",
        "Taille maximale de requête ou fichier dépassée.",
        Collections.emptyMap(),
        request);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorResponse> media(Exception e, HttpServletRequest request) {
    return error(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "UNSUPPORTED_MEDIA_TYPE",
        "Utiliser multipart/form-data.",
        Collections.emptyMap(),
        request);
  }

  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<ErrorResponse> oracle(DataAccessException e, HttpServletRequest request) {
    log.error("Erreur accès Oracle classe={}", e.getClass().getSimpleName());
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "ORACLE_ERROR",
        "Une erreur Oracle empêche le traitement.",
        Collections.emptyMap(),
        request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> unexpected(Exception e, HttpServletRequest request) {
    log.error("Erreur inattendue classe={}", e.getClass().getSimpleName());
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "Une erreur interne empêche le traitement.",
        Collections.emptyMap(),
        request);
  }

  private ResponseEntity<ErrorResponse> error(
      HttpStatus status,
      String code,
      String message,
      Map<String, Object> details,
      HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(
            new ErrorResponse(
                false,
                Instant.now().toString(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                MDC.get("correlationId"),
                details));
  }
}
