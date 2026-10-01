package com.cnrps.InteropPlatformeEchangeCI.services;

import com.cnrps.InteropPlatformeEchangeCI.models.EpicCiUploadRequest;
import java.sql.CallableStatement;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

@Repository
public class EpicCiRepository {
  private static final String EXISTS_SQL =
      "SELECT 1 FROM EPIC_FM WHERE MATRICULE = ? "
          + "AND CODE_TYPE_SALAIRE = ? AND ANNEE = ? AND PERIODE = ?";
  private static final String CALL_SQL =
      "{call traitement_fichier_epic(?, ?, ?, ?, ?, ?, ?, ?, ?)}";
  private final JdbcTemplate jdbc;

  public EpicCiRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean existsEpicFile(
      Integer codeEtab, Integer typeSalaire, Integer annee, Integer periode) {
    // Les quatre colonnes de PK_EPIC_FM sont bindées, sans concaténation de données client.
    return jdbc.query(
        EXISTS_SQL,
        ps -> {
          ps.setInt(1, codeEtab);
          ps.setInt(2, typeSalaire);
          ps.setInt(3, annee);
          ps.setInt(4, periode);
        },
        (ResultSetExtractor<Boolean>) rs -> rs.next());
  }

  public void traitementFichierEpic(
      String remoteName, EpicCiUploadRequest r, String operationDate) {
    // Signature positionnelle explicite: aucune découverte de métadonnées Oracle nécessaire.
    // Pas de @Transactional couvrant SCP: SSH n'est pas une ressource transactionnelle JDBC.
    jdbc.execute(
        (ConnectionCallback<Void>)
            connection -> {
              try (CallableStatement statement = connection.prepareCall(CALL_SQL)) {
                if (jdbc.getQueryTimeout() > 0) statement.setQueryTimeout(jdbc.getQueryTimeout());
                statement.setString(1, remoteName); // xnomFichier: nom effectivement transféré
                statement.setInt(2, r.getCodeEtab()); // xmatricule = établissement
                statement.setInt(3, r.getPeriode()); // xperiode
                statement.setInt(4, r.getAnnee()); // xannee
                statement.setInt(5, r.getTypeSalaire()); // xcodeTypeSalaire
                statement.setLong(6, r.getMatriculeUser()); // xuser, NUMBER(10) nécessite Long
                statement.setString(7, "C"); // xtypeOperation
                statement.setString(
                    8, operationDate); // xdateOperation VARCHAR2 au format configuré
                statement.setInt(9, r.getDirection()); // xcdirect
                statement.execute();
              }
              return null;
            });
  }
}
