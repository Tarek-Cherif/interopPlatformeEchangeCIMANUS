package com.cnrps.InteropPlatformeEchangeCI.utilities;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;

public final class OracleErrorUtility {
  private OracleErrorUtility() {}

  public static boolean isUniqueViolation(Throwable error) {
    // Oracle ORA-00001 peut être traduit en DuplicateKeyException ou enveloppé par JDBC.
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
    while (error != null && seen.add(error)) {
      if (error instanceof DuplicateKeyException) return true;
      if (error instanceof SQLException) {
        SQLException sql = (SQLException) error;
        Set<SQLException> sqlSeen =
            Collections.newSetFromMap(new IdentityHashMap<SQLException, Boolean>());
        while (sql != null && sqlSeen.add(sql)) {
          if (sql.getErrorCode() == 1) return true;
          sql = sql.getNextException();
        }
      }
      error = error.getCause();
    }
    return false;
  }
}
