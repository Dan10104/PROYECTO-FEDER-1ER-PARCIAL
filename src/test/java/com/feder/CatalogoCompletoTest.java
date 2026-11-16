package com.feder;

import com.feder.datos.ConexionBD;
import com.feder.datos.GestorTransacciones;
import com.feder.datos.auditoria.BitacoraRepositorio;
import com.feder.datos.notificaciones.NotificacionRepositorio;
import com.feder.negocio.ContextoNegocio;
import com.feder.negocio.auditoria.ServicioBitacora;
import com.feder.negocio.comandos.CatalogoComandos;
import com.feder.negocio.comandos.ComandoSpec;
import com.feder.negocio.comandos.ModuloComandos;
import com.feder.negocio.comandos.ValidadorParametros;
import com.feder.negocio.notificaciones.ServicioNotificaciones;
import com.feder.presentacion.comandos.ComandoAnalizado;
import com.feder.presentacion.comandos.CommandParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprueba el catálogo completo tal como lo arma la raíz de composición: los 131 comandos de CU1–CU8 y del
 * sistema están registrados una sola vez, y el ejemplo documentado de cada uno pasa por el analizador del asunto y
 * por la validación de parámetros. No necesita base de datos: solo se construyen los módulos.
 */
class CatalogoCompletoTest {

    private static CatalogoComandos catalogo() {
        Clock reloj = Clock.systemUTC();
        // La conexión no se abre al construir los módulos.
        ContextoNegocio contexto = new ContextoNegocio(
                new GestorTransacciones(new ConexionBD("jdbc:postgresql://127.0.0.1:1/sin_conexion", "nadie", "")),
                reloj, new ServicioBitacora(new BitacoraRepositorio()), new ServicioNotificaciones(new NotificacionRepositorio(), reloj));
        CatalogoComandos catalogo = new CatalogoComandos();
        for (ModuloComandos modulo : Aplicacion.modulos(contexto, 30)) {
            catalogo.registrarModulo(modulo);
        }
        return catalogo;
    }

    @Test
    @DisplayName("Los 131 comandos están registrados, uno por nombre, repartidos en CU1–CU8 y el sistema")
    void cantidadYReparto() {
        CatalogoComandos catalogo = catalogo();
        assertEquals(131, catalogo.todos().size());
        Map<String, Integer> porCaso = new TreeMap<>();
        catalogo.todos().forEach(c -> porCaso.merge(c.casoDeUso(), 1, Integer::sum));
        for (String cu : List.of("CU1", "CU2", "CU3", "CU4", "CU5", "CU6", "CU7", "CU8")) {
            assertTrue(porCaso.getOrDefault(cu, 0) > 0, "sin comandos para " + cu + ": " + porCaso);
        }
    }

    @Test
    @DisplayName("El ejemplo de cada comando se analiza y valida sin errores")
    void ejemplosAnalizablesYValidos() throws Exception {
        CommandParser analizador = new CommandParser();
        List<String> errores = new ArrayList<>();
        for (ComandoSpec comando : catalogo().todos()) {
            try {
                ComandoAnalizado analizado = analizador.analizar(comando.ejemplo());
                if (!analizado.nombre().equals(comando.nombre())) {
                    errores.add(comando.nombre() + ": el ejemplo invoca " + analizado.nombre());
                    continue;
                }
                ValidadorParametros.validar(comando, analizado.parametros());
            } catch (Exception e) {
                errores.add(comando.nombre() + ": " + comando.ejemplo() + " -> " + e.getMessage());
            }
        }
        assertTrue(errores.isEmpty(), "Ejemplos con errores:\n" + String.join("\n", errores));
    }
}
