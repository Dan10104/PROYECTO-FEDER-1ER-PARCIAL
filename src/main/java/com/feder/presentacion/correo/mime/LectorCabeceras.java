package com.feder.presentacion.correo.mime;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Extrae el bloque de cabeceras (hasta la primera línea vacía) y lo despliega en pares nombre-valor.
 */
final class LectorCabeceras {

    /** Cabecera ya desplegada: el valor no contiene CRLF. */
    record Cabecera(String nombre, String valor) {
    }

    private LectorCabeceras() {
    }

    static List<Cabecera> leer(byte[] crudo, int maximoBytes, int maximoCabeceras) throws MensajeInvalidoException {
        int fin = finDeCabeceras(crudo, maximoBytes);
        String texto = decodificar(crudo, fin);
        List<Cabecera> cabeceras = new ArrayList<>();
        String nombre = null;
        StringBuilder valor = new StringBuilder();
        for (String linea : texto.split("\r?\n", -1)) {
            String sinRetorno = linea.endsWith("\r") ? linea.substring(0, linea.length() - 1) : linea;
            if (!sinRetorno.isEmpty() && (sinRetorno.charAt(0) == ' ' || sinRetorno.charAt(0) == '\t')) {
                // Línea de continuación (RFC 5322, 2.2.3): desplegar es quitar el CRLF y conservar el espacio.
                if (nombre != null) {
                    valor.append(sinRetorno);
                }
                continue;
            }
            agregar(cabeceras, nombre, valor, maximoCabeceras);
            nombre = null;
            valor.setLength(0);
            int dosPuntos = sinRetorno.indexOf(':');
            if (dosPuntos <= 0) {
                // Líneas sin ':' (p. ej. el "From " de un buzón mbox) no son cabeceras: se descartan.
                continue;
            }
            String candidato = sinRetorno.substring(0, dosPuntos).stripTrailing();
            if (esNombreValido(candidato)) {
                nombre = candidato;
                valor.append(sinRetorno, dosPuntos + 1, sinRetorno.length());
            }
        }
        agregar(cabeceras, nombre, valor, maximoCabeceras);
        return cabeceras;
    }

    private static void agregar(List<Cabecera> cabeceras, String nombre, StringBuilder valor, int maximo)
            throws MensajeInvalidoException {
        if (nombre == null) {
            return;
        }
        if (cabeceras.size() >= maximo) {
            throw new MensajeInvalidoException("El mensaje tiene más de " + maximo + " cabeceras");
        }
        cabeceras.add(new Cabecera(nombre, valor.toString().strip()));
    }

    /** Posición donde terminan las cabeceras (excluye la línea vacía) o fallo si superan el máximo. */
    private static int finDeCabeceras(byte[] crudo, int maximoBytes) throws MensajeInvalidoException {
        int inicioLinea = 0;
        for (int i = 0; i < crudo.length; i++) {
            if (i >= maximoBytes) {
                throw new MensajeInvalidoException("Las cabeceras del mensaje superan " + maximoBytes + " bytes");
            }
            if (crudo[i] == '\n') {
                int largo = i - inicioLinea;
                boolean vacia = largo == 0 || (largo == 1 && crudo[inicioLinea] == '\r');
                if (vacia) {
                    return inicioLinea;
                }
                inicioLinea = i + 1;
            }
        }
        return crudo.length;
    }

    /**
     * Las cabeceras deberían ser ASCII, pero RFC 6532 permite UTF-8 crudo y hay clientes que envían
     * Latin-1. Probamos UTF-8 estricto y, si los bytes no lo son, usamos ISO-8859-1, que nunca falla.
     */
    private static String decodificar(byte[] crudo, int longitud) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(crudo, 0, longitud))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(crudo, 0, longitud, StandardCharsets.ISO_8859_1);
        }
    }

    private static boolean esNombreValido(String nombre) {
        if (nombre.isEmpty()) {
            return false;
        }
        for (int i = 0; i < nombre.length(); i++) {
            char c = nombre.charAt(i);
            if (c < 0x21 || c > 0x7E || c == ':') {
                return false;
            }
        }
        return true;
    }
}
