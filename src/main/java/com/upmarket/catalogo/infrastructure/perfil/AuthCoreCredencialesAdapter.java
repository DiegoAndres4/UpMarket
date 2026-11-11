package com.upmarket.catalogo.infrastructure.perfil;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Elimina las credenciales en AuthCore usando el token del propio usuario
 * (endpoint idempotente DELETE /api/auth/users/me).
 */
@Component
public class AuthCoreCredencialesAdapter implements CredencialesPort {

    private static final Logger log = LoggerFactory.getLogger(AuthCoreCredencialesAdapter.class);

    private final RestClient rest;

    public AuthCoreCredencialesAdapter(RestClient.Builder builder,
                                       @Value("${authcore.url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.rest = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public void eliminarCuenta(String authorizationHeader) {
        try {
            rest.delete()
                    .uri("/api/auth/users/me")
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve()
                    .onStatus(estado -> estado.value() == 404, (peticion, respuesta) -> { })
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("AuthCore no pudo eliminar las credenciales de la cuenta", e);
            throw new IllegalStateException("No se pudo eliminar la cuenta en AuthCore; intenta de nuevo", e);
        }
    }
}
