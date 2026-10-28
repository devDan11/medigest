package com.medigestsystem.notificaciones;

/** Canal de salida de correo. Lanza una excepción no verificada si el envío falla. */
public interface CanalCorreo {

    void enviar(String destinatario, String asunto, String cuerpo);
}
