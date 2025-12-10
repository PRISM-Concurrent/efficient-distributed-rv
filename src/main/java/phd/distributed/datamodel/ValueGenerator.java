package phd.distributed.datamodel;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Generador de valores para testing de linearizabilidad Soporta tipos
 * primitivos, colecciones y objetos personalizados
 */
public class ValueGenerator {

    // Cache de generadores para mejorar performance
    private static final Map<Class<?>, Function<Integer, Object>> GENERATOR_CACHE
            = new ConcurrentHashMap<>();
    // Registro de generadores personalizados
    private static final Map<Class<?>, Function<Integer, Object>> CUSTOM_GENERATORS
            = new ConcurrentHashMap<>();

    static {
        initializeBuiltinGenerators();
    }

    /**
     * Genera un valor para el tipo especificado
     *
     * @param tipo La clase del tipo a generar
     * @param base Valor base para la generación
     * @return Valor generado o null si no es posible
     */
    public static Object getValue(Class<?> tipo, int base) {
        if (tipo == null) {
            throw new IllegalArgumentException("El tipo no puede ser null");
        }

        return GENERATOR_CACHE.computeIfAbsent(tipo, ValueGenerator::createGenerator)
                .apply(base);
    }

    /**
     * Registra un generador personalizado para un tipo específico
     *
     * @param <T>
     * @param tipo
     * @param generador
     */
    public static <T> void registerGenerator(Class<T> tipo, Function<Integer, T> generador) {
        CUSTOM_GENERATORS.put(tipo, (Function<Integer, Object>) generador);
        GENERATOR_CACHE.remove(tipo); // Limpiar cache para forzar regeneración
    }

    /**
     * Crea un generador para el tipo especificado
     */
    private static Function<Integer, Object> createGenerator(Class<?> tipo) {
        // Primero verificar generadores personalizados
        if (CUSTOM_GENERATORS.containsKey(tipo)) {
            return CUSTOM_GENERATORS.get(tipo);
        }

        // Tipos primitivos y wrappers
        if (isPrimitiveOrWrapper(tipo)) {
            return createPrimitiveGenerator(tipo);
        }

        // String y Object
        if (tipo == String.class) {
            return base -> "msg-" + base;
        }
        if (tipo == Object.class) {
            return base -> "obj-" + base;
        }

        // Colecciones
        if (Collection.class.isAssignableFrom(tipo)) {
            return createCollectionGenerator(tipo);
        }

        // Maps
        if (Map.class.isAssignableFrom(tipo)) {
            return createMapGenerator(tipo);
        }

        // Arrays
        if (tipo.isArray()) {
            return createArrayGenerator(tipo);
        }

        // Enums
        if (tipo.isEnum()) {
            return createEnumGenerator(tipo);
        }

        // Objetos personalizados
        return createObjectGenerator(tipo);
    }

    private static boolean isPrimitiveOrWrapper(Class<?> tipo) {
        return tipo.isPrimitive()
                || tipo == Integer.class || tipo == Long.class
                || tipo == Double.class || tipo == Float.class
                || tipo == Boolean.class || tipo == Character.class
                || tipo == Byte.class || tipo == Short.class;
    }

    private static Function<Integer, Object> createPrimitiveGenerator(Class<?> tipo) {
        if (tipo == int.class || tipo == Integer.class) {
            return base -> base;
        }
        if (tipo == long.class || tipo == Long.class) {
            return base -> (long) base;
        }
        if (tipo == double.class || tipo == Double.class) {
            return base -> base + 0.5;
        }
        if (tipo == float.class || tipo == Float.class) {
            return base -> (float) (base + 0.25);
        }
        if (tipo == boolean.class || tipo == Boolean.class) {
            return base -> base % 2 == 0;
        }
        if (tipo == char.class || tipo == Character.class) {
            return base -> (char) ('A' + (base % 26));
        }
        if (tipo == byte.class || tipo == Byte.class) {
            return base -> (byte) (base % 128);
        }
        if (tipo == short.class || tipo == Short.class) {
            return base -> (short) (base % 32768);
        }
        return base -> null;
    }

