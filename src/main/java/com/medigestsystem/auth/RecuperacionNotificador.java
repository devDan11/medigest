package com.medigestsystem.auth;

import com.medigestsystem.usuarios.Usuario;

/** Canal por el que se entrega al usuario el enlace de recuperación de contraseña. */
public interface RecuperacionNotificador {

    void enviarEnlace(Usuario usuario, String enlace, int minutosVigencia);
}
