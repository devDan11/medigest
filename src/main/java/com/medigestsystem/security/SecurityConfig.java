package com.medigestsystem.security;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutHandler;

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.auditoria.ResultadoAuditoria;
import com.medigestsystem.config.MediGestProperties;
import com.medigestsystem.usuarios.NombreRol;

/**
 * Dos cadenas de seguridad independientes para evitar conflictos entre sesión y JWT:
 * <ol>
 *   <li>{@code /api/**}: stateless, sin CSRF, autenticación por token Bearer (JWT).</li>
 *   <li>Resto (web Thymeleaf): sesión HTTP segura con formulario de login y protección CSRF.</li>
 * </ol>
 * La autorización fina se aplica en el backend con {@code @PreAuthorize} en cada endpoint.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] API_PUBLICA_POST = {
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password"
    };

    private static final String[] WEB_PUBLICA = {
            "/login", "/registro", "/recuperar", "/restablecer", "/error",
            "/css/**", "/js/**", "/img/**", "/vendor/**", "/favicon.ico",
            "/actuator/health", "/actuator/info"
    };

    /** CSP del sitio web: solo recursos propios. Los estilos en línea se permiten porque FullCalendar y Chart.js los inyectan. */
    private static final String CSP_WEB = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; "
            + "form-action 'self'; frame-ancestors 'none'";
    /** CSP de la API: devuelve solo JSON, no debe cargar nada. */
    private static final String CSP_API = "default-src 'none'; frame-ancestors 'none'";
    private static final String PERMISSIONS_POLICY = "geolocation=(), camera=(), microphone=(), payment=()";

    private static Customizer<HeadersConfigurer<HttpSecurity>> cabeceras(String csp) {
        return headers -> headers
                .addHeaderWriter(new StaticHeadersWriter("Content-Security-Policy", csp))
                .addHeaderWriter(new StaticHeadersWriter("Referrer-Policy", "strict-origin-when-cross-origin"))
                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", PERMISSIONS_POLICY));
    }

    private static final String[] DOCUMENTACION_API = {
            "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
    };

    /** Administrador de autenticación único (correo + contraseña BCrypt) con eventos para el control de intentos. */
    @Bean
    public AuthenticationManager authenticationManager(UsuarioDetailsService usuarioDetailsService,
                                                       PasswordEncoder passwordEncoder,
                                                       ApplicationEventPublisher publisher) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(usuarioDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        ProviderManager manager = new ProviderManager(provider);
        manager.setAuthenticationEventPublisher(new DefaultAuthenticationEventPublisher(publisher));
        return manager;
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtService jwtService) {
        return jwtService.decodificadorAcceso();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                              ApiSecurityHandlers handlers) throws Exception {
        http
                .securityMatcher("/api/**")
                .headers(cabeceras(CSP_API))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, API_PUBLICA_POST).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/system/ping").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(convertidorJwt()))
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http, AuthenticationManager authenticationManager,
                                              MediGestProperties properties,
                                              AuditoriaService auditoriaService) throws Exception {
        boolean swaggerPublico = properties.seguridad().swaggerPublico();
        http
                .authenticationManager(authenticationManager)
                .headers(cabeceras(CSP_WEB))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(WEB_PUBLICA).permitAll();
                    if (swaggerPublico) {
                        auth.requestMatchers(DOCUMENTACION_API).permitAll();
                    } else {
                        auth.requestMatchers(DOCUMENTACION_API).hasRole(NombreRol.ADMINISTRADOR.name());
                    }
                    auth.requestMatchers("/admin/**").hasRole(NombreRol.ADMINISTRADOR.name())
                            .requestMatchers("/medico/**").hasRole(NombreRol.MEDICO.name())
                            .requestMatchers("/recepcion/**").hasRole(NombreRol.RECEPCIONISTA.name())
                            .requestMatchers("/paciente/**").hasRole(NombreRol.PACIENTE.name())
                            .anyRequest().authenticated();
                })
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/inicio", true)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .addLogoutHandler(auditarCierreSesion(auditoriaService)));
        return http.build();
    }

    private LogoutHandler auditarCierreSesion(AuditoriaService auditoriaService) {
        return (request, response, authentication) -> {
            if (authentication != null && authentication.getPrincipal() instanceof UsuarioPrincipal principal) {
                auditoriaService.registrarComo(principal.getId(), principal.getUsername(), "LOGOUT", "usuarios",
                        String.valueOf(principal.getId()), ResultadoAuditoria.EXITO, null);
            }
        };
    }

    private JwtAuthenticationConverter convertidorJwt() {
        JwtGrantedAuthoritiesConverter autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthoritiesClaimName(JwtService.CLAIM_ROLES);
        autoridades.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(autoridades);
        return convertidor;
    }
}
