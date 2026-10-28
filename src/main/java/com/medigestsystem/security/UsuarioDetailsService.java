package com.medigestsystem.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medigestsystem.usuarios.Usuario;
import com.medigestsystem.usuarios.UsuarioRepository;

/** Carga el usuario por correo para el proveedor de autenticación de Spring Security. */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository, Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String email = username == null ? "" : username.trim();
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        return aPrincipal(usuario, clock.instant());
    }

    static UsuarioPrincipal aPrincipal(Usuario usuario, Instant ahora) {
        boolean noBloqueada = usuario.getBloqueadoHasta() == null || !usuario.getBloqueadoHasta().isAfter(ahora);
        List<String> roles = usuario.getRoles().stream().map(r -> r.getNombre().name()).sorted().toList();
        return new UsuarioPrincipal(usuario.getId(), usuario.getEmail(), usuario.getPasswordHash(),
                usuario.isActivo(), noBloqueada, roles);
    }
}
