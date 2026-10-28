package com.medigestsystem.auth;

/** Política de contraseñas: 8 a 72 caracteres (límite de BCrypt) con al menos una letra y un número. */
public final class PoliticaPassword {

    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String MENSAJE =
            "La contraseña debe tener entre 8 y 72 caracteres e incluir al menos una letra y un número.";

    private PoliticaPassword() {
    }

    public static boolean esValida(String password) {
        return password != null && password.matches(REGEX);
    }
}
