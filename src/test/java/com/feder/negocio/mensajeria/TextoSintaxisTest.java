package com.feder.negocio.mensajeria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextoSintaxisTest {

    @Test
    @DisplayName("El error de sintaxis termina en punto antes de «Formato esperado»")
    void puntoFinal() {
        assertEquals("Se esperaba un parámetro entre comillas en la posición 8, pero se encontró '*'.",
                ServicioMensajeria.conPuntoFinal("Se esperaba un parámetro entre comillas en la posición 8, pero se encontró '*'"));
        assertEquals("Falta el corchete de cierre.", ServicioMensajeria.conPuntoFinal("Falta el corchete de cierre."));
        assertEquals("¿Comando vacío?", ServicioMensajeria.conPuntoFinal("¿Comando vacío?"));
    }
}
