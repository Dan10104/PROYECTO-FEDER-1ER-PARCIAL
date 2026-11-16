package com.feder.presentacion.respuesta;

import static com.feder.presentacion.respuesta.EscapadorHtml.escapar;

import com.feder.negocio.comun.ResultadoOperacion;
import java.util.List;
import java.util.Map;

/**
 * Cuerpo text/html de la respuesta a partir de las plantillas de src/main/resources/plantillas.
 *
 * Cada valor que proviene del resultado o del comando se escapa aquí; las plantillas solo aportan estructura.
 */
final class FormateadorHtml {

    private final MotorPlantillas plantillas;

    FormateadorHtml(MotorPlantillas plantillas) {
        this.plantillas = plantillas;
    }

    /** @param comando asunto recibido, o null si el correo es un aviso que no responde a ningún comando */
    String formatear(ResultadoOperacion resultado, String titulo, String comando, String pie) {
        StringBuilder tablas = new StringBuilder();
        for (ResultadoOperacion.Tabla tabla : resultado.tablas()) {
            tablas.append(tabla(tabla));
        }
        String mensaje = Textos.multilinea(resultado.mensaje());
        return plantillas.renderizar("respuesta.html", Map.of(
                "estilos", estilos(),
                "titulo", escapar(titulo),
                "estado", comando == null ? "Aviso" : resultado.esExito() ? "Éxito" : "Error",
                "claseEstado", comando == null ? "aviso" : resultado.esExito() ? "exito" : "error",
                "mensaje", mensaje.isEmpty() ? "" : "<p class=\"mensaje\">" + escapar(mensaje).replace("\n", "<br>") + "</p>",
                "detalles", detalles(resultado.detalles()),
                "tablas", tablas.toString(),
                "bloqueComando", comando == null ? "" : plantillas.renderizar("comando.html",
                        Map.of("comando", escapar(comando.isEmpty() ? "(sin comando)" : comando))),
                "pie", escapar(pie)));
    }

    private String estilos() {
        String css = plantillas.cargar("estilos.css");
        // El CSS se inserta dentro de <style>; si contuviera "</" podría cerrar la etiqueta antes de tiempo.
        if (css.contains("</")) {
            throw new IllegalStateException("estilos.css no puede contener la secuencia '</'");
        }
        return css.strip();
    }

    private String detalles(List<String[]> detalles) {
        if (detalles.isEmpty()) {
            return "";
        }
        StringBuilder filas = new StringBuilder();
        for (String[] detalle : detalles) {
            filas.append("<tr><th scope=\"row\">").append(escapar(Textos.unaLinea(detalle[0])))
                    .append("</th><td>").append(escapar(Textos.unaLinea(detalle[1]))).append("</td></tr>\n");
        }
        return plantillas.renderizar("detalles.html", Map.of("filas", filas.toString()));
    }

    private String tabla(ResultadoOperacion.Tabla tabla) {
        StringBuilder encabezados = new StringBuilder();
        for (String columna : tabla.columnas()) {
            encabezados.append("<th scope=\"col\">").append(escapar(Textos.unaLinea(columna))).append("</th>");
        }
        StringBuilder filas = new StringBuilder();
        for (List<String> fila : tabla.filas()) {
            filas.append("<tr>");
            for (String celda : fila) {
                filas.append("<td>").append(escapar(Textos.unaLinea(celda))).append("</td>");
            }
            filas.append("</tr>\n");
        }
        if (tabla.filas().isEmpty()) {
            filas.append("<tr><td class=\"vacio\" colspan=\"").append(Math.max(1, tabla.columnas().size()))
                    .append("\">Sin registros.</td></tr>\n");
        }
        int cantidad = tabla.filas().size();
        String titulo = Textos.unaLinea(tabla.titulo());
        return plantillas.renderizar("tabla.html", Map.of(
                "titulo", escapar(titulo.isEmpty() ? "Resultados" : titulo),
                "encabezados", encabezados.toString(),
                "filas", filas.toString(),
                "conteo", cantidad == 1 ? "1 registro" : cantidad + " registros"));
    }
}
