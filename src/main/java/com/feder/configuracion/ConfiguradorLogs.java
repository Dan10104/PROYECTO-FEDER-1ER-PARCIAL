package com.feder.configuracion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Configura los dos registros exigidos por el proyecto:
 *   logs/access.log  cada correo procesado y cada respuesta enviada (logger "feder.access").
 *   logs/error.log   excepciones y fallos de protocolo o de base de datos (logger "feder.error").
 * Se usa java.util.logging del JDK; no hace falta ninguna dependencia adicional.
 * Ningún mensaje de log incluye contraseñas: las clases que registran nunca las reciben como texto.
 */
public final class ConfiguradorLogs {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private ConfiguradorLogs() {
    }

    public static void configurar(Path carpeta) {
        try {
            Files.createDirectories(carpeta);
            configurarLogger("feder.access", carpeta.resolve("access.log"), Level.INFO);
            configurarLogger("feder.error", carpeta.resolve("error.log"), Level.WARNING);
        } catch (IOException e) {
            throw new ConfiguracionException("No se pudo preparar la carpeta de logs " + carpeta + ": " + e.getMessage());
        }
    }

    private static void configurarLogger(String nombre, Path archivo, Level nivel) throws IOException {
        Logger logger = Logger.getLogger(nombre);
        for (Handler anterior : logger.getHandlers()) {
            logger.removeHandler(anterior);
            anterior.close();
        }
        // Rotación: hasta 5 archivos de 5 MB, en modo agregar para conservar el historial.
        FileHandler archivoLog = new FileHandler(archivo.toString().replace("%", "%%"), 5 * 1024 * 1024, 5, true);
        archivoLog.setEncoding("UTF-8");
        archivoLog.setFormatter(new FormatoLinea());
        archivoLog.setLevel(nivel);
        logger.addHandler(archivoLog);
        ConsoleHandler consola = new ConsoleHandler();
        consola.setFormatter(new FormatoLinea());
        consola.setLevel(nivel);
        logger.addHandler(consola);
        logger.setUseParentHandlers(false);
        logger.setLevel(nivel);
    }

    /** Una línea por evento: fecha | nivel | mensaje [| excepción]. */
    static final class FormatoLinea extends Formatter {
        @Override
        public String format(LogRecord registro) {
            StringBuilder linea = new StringBuilder()
                    .append(FORMATO.format(OffsetDateTime.now()))
                    .append(" | ").append(registro.getLevel().getName())
                    .append(" | ").append(limpiar(formatMessage(registro)));
            if (registro.getThrown() != null) {
                Throwable t = registro.getThrown();
                linea.append(" | ").append(t.getClass().getName()).append(": ").append(limpiar(String.valueOf(t.getMessage())));
                if (t.getCause() != null) {
                    linea.append(" | causa: ").append(t.getCause().getClass().getSimpleName())
                            .append(": ").append(limpiar(String.valueOf(t.getCause().getMessage())));
                }
            }
            return linea.append(System.lineSeparator()).toString();
        }

        /** Evita que un asunto o remitente malicioso inserte líneas falsas en el log. */
        static String limpiar(String texto) {
            StringBuilder sb = new StringBuilder(texto.length());
            texto.codePoints().forEach(c -> {
                if (c == '\r' || c == '\n') {
                    sb.append("\\n");
                } else if (Character.isISOControl(c)) {
                    sb.append('?');
                } else {
                    sb.appendCodePoint(c);
                }
            });
            return sb.toString();
        }
    }
}
