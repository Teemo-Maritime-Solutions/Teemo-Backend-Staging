# Fase 7: integración versionada de investigación

Alcance: `PHASE7_INTEGRATED_RESEARCH_PLANS`. Conserva los alcances aprobados:
grafo mundial de investigación con controles regionales, buque parametrizable,
contratos de cumplimiento ordinario y probabilidades conjuntas declaradas.

## Entregables y criterios previos

1. Crear un plan mediante rutas meteorológicas realmente calculadas en el servidor,
   contratos evaluados y comparación conjunta. No aceptar rutas calculadas por el
   cliente como si fueran resultados verificados.
2. Mismos extremos físicos, salida, grafo y pronóstico para las rutas comparadas.
   Vinculación explícita de cada alternativa con un tramo contractual completo;
   su correspondencia con los hitos sigue siendo una declaración de investigación.
3. Duración de escenario = navegación + espera modelada + tiempo no marítimo
   declarado + tiempo adicional de escenario. No inferir costos o probabilidades
   de la distancia o del pronóstico. Registrar redondeo y horizonte físico.
4. Respuestas con geometría, versiones, fuentes, restricciones, cobertura, costos,
   pérdidas, exposición/control, Pareto, motivos de dominancia y datos ausentes.
5. Planes inmutables en un directorio configurado por operador, con ID aleatorio,
   hash de contenido y cuota. Lectura/procedencia/explicación por ID. Sin listado
   público, rutas de archivos o URLs suministradas por el cliente.
6. Recálculo con petición completa y revisión comercial explícita; comprobar hash
   de la revisión anterior. Guardar una nueva revisión y diferencias, sin editar
   el padre ni reinterpretar tarifas como automáticamente actualizadas.
7. Pruebas de unión de contratos/tiempos, fallos de rutas sin comparación parcial,
   versiones, integridad, JSON estricto, cuotas y concurrencia. Integración real
   con el grafo y pronóstico conservados, más recálculo con una arista cerrada.
8. Congelados y regresiones preservados. Sin despliegue, modificación de MongoDB,
   descarga de nuevos datos ni afirmación de calibración o navegación operativa.

El almacenamiento requiere configuración explícita; las pruebas usan directorios
aislados. El mecanismo de IDs no sustituye la autorización de usuarios para un
despliegue público. El control de acceso productivo no está incluido en este cierre.
