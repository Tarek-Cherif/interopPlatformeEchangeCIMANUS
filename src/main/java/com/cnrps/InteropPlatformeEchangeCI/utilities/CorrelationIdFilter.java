package com.cnrps.InteropPlatformeEchangeCI.utilities;

import java.io.IOException;
import java.util.UUID;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String id = request.getHeader("X-Correlation-ID");
    // Alphabet limité: empêche l'injection de lignes dans les logs via un en-tête HTTP.
    if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}")) id = UUID.randomUUID().toString();
    MDC.put("correlationId", id);
    response.setHeader("X-Correlation-ID", id);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove("correlationId");
    }
  }
}
