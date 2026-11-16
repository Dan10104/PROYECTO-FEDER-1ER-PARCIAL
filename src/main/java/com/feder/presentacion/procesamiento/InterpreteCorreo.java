package com.feder.presentacion.procesamiento;

import com.feder.negocio.mensajeria.SolicitudCorreo;
import com.feder.presentacion.comandos.CommandParser;
import com.feder.presentacion.comandos.ComandoAnalizado;
import com.feder.presentacion.comandos.ErrorSintaxisException;
import com.feder.presentacion.correo.mime.CorreoEntrante;
import com.feder.presentacion.correo.mime.EmailMessageParser;
import com.feder.presentacion.correo.mime.MensajeInvalidoException;

import java.util.logging.Logger;

/**
 * Convierte los bytes de un correo recibido en una {@link SolicitudCorreo} para la capa de negocio:
 * extrae From y Subject, descarta rebotes y respuestas automáticas, y analiza la sintaxis del comando.
 */
public class InterpreteCorreo {

    private static final Logger ERROR = Logger.getLogger("feder.error");

    private final EmailMessageParser analizadorMensajes = new EmailMessageParser();
    private final CommandParser analizadorComandos = new CommandParser();
    private final String buzon;
    private final String direccionSistema;

    public InterpreteCorreo(String buzon, String direccionSistema) {
        this.buzon = buzon;
        this.direccionSistema = direccionSistema;
    }

    public SolicitudCorreo interpretar(String uidl, byte[] crudo) {
        CorreoEntrante correo;
        try {
            correo = analizadorMensajes.analizar(crudo);
        } catch (MensajeInvalidoException e) {
            // Sin cabeceras confiables no sabemos a quién responder: se registra y no se contesta.
            ERROR.warning(() -> "Mensaje con cabeceras inválidas uidl=" + uidl + ": " + e.getMessage());
            return SolicitudCorreo.automatica(buzon, uidl, null, "desconocido", null);
        }
        String remitente = correo.from();
        // Un correo enviado por el propio sistema (por ejemplo, una respuesta reenviada) nunca se procesa:
        // así una respuesta automática no puede convertirse en un comando nuevo.
        if (correo.esRespuestaAutomatica() || remitente.equalsIgnoreCase(direccionSistema)) {
            return SolicitudCorreo.automatica(buzon, uidl, correo.messageId(), remitente, correo.subject());
        }
        String asunto = correo.subject() == null ? "" : correo.subject();
        try {
            ComandoAnalizado comando = analizadorComandos.analizar(asunto);
            return SolicitudCorreo.conComando(buzon, uidl, correo.messageId(), remitente, asunto,
                    comando.nombre(), comando.parametros());
        } catch (ErrorSintaxisException e) {
            return SolicitudCorreo.conErrorSintaxis(buzon, uidl, correo.messageId(), remitente, asunto, e.getMessage());
        }
    }
}
