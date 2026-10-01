package com.cnrps.InteropPlatformeEchangeCI.utilities;

import java.beans.PropertyEditorSupport;

public class NumericPropertyEditor extends PropertyEditorSupport {
  private final String pattern;
  private final boolean longValue;

  public NumericPropertyEditor(String pattern, boolean longValue) {
    this.pattern = pattern;
    this.longValue = longValue;
  }

  @Override
  public void setAsText(String text) {
    // Contrôle avant conversion: "00001" ne doit pas devenir un codeEtab valide à un chiffre.
    if (text == null || !text.matches(pattern))
      throw new IllegalArgumentException("Format numérique invalide");
    if (longValue) setValue(Long.valueOf(text));
    else setValue(Integer.valueOf(text));
  }
}
