package com.cnrps.InteropPlatformeEchangeCI.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cnrps.InteropPlatformeEchangeCI.models.*;
import com.cnrps.InteropPlatformeEchangeCI.services.*;
import com.cnrps.InteropPlatformeEchangeCI.utilities.*;
import java.time.*;
import javax.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EpicCiControllerTest {
  private static final String URL = "/api/epic-ci/chargerFichierEpic_CI";
  private EpicCiRepository repository;
  private FileTransferUtility transfer;
  private MockMvc mvc;
  private ValidatorFactory factory;

  @BeforeEach
  void setup() {
    repository = mock(EpicCiRepository.class);
    transfer = mock(FileTransferUtility.class);
    factory = Validation.buildDefaultValidatorFactory();
    EpicCiService service =
        new EpicCiServiceImpl(
            repository,
            transfer,
            factory.getValidator(),
            Clock.fixed(Instant.parse("2026-10-01T11:00:00Z"), ZoneId.of("Africa/Tunis")),
            "dd/MM/yyyy HH:mm:ss");
    mvc =
        MockMvcBuilders.standaloneSetup(new EpicCiController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new CorrelationIdFilter())
            .build();
  }

  @AfterEach
  void close() {
    factory.close();
  }

  private MockHttpServletRequestBuilder upload(
      String period, String establishment, String name, byte[] bytes) {
    return multipart(URL)
        .file(new MockMultipartFile("fichier", name, "text/plain", bytes))
        .param("periode", period)
        .param("annee", "2026")
        .param("typeSalaire", "1")
        .param("codeEtab", establishment)
        .param("matriculeUser", "12345")
        .param("direction", "5600");
  }

  private MockHttpServletRequestBuilder valid() {
    return upload("9", "3012", "paie.txt", new byte[] {65});
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "100", "-1", "", "1.5", "+9"})
  void invalidPeriodIs400BeforeOracle(String period) throws Exception {
    mvc.perform(upload(period, "3012", "paie.txt", new byte[] {65}))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
    verifyNoInteractions(repository, transfer);
  }

  @ParameterizedTest
  @ValueSource(strings = {"10000", "00001", "-1", "1e3"})
  void establishmentLengthAndFormatAreCheckedBeforeConversion(String code) throws Exception {
    mvc.perform(upload("9", code, "paie.txt", new byte[] {65})).andExpect(status().isBadRequest());
    verifyNoInteractions(repository, transfer);
  }

  @Test
  void missingFileIs400() throws Exception {
    mvc.perform(
            multipart(URL)
                .param("periode", "9")
                .param("annee", "2026")
                .param("typeSalaire", "1")
                .param("codeEtab", "3012")
                .param("matriculeUser", "12345")
                .param("direction", "5600"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(repository, transfer);
  }

  @Test
  void missingMetadataIs400() throws Exception {
    mvc.perform(
            multipart(URL)
                .file(new MockMultipartFile("fichier", "paie.txt", "text/plain", new byte[] {65})))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(repository, transfer);
  }

  @Test
  void emptyFileIs400() throws Exception {
    mvc.perform(upload("9", "3012", "paie.txt", new byte[0]))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("EMPTY_FILE"));
    verifyNoInteractions(repository, transfer);
  }

  @Test
  void duplicateIs409AndNoTransferOrProcedure() throws Exception {
    when(repository.existsEpicFile(3012, 1, 2026, 9)).thenReturn(true);
    mvc.perform(valid())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("FILE_ALREADY_EXISTS"))
        .andExpect(jsonPath("$.details.codeEtab").value(3012));
    verifyNoInteractions(transfer);
    verify(repository, never()).traitementFichierEpic(anyString(), any(), anyString());
  }

  @Test
  void successIs200AndCorrelationHeaderPropagates() throws Exception {
    mvc.perform(valid().header("X-Correlation-ID", "test-correlation-123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.originalFileName").value("paie.txt"))
        .andExpect(header().string("X-Correlation-ID", "test-correlation-123"));
    assertNull(org.slf4j.MDC.get("correlationId"));
  }

  @Test
  void period13IsAccepted() throws Exception {
    mvc.perform(upload("13", "3012", "paie.txt", new byte[] {65})).andExpect(status().isOk());
  }

  @Test
  void scpFailureIs502AndProcedureNotCalled() throws Exception {
    doThrow(new FileTransferException("network", new Exception()))
        .when(transfer)
        .transfer(any(), anyString());
    mvc.perform(valid())
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("SCP_TRANSFER_FAILED"));
    verify(repository, never()).traitementFichierEpic(anyString(), any(), anyString());
  }

  @Test
  void oracleProcedureFailureIs500AndNoRawErrorLeaks() throws Exception {
    doThrow(new DataAccessResourceFailureException("PASSWORD SQL DETAIL"))
        .when(repository)
        .traitementFichierEpic(anyString(), any(), anyString());
    mvc.perform(valid())
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("ORACLE_PROCESSING_FAILED"))
        .andExpect(jsonPath("$.details.fileName").exists());
  }

  @Test
  void oracleExistenceFailureIs500AndNoTransfer() throws Exception {
    when(repository.existsEpicFile(3012, 1, 2026, 9))
        .thenThrow(new DataAccessResourceFailureException("network"));
    mvc.perform(valid())
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("ORACLE_ERROR"));
    verifyNoInteractions(transfer);
  }

  @Test
  void wrongContentTypeIs415() throws Exception {
    mvc.perform(post(URL).contentType("application/json").content("{}"))
        .andExpect(status().isUnsupportedMediaType());
    verifyNoInteractions(repository, transfer);
  }
}
