package com.feder.presentacion.correo.mime;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decodificación de "encoded-words" de RFC 2047 (=?charset?B|Q?texto?=).
 *
 * Las palabras codificadas contiguas, separadas solo por espacios, se unen sin ese espacio.
 * Además se concatenan sus bytes antes de decodificar, porque algunos clientes parten un carácter
 * UTF-8 de varios bytes entre dos palabras. Una palabra mal formada se deja tal cual.
 */
public final class DecodificadorRfc2047 {

    private static final Pattern PALABRA = Pattern.compile("=\\?([^?\\s]+)\\?([BbQq])\\?([^?\\s]*)\\?=");
    private static final Pattern SOLO_ESPACIOS = Pattern.compile("[ \\t\\r\\n]*");
    private static final Charset WINDOWS_1252 = cargarWindows1252();

    private DecodificadorRfc2047() {
    }

    public static String decodificar(String texto) {
        if (texto == null || !texto.contains("=?")) {
            return texto;
        }
        StringBuilder resultado = new StringBuilder(texto.length());
        ByteArrayOutputStream pendientes = new ByteArrayOutputStream();
        Charset charsetPendiente = null;
        int ultimoFin = 0;
        Matcher m = PALABRA.matcher(texto);
        while (m.find()) {
            String intermedio = texto.substring(ultimoFin, m.start());
            // RFC 2047 §5: una palabra codificada debe estar separada del texto vecino por espacios.
            // Si está pegada (p. ej. dentro de comillas: HELP["=?UTF-8?Q?=2A?="]) se deja literal,
            // así el comando que llega al analizador es exactamente el que escribió el usuario.
            boolean delimitada = (m.start() == 0 || esEspacio(texto.charAt(m.start() - 1)))
                    && (m.end() == texto.length() || esEspacio(texto.charAt(m.end())));
            Charset charset = charset(m.group(1));
            byte[] bytes = charset == null || !delimitada ? null : decodificarBytes(m.group(2), m.group(3));
            boolean contigua = charsetPendiente != null && SOLO_ESPACIOS.matcher(intermedio).matches();
            if (bytes == null) {
                volcar(resultado, pendientes, charsetPendiente);
                charsetPendiente = null;
                resultado.append(intermedio).append(m.group());
            } else if (contigua && charset.equals(charsetPendiente) && admiteUnion(charset)) {
                pendientes.writeBytes(bytes);
            } else {
                volcar(resultado, pendientes, charsetPendiente);
                if (!contigua) {
                    resultado.append(intermedio);
                }
                pendientes.writeBytes(bytes);
                charsetPendiente = charset;
            }
            ultimoFin = m.end();
        }
        volcar(resultado, pendientes, charsetPendiente);
        resultado.append(texto, ultimoFin, texto.length());
        return resultado.toString();
    }

    private static boolean esEspacio(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    }

    /**
     * Unir los bytes de palabras contiguas solo es seguro en UTF-8 y en juegos de un byte por carácter.
     * En UTF-16/32 cada palabra puede traer su propia marca de orden de bytes: se decodifican por separado.
     */
    private static boolean admiteUnion(Charset charset) {
        String nombre = charset.name().toUpperCase(Locale.ROOT);
        return !nombre.startsWith("UTF-16") && !nombre.startsWith("UTF-32");
    }

    private static void volcar(StringBuilder resultado, ByteArrayOutputStream pendientes, Charset charset) {
        if (charset != null && pendientes.size() > 0) {
            resultado.append(new String(pendientes.toByteArray(), charset));
        }
        pendientes.reset();
    }

    private static byte[] decodificarBytes(String codificacion, String datos) {
        return codificacion.equalsIgnoreCase("B") ? decodificarB(datos) : decodificarQ(datos);
    }

    private static byte[] decodificarB(String datos) {
        String completo = datos;
        // Algunos clientes omiten el relleno '='; lo restituimos para que el decodificador estricto lo acepte.
        while (completo.length() % 4 != 0) {
            completo += "=";
        }
        try {
            return Base64.getDecoder().decode(completo);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static byte[] decodificarQ(String datos) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(datos.length());
        for (int i = 0; i < datos.length(); i++) {
            char c = datos.charAt(i);
            if (c == '_') {
                bytes.write(' ');
            } else if (c == '=') {
                if (i + 2 >= datos.length()) {
                    return null;
                }
                int alto = Character.digit(datos.charAt(i + 1), 16);
                int bajo = Character.digit(datos.charAt(i + 2), 16);
                if (alto < 0 || bajo < 0) {
                    return null;
                }
                bytes.write(alto * 16 + bajo);
                i += 2;
            } else if (c > 0x20 && c < 0x7F) {
                bytes.write(c);
            } else {
                return null;
            }
        }
        return bytes.toByteArray();
    }

    /** Devuelve el juego de caracteres declarado o null si es desconocido (la palabra se deja literal). */
    private static Charset charset(String declarado) {
        // RFC 2231 permite añadir el idioma tras '*' (p. ej. UTF-8*es); el idioma no afecta la decodificación.
        String nombre = declarado.split("\\*", 2)[0].trim().toLowerCase(Locale.ROOT);
        switch (nombre) {
            case "utf-8", "utf8":
                return StandardCharsets.UTF_8;
            case "us-ascii":
                return StandardCharsets.US_ASCII;
            case "iso-8859-1", "latin1", "windows-1252", "cp1252":
                // Los clientes de Windows etiquetan como ISO-8859-1 texto que en realidad es Windows-1252
                // (comillas tipográficas, €). Windows-1252 es compatible en todos los caracteres imprimibles.
                return WINDOWS_1252;
            default:
                try {
                    return Charset.forName(nombre);
                } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                    return null;
                }
        }
    }

    private static Charset cargarWindows1252() {
        try {
            return Charset.forName("windows-1252");
        } catch (UnsupportedCharsetException e) {
            // Todo JDK completo trae windows-1252; si faltara, ISO-8859-1 es la mejor aproximación.
            return StandardCharsets.ISO_8859_1;
        }
    }
}
