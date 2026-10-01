package com.cnrps.InteropPlatformeEchangeCI.utilities;

import static org.junit.jupiter.api.Assertions.*;

import com.cnrps.InteropPlatformeEchangeCI.models.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FileNameUtilityTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "../f.txt",
        "..\\f.txt",
        "/etc/f.txt",
        "C:\\f.txt",
        "f;rm.txt",
        "f\nf.txt",
        "f..txt",
        "paie.csv",
        "-paie.txt",
        "paie a.txt"
      })
  void rejectsUnsafeNames(String name) {
    assertThrows(EpicCiException.class, () -> FileNameUtility.validateOriginal(name));
  }

  @Test
  void acceptsSafeAsciiName() {
    assertEquals(
        "fichier_EPIC-2026.txt", FileNameUtility.validateOriginal("fichier_EPIC-2026.txt"));
  }

  @Test
  void rejectsNull() {
    assertThrows(EpicCiException.class, () -> FileNameUtility.validateOriginal(null));
  }

  @Test
  void generatedNamesAreUniqueAndSafe() {
    EpicCiUploadRequest r = new EpicCiUploadRequest();
    r.setCodeEtab(3012);
    r.setTypeSalaire(1);
    r.setAnnee(2026);
    r.setPeriode(13);
    String a = FileNameUtility.remoteName(r), b = FileNameUtility.remoteName(r);
    assertNotEquals(a, b);
    assertEquals(a, FileNameUtility.validateOriginal(a));
  }
}
