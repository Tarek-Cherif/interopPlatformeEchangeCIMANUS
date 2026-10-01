package com.cnrps.InteropPlatformeEchangeCI.utilities;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cnrps.InteropPlatformeEchangeCI.config.ScpProperties;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.xfer.LocalSourceFile;
import net.schmizz.sshj.xfer.scp.SCPFileTransfer;
import net.schmizz.sshj.xfer.scp.SCPUploadClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class ScpFileTransferUtilityTest {
  private ScpProperties properties() {
    ScpProperties p = new ScpProperties();
    p.setHost("test-host");
    p.setUsername("oracle");
    p.setPassword("test-only");
    p.setKnownHosts("test-known-hosts");
    return p;
  }

  private ScpFileTransferUtility utility(ScpProperties p, SSHClient ssh) {
    return new ScpFileTransferUtility(p) {
      @Override
      SSHClient createSshClient() {
        return ssh;
      }
    };
  }

  @Test
  void successfulScpVerifiesHostKeyUsesPrivatePermissionsAndDeletesTemporary() throws Exception {
    SSHClient ssh = mock(SSHClient.class);
    SCPFileTransfer scp = mock(SCPFileTransfer.class);
    SCPUploadClient upload = mock(SCPUploadClient.class);
    when(ssh.newSCPFileTransfer()).thenReturn(scp);
    when(scp.newSCPUploadClient()).thenReturn(upload);
    AtomicReference<Path> local = new AtomicReference<>();
    when(upload.copy(any(LocalSourceFile.class), anyString()))
        .thenAnswer(
            inv -> {
              FileSystemFile file = inv.getArgument(0);
              local.set(file.getFile().toPath());
              assertEquals("remote.txt", file.getName());
              assertEquals(0600, file.getPermissions());
              assertArrayEquals(new byte[] {65, 66}, Files.readAllBytes(local.get()));
              if (Files.getFileStore(local.get()).supportsFileAttributeView("posix")) {
                assertEquals(
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"),
                    Files.getPosixFilePermissions(local.get()));
              }
              assertEquals("/oracle/traitement/remote.txt", inv.getArgument(1));
              return 0;
            });
    utility(properties(), ssh)
        .transfer(
            new MockMultipartFile("fichier", "paie.txt", "text/plain", new byte[] {65, 66}),
            "remote.txt");
    verify(ssh).loadKnownHosts(new File("test-known-hosts"));
    verify(ssh).setConnectTimeout(30000);
    verify(ssh).setTimeout(30000);
    verify(ssh).connect("test-host", 22);
    verify(ssh).authPassword("oracle", "test-only");
    verify(ssh).close();
    assertFalse(Files.exists(local.get()));
  }

  @Test
  void nonzeroScpExitStatusIsFailure() throws Exception {
    SSHClient ssh = mock(SSHClient.class);
    SCPFileTransfer scp = mock(SCPFileTransfer.class);
    SCPUploadClient upload = mock(SCPUploadClient.class);
    when(ssh.newSCPFileTransfer()).thenReturn(scp);
    when(scp.newSCPUploadClient()).thenReturn(upload);
    when(upload.copy(any(LocalSourceFile.class), anyString())).thenReturn(1);
    assertThrows(
        FileTransferException.class,
        () ->
            utility(properties(), ssh)
                .transfer(
                    new MockMultipartFile("fichier", "paie.txt", "text/plain", new byte[] {65}),
                    "remote.txt"));
    verify(ssh).close();
  }

  @Test
  void unknownKnownHostsFilePreventsConnection() throws Exception {
    SSHClient ssh = mock(SSHClient.class);
    doThrow(new IOException("missing known_hosts")).when(ssh).loadKnownHosts(any(File.class));
    assertThrows(
        FileTransferException.class,
        () ->
            utility(properties(), ssh)
                .transfer(
                    new MockMultipartFile("fichier", "paie.txt", "text/plain", new byte[] {65}),
                    "remote.txt"));
    verify(ssh, never()).connect(anyString(), anyInt());
    verify(ssh).close();
  }

  @Test
  void sshjAndItsCryptoDependenciesInitializeUnderJava8() throws Exception {
    try (SSHClient ssh = new SSHClient()) {
      assertNotNull(ssh);
    }
  }

  @Test
  void rejectsUnsafeRemoteNameBeforeReadingFileOrConnecting() {
    MultipartFile file = mock(MultipartFile.class);
    assertThrows(
        com.cnrps.InteropPlatformeEchangeCI.models.EpicCiException.class,
        () -> new ScpFileTransferUtility(new ScpProperties()).transfer(file, "../paie.txt"));
    verifyNoInteractions(file);
  }

  @Test
  void readFailureIsWrappedAsTransferErrorBeforeConnecting() throws Exception {
    MultipartFile file = mock(MultipartFile.class);
    when(file.getInputStream()).thenThrow(new IOException("read failure"));
    FileTransferException e =
        assertThrows(
            FileTransferException.class,
            () -> new ScpFileTransferUtility(new ScpProperties()).transfer(file, "paie.txt"));
    assertTrue(e.getCause() instanceof IOException);
  }
}
