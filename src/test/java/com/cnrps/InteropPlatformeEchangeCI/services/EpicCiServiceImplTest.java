package com.cnrps.InteropPlatformeEchangeCI.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cnrps.InteropPlatformeEchangeCI.models.*;
import com.cnrps.InteropPlatformeEchangeCI.utilities.*;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.validation.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.*;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

class EpicCiServiceImplTest {
  private EpicCiRepository repository;
  private FileTransferUtility transfer;
  private EpicCiServiceImpl service;
  private ValidatorFactory factory;

  @BeforeEach
  void setup() {
    repository = mock(EpicCiRepository.class);
    transfer = mock(FileTransferUtility.class);
    factory = Validation.buildDefaultValidatorFactory();
    Clock clock = Clock.fixed(Instant.parse("2026-10-01T11:00:00Z"), ZoneId.of("Africa/Tunis"));
    service =
        new EpicCiServiceImpl(
            repository, transfer, factory.getValidator(), clock, "dd/MM/yyyy HH:mm:ss");
  }

  @AfterEach
  void close() {
    factory.close();
  }

  private EpicCiUploadRequest request() {
    EpicCiUploadRequest r = new EpicCiUploadRequest();
    r.setFichier(new MockMultipartFile("fichier", "paie.txt", "text/plain", new byte[] {65}));
    r.setCodeEtab(3012);
    r.setTypeSalaire(1);
    r.setAnnee(2026);
    r.setPeriode(9);
    r.setMatriculeUser(12345L);
    r.setDirection(5600);
    return r;
  }

  @Test
  void invalidParametersDoNotAccessOracleOrScp() {
    EpicCiUploadRequest r = request();
    r.setCodeEtab(10000);
    EpicCiException e = assertThrows(EpicCiException.class, () -> service.chargerFichierEpicCI(r));
    assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    verifyNoInteractions(repository, transfer);
  }

  @Test
  void emptyFileStopsBeforeOracle() {
    EpicCiUploadRequest r = request();
    r.setFichier(new MockMultipartFile("fichier", "paie.txt", "text/plain", new byte[0]));
    assertEquals(
        "EMPTY_FILE",
        assertThrows(EpicCiException.class, () -> service.chargerFichierEpicCI(r)).getCode());
    verifyNoInteractions(repository, transfer);
  }

  @Test
  void maliciousFilenameStopsBeforeOracle() {
    EpicCiUploadRequest r = request();
    r.setFichier(new MockMultipartFile("fichier", "../paie.txt", "text/plain", new byte[] {65}));
    assertEquals(
        "INVALID_FILE_NAME",
        assertThrows(EpicCiException.class, () -> service.chargerFichierEpicCI(r)).getCode());
    verifyNoInteractions(repository, transfer);
  }

  @Test
  void existingEpicReturns409WithoutScpOrProcedure() {
    when(repository.existsEpicFile(3012, 1, 2026, 9)).thenReturn(true);
    EpicCiException e =
        assertThrows(EpicCiException.class, () -> service.chargerFichierEpicCI(request()));
    assertEquals(HttpStatus.CONFLICT, e.getStatus());
    assertEquals("FILE_ALREADY_EXISTS", e.getCode());
    assertEquals(3012, e.getDetails().get("codeEtab"));
    verifyNoInteractions(transfer);
    verify(repository, never()).traitementFichierEpic(anyString(), any(), anyString());
  }

  @Test
  void absentEpicTransfersThenCallsWithSameNameAndExplicitDate() {
    EpicCiUploadRequest r = request();
    EpicCiResponse result = service.chargerFichierEpicCI(r);
    assertTrue(result.isSuccess());
    assertEquals("FILE_LOADED", result.getCode());
    assertEquals("paie.txt", result.getOriginalFileName());
    InOrder order = inOrder(repository, transfer);
    order.verify(repository).existsEpicFile(3012, 1, 2026, 9);
    order.verify(transfer).transfer(r.getFichier(), result.getFileName());
    order.verify(repository).traitementFichierEpic(result.getFileName(), r, "01/10/2026 12:00:00");
    order.verifyNoMoreInteractions();
  }

