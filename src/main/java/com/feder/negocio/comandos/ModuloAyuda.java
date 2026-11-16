package com.feder.negocio.comandos;

import com.feder.negocio.comun.NegocioException;
import com.feder.negocio.comun.ResultadoOperacion;

import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Comando HELP: la ayuda se genera a partir del propio catálogo, así nunca queda desactualizada.
 *   HELP["*"]      lista todos los comandos agrupados por caso de uso.
 *   HELP["CU4"]    lista los comandos de un caso de uso.
 *   HELP["INSCAT"] muestra la sintaxis, los parámetros y un ejemplo de un comando.
 */
public final class ModuloAyuda implements ModuloComandos {

    private CatalogoComandos catalogo;

    @Override
    public void registrar(CatalogoComandos catalogo) {
        this.catalogo = catalogo;
        catalogo.registrar(ComandoSpec.comando("HELP")
                .casoDeUso("SISTEMA")
                .descripcion("Muestra los comandos disponibles o el detalle de uno")
                .parametros(ParametroSpec.texto("tema", 12).conComodin())
                .publico()
                .ejemplo("HELP[\"*\"]")
                .handler((p, s) -> p.esComodin("tema") ? listarTodo() : detalle(p.texto("tema")))
                .construir());
    }

    private ResultadoOperacion listarTodo() {
        ResultadoOperacion resultado = ResultadoOperacion.exito("Comandos disponibles",
                "Formato general: COMANDO[\"param1\",\"param2\"]. Todos los parámetros van entre comillas dobles. "
                        + "Las operaciones que modifican datos se confirman con CONFIRMAR[\"CODIGO\"] usando el código "
                        + "que el sistema envía a su correo registrado.");
        TreeMap<String, List<ComandoSpec>> porCaso = catalogo.todos().stream()
                .collect(Collectors.groupingBy(ComandoSpec::casoDeUso, TreeMap::new, Collectors.toList()));
        porCaso.forEach((caso, comandos) -> {
            ResultadoOperacion.Tabla tabla = new ResultadoOperacion.Tabla(caso, List.of("Comando", "Descripción", "Acceso"));
            comandos.forEach(c -> tabla.fila(c.sintaxis(), c.descripcion(), acceso(c)));
            resultado.tabla(tabla);
        });
        return resultado;
    }

    private ResultadoOperacion detalle(String tema) {
        String buscado = tema.trim().toUpperCase(Locale.ROOT);
        List<ComandoSpec> delCaso = catalogo.delCasoDeUso(buscado);
        if (!delCaso.isEmpty()) {
            ResultadoOperacion resultado = ResultadoOperacion.exito("Comandos de " + buscado, "");
            ResultadoOperacion.Tabla tabla = new ResultadoOperacion.Tabla(buscado, List.of("Comando", "Descripción", "Ejemplo"));
            delCaso.forEach(c -> tabla.fila(c.sintaxis(), c.descripcion(), c.ejemplo()));
            return resultado.tabla(tabla);
        }
        ComandoSpec comando = catalogo.buscar(buscado).orElseThrow(() -> NegocioException.noEncontrado(
                "No existe el comando ni el caso de uso " + buscado + ". Envíe HELP[\"*\"]."));
        ResultadoOperacion resultado = ResultadoOperacion.exito("Ayuda de " + comando.nombre(), comando.descripcion())
                .detalle("Sintaxis", comando.sintaxis())
                .detalle("Ejemplo", comando.ejemplo())
                .detalle("Acceso", acceso(comando))
                .detalle("Confirmación", comando.modificaDatos() ? "Sí: responda con CONFIRMAR[\"CODIGO\"]" : "No");
        ResultadoOperacion.Tabla parametros = new ResultadoOperacion.Tabla("Parámetros", List.of("N.º", "Parámetro"));
        for (int i = 0; i < comando.parametros().size(); i++) {
            parametros.fila(i + 1, comando.parametros().get(i).describir());
        }
        return resultado.tabla(parametros);
    }

    private static String acceso(ComandoSpec c) {
        switch (c.acceso()) {
            case PUBLICO:
                return "Público";
            case REGISTRADO:
                return "Usuario registrado";
            default:
                return c.roles().stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
        }
    }
}
