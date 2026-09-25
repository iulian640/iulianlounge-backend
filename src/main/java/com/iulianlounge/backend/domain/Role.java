package com.iulianlounge.backend.domain;

// Se guarda por nombre (@Enumerated STRING) y viaja igual en el claim "role" del JWT.
// Añadir un valor exige también ampliar el CHECK de la columna (V3)
public enum Role {
    USER,
    ADMIN
}
