package com.cnrps.InteropPlatformeEchangeCI.utilities;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.*;

class OracleErrorUtilityTest {
  @Test
  void detectsTranslatedDuplicate() {
    assertTrue(OracleErrorUtility.isUniqueViolation(new DuplicateKeyException("duplicate")));
  }

  @Test
  void detectsNestedOra00001() {
    assertTrue(
        OracleErrorUtility.isUniqueViolation(
            new DataIntegrityViolationException(
                "wrapped", new SQLException("duplicate", "23000", 1))));
  }

  @Test
  void detectsNextSQLException() {
    SQLException first = new SQLException("first", "99999", 0);
    first.setNextException(new SQLException("duplicate", "23000", 1));
    assertTrue(OracleErrorUtility.isUniqueViolation(first));
  }

  @Test
  void doesNotTreatOtherOracleErrorsAsDuplicates() {
    assertFalse(
        OracleErrorUtility.isUniqueViolation(new SQLException("missing table", "42000", 942)));
  }
}
