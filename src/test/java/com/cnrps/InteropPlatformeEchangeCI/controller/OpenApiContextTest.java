package com.cnrps.InteropPlatformeEchangeCI.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cnrps.InteropPlatformeEchangeCI.config.OpenApiConfig;
import com.cnrps.InteropPlatformeEchangeCI.services.EpicCiService;
import com.cnrps.InteropPlatformeEchangeCI.utilities.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

// Vérifie réellement le démarrage du contexte web et la génération Springdoc, sans serveur externe.
@SpringBootTest(classes = OpenApiContextTest.TestWebApplication.class)
@AutoConfigureMockMvc
class OpenApiContextTest {
  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
  @Import({
    EpicCiController.class,
    GlobalExceptionHandler.class,
    OpenApiConfig.class,
    CorrelationIdFilter.class
  })
  static class TestWebApplication {}

  @MockBean EpicCiService service;
  @Autowired MockMvc mvc;

  @Test
  void openApiDescribesMultipartAndAllMetadata() throws Exception {
    String path = "$.paths['/api/epic-ci/chargerFichierEpic_CI'].post";
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath(path + ".operationId").value("chargerFichierEpicCI"))
        .andExpect(jsonPath(path + ".requestBody.content['multipart/form-data']").exists())
        .andExpect(
            jsonPath("$.components.schemas.EpicCiUploadRequest.properties.fichier.format")
                .value("binary"))
        .andExpect(
            jsonPath("$.components.schemas.EpicCiUploadRequest.properties.periode.maximum")
                .value(99))
        .andExpect(jsonPath("$.components.schemas.EpicCiUploadRequest.properties.annee").exists())
        .andExpect(
            jsonPath("$.components.schemas.EpicCiUploadRequest.properties.typeSalaire").exists())
        .andExpect(
            jsonPath("$.components.schemas.EpicCiUploadRequest.properties.codeEtab").exists())
        .andExpect(
            jsonPath("$.components.schemas.EpicCiUploadRequest.properties.matriculeUser").exists())
        .andExpect(
            jsonPath("$.components.schemas.EpicCiUploadRequest.properties.direction").exists())
        .andExpect(jsonPath(path + ".responses['409']").exists())
        .andExpect(jsonPath(path + ".responses['502']").exists());
  }
}
