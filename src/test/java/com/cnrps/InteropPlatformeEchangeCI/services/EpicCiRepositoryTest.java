package com.cnrps.InteropPlatformeEchangeCI.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cnrps.InteropPlatformeEchangeCI.models.EpicCiUploadRequest;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;

class EpicCiRepositoryTest {
  @Test
  void existenceQueryBindsExactPrimaryKeyInCorrectOrder() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    PreparedStatement ps = mock(PreparedStatement.class);
    ResultSet rs = mock(ResultSet.class);
    when(rs.next()).thenReturn(true);
    doAnswer(
            inv -> {
              String sql = inv.getArgument(0);
              assertEquals(
                  "SELECT 1 FROM EPIC_FM WHERE MATRICULE = ? AND CODE_TYPE_SALAIRE = ? AND ANNEE = ? AND PERIODE = ?",
                  sql);
              ((PreparedStatementSetter) inv.getArgument(1)).setValues(ps);
              return ((ResultSetExtractor<?>) inv.getArgument(2)).extractData(rs);
            })
        .when(jdbc)
        .query(anyString(), any(PreparedStatementSetter.class), any(ResultSetExtractor.class));
    assertTrue(new EpicCiRepository(jdbc).existsEpicFile(3012, 1, 2026, 13));
    verify(ps).setInt(1, 3012);
    verify(ps).setInt(2, 1);
    verify(ps).setInt(3, 2026);
    verify(ps).setInt(4, 13);
  }

  @Test
  void procedureBindsAllNineParametersAndClosesStatement() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    Connection connection = mock(Connection.class);
    CallableStatement statement = mock(CallableStatement.class);
    when(jdbc.getQueryTimeout()).thenReturn(300);
    when(connection.prepareCall("{call traitement_fichier_epic(?, ?, ?, ?, ?, ?, ?, ?, ?)}"))
        .thenReturn(statement);
    doAnswer(inv -> ((ConnectionCallback<?>) inv.getArgument(0)).doInConnection(connection))
        .when(jdbc)
        .execute(any(ConnectionCallback.class));
    EpicCiUploadRequest r = new EpicCiUploadRequest();
    r.setCodeEtab(3012);
    r.setPeriode(13);
    r.setAnnee(2026);
    r.setTypeSalaire(1);
    r.setMatriculeUser(9999999999L);
    r.setDirection(5600);
    new EpicCiRepository(jdbc).traitementFichierEpic("epic_test.txt", r, "01/10/2026 12:00:00");
    verify(statement).setString(1, "epic_test.txt");
    verify(statement).setInt(2, 3012);
    verify(statement).setInt(3, 13);
    verify(statement).setInt(4, 2026);
    verify(statement).setInt(5, 1);
    verify(statement).setLong(6, 9999999999L);
    verify(statement).setString(7, "C");
    verify(statement).setString(8, "01/10/2026 12:00:00");
    verify(statement).setInt(9, 5600);
    verify(statement).setQueryTimeout(300);
    verify(statement).execute();
    verify(statement).close();
  }

  @Test
  void statementClosesEvenWhenOracleThrows() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    Connection c = mock(Connection.class);
    CallableStatement st = mock(CallableStatement.class);
    when(c.prepareCall(anyString())).thenReturn(st);
    when(st.execute()).thenThrow(new SQLException("ORA-00001", "23000", 1));
    doAnswer(inv -> ((ConnectionCallback<?>) inv.getArgument(0)).doInConnection(c))
        .when(jdbc)
        .execute(any(ConnectionCallback.class));
    EpicCiUploadRequest r = new EpicCiUploadRequest();
    r.setCodeEtab(1);
    r.setPeriode(1);
    r.setAnnee(2026);
    r.setTypeSalaire(1);
    r.setMatriculeUser(1L);
    r.setDirection(1);
    assertThrows(
        SQLException.class,
        () -> new EpicCiRepository(jdbc).traitementFichierEpic("test.txt", r, "01/10/2026"));
    verify(st).close();
  }
}
