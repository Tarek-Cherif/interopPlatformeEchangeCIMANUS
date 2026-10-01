package com.cnrps.InteropPlatformeEchangeCI.utilities;

import org.springframework.web.multipart.MultipartFile;

public interface FileTransferUtility {
  // Le retour normal garantit la fin du transfert; toute erreur empêche l'appel Oracle.
  void transfer(MultipartFile file, String remoteFileName);
}
