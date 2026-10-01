package com.cnrps.InteropPlatformeEchangeCI.utilities;

import com.cnrps.InteropPlatformeEchangeCI.models.EpicCiException;
import com.cnrps.InteropPlatformeEchangeCI.models.EpicCiUploadRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class FileNameUtility {
  private FileNameUtility() {}

  public static String validateOriginal(String name) {
    // Rejet plutôt que nettoyage silencieux: aucun séparateur, contrôle, espace ou métacaractère
    // shell.
    if (name == null
        || name.length() > 128
        || name.contains("..")
        || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]*\\.txt")) {
      throw new EpicCiException(
          HttpStatus.BAD_REQUEST,
          "INVALID_FILE_NAME",
          "Nom invalide: fichier .txt, 128 caractères maximum, lettres ASCII, chiffres, point, tiret et underscore uniquement; aucun chemin.");
    }
    return name;
  }

  public static String remoteName(EpicCiUploadRequest request) {
    // Deux envois ne doivent jamais écraser le même fichier distant, même sur plusieurs instances
    // API.
    return "epic_"
        + request.getCodeEtab()
        + "_"
        + request.getTypeSalaire()
        + "_"
        + request.getAnnee()
        + "_"
        + request.getPeriode()
        + "_"
        + UUID.randomUUID().toString().replace("-", "")
        + ".txt";
  }
}
