package com.medigestsystem.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Traduce los eventos de autenticación de Spring Security en control de intentos y auditoría.
 * Un fallo al registrar nunca debe impedir ni alterar el resultado del login.
 */
@Component
public class LoginEventListener {

    private static final Logger log = LoggerFactory.getLogger(LoginEventListener.class);

    private final IntentosLoginService intentosLoginService;

    public LoginEventListener(IntentosLoginService intentosLoginService) {
        this.intentosLoginService = intentosLoginService;
    }

    @EventListener
    public void alExito(AuthenticationSuccessEvent evento) {
        if (evento.getAuthentication() instanceof UsernamePasswordAuthenticationToken) {
            ejecutar(() -> intentosLoginService.registrarExito(evento.getAuthentication().getName()));
        }
    }

    @EventListener
    public void alFalloCredenciales(AuthenticationFailureBadCredentialsEvent evento) {
        ejecutar(() -> intentosLoginService.registrarFallo(evento.getAuthentication().getName()));
    }

    @EventListener
    public void alCuentaBloqueada(AuthenticationFailureLockedEvent evento) {
        ejecutar(() -> intentosLoginService.registrarRechazo(
                evento.getAuthentication().getName(), "Cuenta bloqueada temporalmente"));
    }

    @EventListener
    public void alCuentaDeshabilitada(AuthenticationFailureDisabledEvent evento) {
        ejecutar(() -> intentosLoginService.registrarRechazo(
                evento.getAuthentication().getName(), "Cuenta desactivada"));
    }

    private void ejecutar(Runnable accion) {
        try {
            accion.run();
        } catch (RuntimeException e) {
            log.error("No se pudo registrar el evento de autenticación", e);
        }
    }
}
