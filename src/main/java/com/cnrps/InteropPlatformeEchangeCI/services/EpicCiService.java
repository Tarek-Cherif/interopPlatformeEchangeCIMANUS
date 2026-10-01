package com.cnrps.InteropPlatformeEchangeCI.services;

import com.cnrps.InteropPlatformeEchangeCI.models.EpicCiResponse;
import com.cnrps.InteropPlatformeEchangeCI.models.EpicCiUploadRequest;

public interface EpicCiService {
  EpicCiResponse chargerFichierEpicCI(EpicCiUploadRequest request);
}
