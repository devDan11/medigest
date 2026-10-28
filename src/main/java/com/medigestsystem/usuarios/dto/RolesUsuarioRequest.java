package com.medigestsystem.usuarios.dto;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;

import com.medigestsystem.usuarios.NombreRol;

public record RolesUsuarioRequest(@NotEmpty(message = "Debe indicar al menos un rol") Set<NombreRol> roles) {
}
