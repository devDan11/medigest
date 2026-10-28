package com.medigestsystem.auth;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medigestsystem.auth.dto.CambiarPasswordRequest;
import com.medigestsystem.auth.dto.ForgotPasswordRequest;
import com.medigestsystem.auth.dto.LoginRequest;
import com.medigestsystem.auth.dto.RefreshRequest;
import com.medigestsystem.auth.dto.RegistroPacienteRequest;
import com.medigestsystem.auth.dto.ResetPasswordRequest;
import com.medigestsystem.auth.dto.TokenResponse;
import com.medigestsystem.security.SecurityUtils;
import com.medigestsystem.shared.dto.MensajeResponse;
import com.medigestsystem.shared.exception.CredencialesInvalidasException;
import com.medigestsystem.usuarios.dto.UsuarioResponse;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Login, registro, refresco de token y recuperación de contraseña")
public class AuthApiController {

    private final AuthService authService;
    private final RecuperacionService recuperacionService;

    public AuthApiController(AuthService authService, RecuperacionService recuperacionService) {
        this.authService = authService;
        this.recuperacionService = recuperacionService;
    }

    @PostMapping("/login")
    @Operation(summary = "Inicia sesión y devuelve tokens JWT",
            description = "Tras 5 intentos fallidos consecutivos la cuenta se bloquea 15 minutos.")
    public TokenResponse login(@Valid @RequestBody LoginRequest solicitud) {
        return authService.login(solicitud);
    }

    @PostMapping("/register")
    @Operation(summary = "Registra un paciente (cuenta + ficha)")
    public ResponseEntity<UsuarioResponse> registrar(@Valid @RequestBody RegistroPacienteRequest solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registrarPaciente(solicitud));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Canjea un token de refresco por un nuevo par de tokens")
    public TokenResponse refrescar(@Valid @RequestBody RefreshRequest solicitud) {
        return authService.refrescar(solicitud.refreshToken());
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Solicita un enlace de recuperación",
            description = "La respuesta es la misma exista o no el correo, para no revelar cuentas.")
    public ResponseEntity<MensajeResponse> olvideMiPassword(@Valid @RequestBody ForgotPasswordRequest solicitud) {
        recuperacionService.solicitar(solicitud.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new MensajeResponse(
                "Si el correo está registrado, recibirá un enlace para restablecer su contraseña."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Restablece la contraseña con el token recibido")
    public MensajeResponse restablecer(@Valid @RequestBody ResetPasswordRequest solicitud) {
        recuperacionService.restablecer(solicitud.token(), solicitud.nuevaPassword());
        return new MensajeResponse("Contraseña actualizada. Ya puede iniciar sesión.");
    }

    @GetMapping("/me")
    @Operation(summary = "Datos de la cuenta autenticada")
    @SecurityRequirement(name = "bearerAuth")
    public UsuarioResponse perfil() {
        return authService.perfil(usuarioActual());
    }

    @PostMapping("/cambiar-password")
    @Operation(summary = "Cambia la contraseña de la cuenta autenticada",
            description = "Invalida los tokens de refresco emitidos antes del cambio.")
    @SecurityRequirement(name = "bearerAuth")
    public MensajeResponse cambiarPassword(@Valid @RequestBody CambiarPasswordRequest solicitud) {
        authService.cambiarPassword(usuarioActual(), solicitud);
        return new MensajeResponse("Contraseña actualizada.");
    }

    private Long usuarioActual() {
        return SecurityUtils.usuarioIdActual()
                .orElseThrow(() -> new CredencialesInvalidasException("NO_AUTENTICADO", "Se requiere autenticación."));
    }
}
