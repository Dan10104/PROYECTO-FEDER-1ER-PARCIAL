package com.feder.negocio.notificaciones;

import com.feder.modelo.Notificacion;
import com.feder.negocio.ContextoNegocio;
import com.feder.negocio.catalogo.FechasBolivia;
import com.feder.negocio.comandos.CatalogoComandos;
import com.feder.negocio.comandos.ComandoSpec;
import com.feder.negocio.comandos.ModuloComandos;
import com.feder.negocio.comandos.ParametroSpec;
import com.feder.negocio.comandos.Parametros;
import com.feder.negocio.comun.NegocioException;
import com.feder.negocio.comun.ResultadoOperacion;
import com.feder.negocio.comun.Solicitante;

import java.util.List;

/**
 * CU6 · Notificaciones: cada usuario consulta y marca como leídas solo sus propias notificaciones
 * (los avisos de sus pedidos y, para el personal, las alertas de stock).
 */
public final class ModuloNotificaciones implements ModuloComandos {

    private static final String CU = "CU6";
    private static final int LIMITE = 50;

    private final ContextoNegocio ctx;

    public ModuloNotificaciones(ContextoNegocio ctx) {
        this.ctx = ctx;
    }

    @Override
    public void registrar(CatalogoComandos catalogo) {
        catalogo.registrar(ComandoSpec.comando("LISNOT").casoDeUso(CU)
                .descripcion("Lista sus notificaciones, de la más reciente a la más antigua (\"true\" = solo las no leídas)")
                .parametros(ParametroSpec.booleano("solo_no_leidas").conComodin())
                .registrado().ejemplo("LISNOT[\"*\"]").handler(this::listar).construir());
        catalogo.registrar(ComandoSpec.comando("LEENOT").casoDeUso(CU)
                .descripcion("Marca como leída una notificación suya por su número (\"*\" = todas las no leídas)")
                .parametros(ParametroSpec.entero("notificacion").conComodin())
                .registrado().modificaDatos().ejemplo("LEENOT[\"*\"]").handler(this::marcar).construir());
    }

    private ResultadoOperacion listar(Parametros p, Solicitante s) {
        boolean soloNoLeidas = Boolean.TRUE.equals(p.booleano("solo_no_leidas"));
        List<Notificacion> lista = ctx.transacciones().enTransaccion(
                () -> ctx.notificaciones().deUsuario(s.usuarioId(), soloNoLeidas, LIMITE));
        ResultadoOperacion.Tabla tabla = new ResultadoOperacion.Tabla("Notificaciones", List.of("N.º", "Fecha", "Tipo", "Canal",
                "Título", "Mensaje", "Estado", "Leída"));
        lista.forEach(n -> tabla.fila(n.getId(), FechasBolivia.formatear(n.getFechaCreacion()), n.getTipo(), n.getCanal(),
                n.getTitulo(), n.getMensaje(), n.getEstado(), n.getFechaLectura() == null ? "No" : FechasBolivia.formatear(n.getFechaLectura())));
        return ResultadoOperacion.exito("Sus notificaciones", lista.size() + " notificación(es)"
                + (lista.size() == LIMITE ? " (se muestran las " + LIMITE + " más recientes)." : ".")).tabla(tabla);
    }

    private ResultadoOperacion marcar(Parametros p, Solicitante s) {
        long usuario = s.usuarioId();
        if (!p.tieneValor("notificacion")) {
            int marcadas = ctx.transacciones().enTransaccion(() -> ctx.notificaciones().marcarLeidas(usuario));
            return ResultadoOperacion.exito("Notificaciones leídas", marcadas + " notificación(es) marcada(s) como leída(s).");
        }
        long id = p.entero("notificacion");
        int marcadas = ctx.transacciones().enTransaccion(() -> {
            // Una notificación ajena se informa igual que una inexistente.
            if (!ctx.notificaciones().esDelUsuario(usuario, id)) {
                throw NegocioException.noEncontrado("No tiene la notificación " + id + ". Consulte LISNOT[\"*\"].");
            }
            return ctx.notificaciones().marcarLeida(usuario, id);
        });
        return ResultadoOperacion.exito("Notificación leída", marcadas == 1 ? "La notificación " + id + " quedó marcada como leída."
                : "La notificación " + id + " ya estaba leída.");
    }
}
