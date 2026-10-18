package com.tecnomovil;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
    private static final int TOTAL_REGISTROS = 12000;
    private static final long SEMILLA = 20261018L;
    private static final long UMBRAL_CRITICO = 1150;

    /** Predicado reutilizable: el evento es una entrada al sistema. */
    private static final Predicate<RegistroTransporte> ES_ENTRADA =
            r -> r.accion().equals("entrada");

    /** Composición de funciones: registro -> timestamp -> hora del día. */
    private static final Function<RegistroTransporte, Integer> HORA_DEL_EVENTO =
            ((Function<RegistroTransporte, LocalDateTime>) RegistroTransporte::timestamp)
                    .andThen(LocalDateTime::getHour);

    public record RegistroTransporte(int idUsuario, String ruta,
            String estacion, String accion, LocalDateTime timestamp) {
        public RegistroTransporte {
            if (idUsuario < 1 || ruta == null || estacion == null
                    || accion == null || timestamp == null)
                throw new IllegalArgumentException("Todos los campos son obligatorios.");
            if (!accion.equals("entrada") && !accion.equals("salida"))
                throw new IllegalArgumentException("La acción debe ser entrada o salida.");
        }
    }

    public record RutaUso(String ruta, long registros) {}
    public record RutaEstado(String ruta, long entradas, boolean critica) {}

    public static void main(String[] args) {
        List<RegistroTransporte> registros = generarRegistros(TOTAL_REGISTROS, SEMILLA);
        System.out.println("=== TECNOMOVIL DATA: ANALISIS FUNCIONAL ===");
        System.out.println("Total de registros: " + registros.size());

        System.out.println("\n1. Afluencia por estación (top 5):");
        afluenciaPorEstacion(registros).entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .forEach(e -> System.out.println(e.getKey() + ": " + e.getValue() + " entradas"));

        System.out.println("\n2. Horas de mayor flujo (top 5):");
        entradasPorHora(registros).entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(5)
                .forEach(e -> System.out.printf("%02d:00 - %d entradas%n", e.getKey(), e.getValue()));

        System.out.println("\n3. Rutas más utilizadas:");
        rutasMasUtilizadas(registros)
                .forEach(r -> System.out.println(r.ruta() + ": " + r.registros() + " eventos"));

        System.out.println("\n4. Patrones de viaje por usuario (primeros 3 usuarios, 8 estaciones):");
        patronesPorUsuario(registros).entrySet().stream()
                .limit(3)
                .forEach(e -> System.out.println("Usuario " + e.getKey() + ": "
                        + e.getValue().stream().limit(8).collect(Collectors.joining(" -> "))));

        System.out.printf("%n5. Tiempo promedio entre estaciones diferentes: %.2f minutos%n",
                tiempoPromedioEntreEstaciones(registros));

        System.out.println("\n6. Estado de las rutas (umbral: " + UMBRAL_CRITICO + " entradas):");
        detectarSobrecarga(registros, UMBRAL_CRITICO)
                .forEach(r -> System.out.println(r.ruta() + ": " + r.entradas() + " entradas -> "
                        + (r.critica() ? "CRITICA" : "Normal")));

        System.out.println("\nValidación: la lista original conserva " + registros.size() + " registros.");
        System.out.println("Ejecución finalizada correctamente.");
    }

    /** Genera datos simulados reproducibles (misma semilla = mismos datos). */
    public static List<RegistroTransporte> generarRegistros(int cantidad, long semilla) {
        Random random = new Random(semilla);
        List<String> rutas = List.of("R1", "R2", "R3", "R4", "R5", "R6");
        List<String> estaciones = List.of("Centro", "Norte", "Sur", "Universidad",
                "Hospital", "Terminal", "Museo", "Parque", "Industria", "Aeropuerto");
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 18, 5, 0);
        return IntStream.range(0, cantidad).mapToObj(i -> {
            int usuario = 1 + random.nextInt(300);
            String ruta = rutas.get(random.nextInt(rutas.size()));
            String estacion = estaciones.get(random.nextInt(estaciones.size()));
            String accion = random.nextDouble() < 0.58 ? "entrada" : "salida";
            LocalDateTime tiempo = inicio.plusMinutes(random.nextInt(1080));
            return new RegistroTransporte(usuario, ruta, estacion, accion, tiempo);
        }).sorted(Comparator.comparing(RegistroTransporte::timestamp)).toList();
    }

    /**
     * Función de orden superior: recibe un filtro y una función clasificadora
     * y devuelve un conteo agrupado, no modificable.
     */
    public static <K> Map<K, Long> contarPor(List<RegistroTransporte> registros,
            Predicate<RegistroTransporte> filtro,
            Function<RegistroTransporte, K> clasificador) {
        return registros.stream()
                .filter(filtro)
                .collect(Collectors.collectingAndThen(
                        Collectors.groupingBy(clasificador, Collectors.counting()),
                        Collections::unmodifiableMap));
    }

    public static Map<String, Long> afluenciaPorEstacion(List<RegistroTransporte> registros) {
        return contarPor(registros, ES_ENTRADA, RegistroTransporte::estacion);
    }

    public static Map<Integer, Long> entradasPorHora(List<RegistroTransporte> registros) {
        return contarPor(registros, ES_ENTRADA, HORA_DEL_EVENTO);
    }

    public static List<RutaUso> rutasMasUtilizadas(List<RegistroTransporte> registros) {
        return contarPor(registros, r -> true, RegistroTransporte::ruta)
                .entrySet().stream()
                .map(e -> new RutaUso(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(RutaUso::registros).reversed())
                .toList();
    }

    public static Map<Integer, List<String>> patronesPorUsuario(List<RegistroTransporte> registros) {
        return registros.stream()
                .sorted(Comparator.comparing(RegistroTransporte::timestamp))
                .collect(Collectors.collectingAndThen(
                        Collectors.groupingBy(RegistroTransporte::idUsuario, TreeMap::new,
                                Collectors.mapping(RegistroTransporte::estacion,
                                        Collectors.toUnmodifiableList())),
                        Collections::unmodifiableMap));
    }

    public static double tiempoPromedioEntreEstaciones(List<RegistroTransporte> registros) {
        return registros.stream()
                .collect(Collectors.groupingBy(RegistroTransporte::idUsuario))
                .values().stream()
                .flatMap(lista -> {
                    List<RegistroTransporte> ordenados = lista.stream()
                            .sorted(Comparator.comparing(RegistroTransporte::timestamp))
                            .toList();
                    return IntStream.range(1, ordenados.size())
                            .filter(i -> !ordenados.get(i).estacion()
                                    .equals(ordenados.get(i - 1).estacion()))
                            .mapToObj(i -> Duration.between(ordenados.get(i - 1).timestamp(),
                                    ordenados.get(i).timestamp()));
                })
                .mapToLong(Duration::toSeconds)
                .average()
                .orElse(0.0) / 60.0;
    }

    public static List<RutaEstado> detectarSobrecarga(List<RegistroTransporte> registros, long umbral) {
        return contarPor(registros, ES_ENTRADA, RegistroTransporte::ruta)
                .entrySet().stream()
                .map(e -> new RutaEstado(e.getKey(), e.getValue(), e.getValue() > umbral))
                .sorted(Comparator.comparingLong(RutaEstado::entradas).reversed())
                .toList();
    }
}
