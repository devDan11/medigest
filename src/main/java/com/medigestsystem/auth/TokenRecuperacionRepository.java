package com.medigestsystem.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenRecuperacionRepository extends JpaRepository<TokenRecuperacion, Long> {

    Optional<TokenRecuperacion> findByTokenHash(String tokenHash);

    List<TokenRecuperacion> findByUsuarioIdAndUsadoEnIsNull(Long usuarioId);

    boolean existsByUsuarioIdAndCreadoEnAfter(Long usuarioId, Instant desde);
}
