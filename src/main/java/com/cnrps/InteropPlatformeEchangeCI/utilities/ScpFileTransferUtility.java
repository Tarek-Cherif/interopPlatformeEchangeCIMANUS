package com.cnrps.InteropPlatformeEchangeCI.utilities;

import com.cnrps.InteropPlatformeEchangeCI.config.ScpProperties;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.xfer.FileSystemFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
public class ScpFileTransferUtility implements FileTransferUtility {
  private final ScpProperties properties;

  public ScpFileTransferUtility(ScpProperties properties) {
    this.properties = properties;
  }
  // Point de substitution local pour les tests: aucune session SSH réelle n'est requise.
  SSHClient createSshClient() {
    return new SSHClient();
  }

  @Override
  public void transfer(MultipartFile file, String remoteFileName) {
    FileNameUtility.validateOriginal(remoteFileName);
    Path temp = null;
    try {
      // Streaming vers un fichier temporaire privé: pas de chargement complet en mémoire.
      try {
        temp =
            Files.createTempFile(
                "epic-",
                ".txt",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
      } catch (UnsupportedOperationException windows) {
        temp = Files.createTempFile("epic-", ".txt");
      }
      // Écrire dans le temporaire existant sans le remplacer conserve les permissions 0600.
      try (InputStream input = file.getInputStream();
          OutputStream output =
              Files.newOutputStream(
                  temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
      }
      try (SSHClient ssh = createSshClient()) {
        // Vérification obligatoire contre un known_hosts approuvé par l'administrateur.
        // Aucun PromiscuousVerifier: une clé absente ou différente fait échouer le transfert.
        ssh.loadKnownHosts(new File(properties.getKnownHosts()));
        ssh.setConnectTimeout(properties.getTimeout());
        ssh.setTimeout(properties.getTimeout());
        ssh.connect(properties.getHost(), properties.getPort());
        ssh.authPassword(properties.getUsername(), properties.getPassword());
        FileSystemFile source =
            new FileSystemFile(temp.toFile()) {
              @Override
              public String getName() {
                return remoteFileName;
              }
              // Déclarations de paie: pas de lecture pour le groupe ou les autres comptes.
              @Override
              public int getPermissions() {
                return 0600;
              }
            };
        int exitStatus =
            ssh.newSCPFileTransfer()
                .newSCPUploadClient()
                .copy(source, properties.getRemoteDirectory() + "/" + remoteFileName);
        // Le raccourci upload() de SSHJ ignore le code final: le vérifier explicitement.
        if (exitStatus != 0) throw new IOException("Code de sortie SCP non nul: " + exitStatus);
      }
    } catch (Exception e) {
      throw new FileTransferException("Le transfert SCP a échoué", e);
    } finally {
      // Seul le temporaire local est supprimé; aucune suppression automatique distante.
      if (temp != null) {
        try {
          Files.deleteIfExists(temp);
        } catch (Exception ignored) {
          log.warn("Impossible de supprimer un temporaire local EPIC");
        }
      }
    }
  }
}
