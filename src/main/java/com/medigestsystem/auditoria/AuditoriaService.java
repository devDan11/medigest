package com.medigestsystem.auditoria;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.medigestsystem.security.SecurityUtils;

/**
 * Registro de auditoría. Cada evento se guarda en una transacción propia (REQUIRES_NEW):
 * un intento fallido queda registrado aunque la operación principal haga rollback.
 * Nunca debe recibir contraseñas, tokens ni datos clínicos en {@code detalle}.
 */
@Service
public class AuditoriaService {

    private final AuditoriaRepository auditoriaRepository;

    public AuditoriaService(AuditoriaRepository auditoriaRepository) {
        this.auditoriaRepository = auditoriaRepository;
    }

    /** Registra un evento atribuido al usuario autenticado en el contexto actual (si lo hay). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String accion, String entidad, String recursoId,
                          ResultadoAuditoria resultado, String detalle) {
        guardar(SecurityUtils.usuarioIdActual().orElse(null), SecurityUtils.emailActual().orElse(null),
                accion, entidad, recursoId, resultado, detalle);
    }

    /** Registra un evento indicando explícitamente al actor (p. ej. un intento de login fallido). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarComo(Long usuarioId, String usuarioEmail, String accion, String entidad,
                              String recursoId, ResultadoAuditoria resultado, String detalle) {
        guardar(usuarioId, usuarioEmail, accion, entidad, recursoId, resultado, detalle);
    }

    private void guardar(Long usuarioId, String usuarioEmail, String accion, String entidad,
                         String recursoId, ResultadoAuditoria resultado, String detalle) {
        Auditoria a = new Auditoria();
        a.setUsuarioId(usuarioId);
        a.setUsuarioEmail(recortar(usuarioEmail, 150));
        a.setAccion(recortar(accion, 60));
        a.setEntidad(recortar(entidad, 60));
        a.setRecursoId(recortar(recursoId, 60));
        a.setResultado(resultado);
        a.setDetalle(recortar(detalle, 500));
        a.setIp(ipActual());
        auditoriaRepository.save(a);
    }

    private static String ipActual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String ip = attrs.getRequest().getRemoteAddr();
            return recortar(ip, 45);
        }
        return null;
    }

    private static String recortar(String texto, int max) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= max ? texto : texto.substring(0, max);
    }
}