    @SuppressWarnings("unchecked")
    private static Function<Integer, Object> createCollectionGenerator(Class<?> tipo) {
        return base -> {
            Collection<Object> collection;

            // Determinar tipo específico de colección
            if (tipo == LinkedList.class || tipo == Queue.class) {
                collection = new LinkedList<>();
            } else if (tipo == List.class || tipo == ArrayList.class
                    || List.class.isAssignableFrom(tipo)) {
                collection = new ArrayList<>();
            } else if (tipo == Set.class || tipo == HashSet.class
                    || Set.class.isAssignableFrom(tipo)) {
                collection = new HashSet<>();
            } else {
                // Intentar crear instancia del tipo específico
                try {
                    collection = (Collection<Object>) tipo.getDeclaredConstructor().newInstance();
                } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException | SecurityException | InvocationTargetException e) {
                    collection = new ArrayList<>(); // fallback
                }
            }

            // Agregar elementos
            collection.add("elem-" + base);
            if (!(collection instanceof Set) || collection.size() == 1) {
                collection.add("elem-" + (base + 1));
            }

            return collection;
        };
    }

    @SuppressWarnings("unchecked")
    private static Function<Integer, Object> createMapGenerator(Class<?> tipo) {
        return base -> {
            Map<Object, Object> map;

            if (tipo == Map.class || tipo == HashMap.class || Map.class.isAssignableFrom(tipo)) {
                try {
                    map = (Map<Object, Object>) tipo.getDeclaredConstructor().newInstance();
                } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException | SecurityException | InvocationTargetException e) {
                    map = new HashMap<>(); // fallback
                }
            } else {
                map = new HashMap<>();
            }

            map.put("key-" + base, "value-" + base);
            return map;
        };
    }

    private static Function<Integer, Object> createArrayGenerator(Class<?> tipo) {
        Class<?> componentType = tipo.getComponentType();
        return base -> {
            Object array = java.lang.reflect.Array.newInstance(componentType, 2);
            java.lang.reflect.Array.set(array, 0, getValue(componentType, base));
            java.lang.reflect.Array.set(array, 1, getValue(componentType, base + 1));
            return array;
        };
    }

    @SuppressWarnings("unchecked")
    private static Function<Integer, Object> createEnumGenerator(Class<?> tipo) {
        return base -> {
            Object[] constants = tipo.getEnumConstants();
            if (constants != null && constants.length > 0) {
                return constants[base % constants.length];
            }
            return null;
        };
    }

    private static Function<Integer, Object> createObjectGenerator(Class<?> tipo) {
        return base -> {
            try {
                // Intentar constructor sin argumentos
                Constructor<?> constructor = tipo.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException | SecurityException | InvocationTargetException e) {
                // Intentar constructores con parámetros comunes
                return tryParameterizedConstructors(tipo, base);
            }
        };
    }

    private static Object tryParameterizedConstructors(Class<?> tipo, int base) {
        Constructor<?>[] constructors = tipo.getDeclaredConstructors();

        for (Constructor<?> constructor : constructors) {
            try {
                constructor.setAccessible(true);
                Class<?>[] paramTypes = constructor.getParameterTypes();
                Object[] args = new Object[paramTypes.length];

                for (int i = 0; i < paramTypes.length; i++) {
                    args[i] = getValue(paramTypes[i], base + i);
                    if (args[i] == null && !paramTypes[i].isPrimitive()) {
                        break; // No se pudo generar argumento
                    }
                }

                return constructor.newInstance(args);
            } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException e) {
                // Intentar siguiente constructor
            }
        }

        return null; // No se pudo crear instancia
    }

    private static void initializeBuiltinGenerators() {
        // Los generadores se crean bajo demanda, no necesitamos pre-poblar el cache
        // Pero podemos registrar algunos generadores especiales si es necesario

        // Ejemplo: UUID generator
        registerGenerator(UUID.class, base
                -> UUID.nameUUIDFromBytes(("test-" + base).getBytes()));
    }

    /**
     * Limpia el cache de generadores (útil para testing)
     */
    public static void clearCache() {
        GENERATOR_CACHE.clear();
        CUSTOM_GENERATORS.clear();
        // Don't re-initialize built-in generators for testing
    }

    /**
     * Obtiene estadísticas del cache
     *
     * @return
     */
    public static Map<String, Integer> getCacheStats() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("cacheSize", GENERATOR_CACHE.size());
        stats.put("customGenerators", CUSTOM_GENERATORS.size());
        return stats;
    }
}
