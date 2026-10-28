package com.medigestsystem.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String ESQUEMA_BEARER = "bearerAuth";

    @Bean
    public OpenAPI medigestOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MediGest API")
                        .version("v1")
                        .description("API REST del Sistema Integral de Gestión de Citas Médicas. "
                                + "Para probar endpoints protegidos: POST /api/v1/auth/login, copie el accessToken "
                                + "y péguelo en el botón Authorize."))
                .components(new Components().addSecuritySchemes(ESQUEMA_BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
