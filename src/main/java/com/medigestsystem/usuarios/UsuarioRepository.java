package com.medigestsystem.usuarios;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Page<Usuario> findByEmailContainingIgnoreCase(String texto, Pageable pageable);

    long countByRolesNombreAndActivoTrue(NombreRol nombre);
}