  @Test
  void period13AndTenDigitUserAreAccepted() {
    EpicCiUploadRequest r = request();
    r.setPeriode(13);
    r.setMatriculeUser(9999999999L);
    assertTrue(service.chargerFichierEpicCI(r).isSuccess());
    verify(repository).existsEpicFile(3012, 1, 2026, 13);
  }

  @Test
  void scpFailurePreventsProcedure() {
    doThrow(new FileTransferException("failed", new Exception("network")))
        .when(transfer)
        .transfer(any(), anyString());
    EpicCiException e =
        assertThrows(EpicCiException.class, () -> service.chargerFichierEpicCI(request()));
    assertEquals(HttpStatus.BAD_GATEWAY, e.getStatus());
    verify(repository, never()).traitementFichierEpic(anyString(), any(), anyString());
  }

  @Test
  void oracleFailureKeepsRemoteFilenameInErrorForRecovery() {
    doThrow(new DataAccessResourceFailureException("sensitive SQL"))
        .when(repository)
        .traitementFichierEpic(anyString(), any(), anyString());
    EpicCiException e =
        assertThrows(EpicCiException.class, () -> service.chargerFichierEpicCI(request()));
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus());
    assertEquals("ORACLE_PROCESSING_FAILED", e.getCode());
    assertNotNull(e.getDetails().get("fileName"));
    assertFalse(e.getMessage().contains("sensitive SQL"));
    verify(transfer).transfer(any(), anyString());
  }

  @Test
  void uniqueViolationWithEpicPresentIs409() {
    when(repository.existsEpicFile(3012, 1, 2026, 9)).thenReturn(false, true);
    doThrow(new DuplicateKeyException("ORA-00001"))
        .when(repository)
        .traitementFichierEpic(anyString(), any(), anyString());
    assertEquals(
        HttpStatus.CONFLICT,
        assertThrows(EpicCiException.class, () -> service.chargerFichierEpicCI(request()))
            .getStatus());
  }

  @Test
  void uniqueViolationWithoutEpicIsNotMislabelledAsEpicConflict() {
    doThrow(new DuplicateKeyException("OTHER_PK"))
        .when(repository)
        .traitementFichierEpic(anyString(), any(), anyString());
    assertEquals(
        HttpStatus.INTERNAL_SERVER_ERROR,
        assertThrows(EpicCiException.class, () -> service.chargerFichierEpicCI(request()))
            .getStatus());
  }

  @Test
  void concurrentCallsRelyOnOracleUniquenessAndNeverOverwriteFiles() throws Exception {
    CountDownLatch initialChecks = new CountDownLatch(2);
    AtomicBoolean committed = new AtomicBoolean(false);
    when(repository.existsEpicFile(3012, 1, 2026, 9))
        .thenAnswer(
            invocation -> {
              if (initialChecks.getCount() > 0) {
                initialChecks.countDown();
                if (!initialChecks.await(5, TimeUnit.SECONDS))
                  throw new AssertionError("Second appel absent");
                return false;
              }
              return committed.get();
            });
    doAnswer(
            invocation -> {
              if (!committed.compareAndSet(false, true)) {
                throw new DataIntegrityViolationException(
                    "ORA-00001", new SQLException("PK_EPIC_FM", "23000", 1));
              }
              return null;
            })
        .when(repository)
        .traitementFichierEpic(anyString(), any(), anyString());
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Callable<Integer> call =
          () -> {
            try {
              service.chargerFichierEpicCI(request());
              return 200;
            } catch (EpicCiException e) {
              return e.getStatus().value();
            }
          };
      Future<Integer> first = pool.submit(call);
      Future<Integer> second = pool.submit(call);
      List<Integer> codes =
          Arrays.asList(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
      Collections.sort(codes);
      assertEquals(Arrays.asList(200, 409), codes);
      ArgumentCaptor<String> names = ArgumentCaptor.forClass(String.class);
      verify(transfer, times(2)).transfer(any(), names.capture());
      assertNotEquals(names.getAllValues().get(0), names.getAllValues().get(1));
    } finally {
      pool.shutdownNow();
    }
  }
}
