# Integración local y flujo sencillo — 2026-09-23

Actualización posterior: el entorno local activa ahora la
[conexión de Panamá](panama-research-20260923.md). Las versiones y las cifras de
pruebas descritas más abajo son la evidencia anterior a esa ampliación.

El frontend abre `/maritime-plans` en modo sencillo: dos puertos del catálogo
conectado, cálculo de distancia v2 y reproducción de la geometría. No requiere
pronóstico, modelo de buque, contratos ni probabilidades. El modo avanzado
conserva los análisis anteriores y permite animar también una consulta
meteorológica aislada desde **1 · Rutas**.

## Corrección de los errores de ejecución

Los 503 observados eran `ARTIFACT_NOT_CONFIGURED` y `FORECAST_NOT_CONFIGURED`:
el proceso activo no tenía las propiedades de sus snapshots.
`application.properties` importa ahora opcionalmente
`file:./config/maritime-local.properties`. Ese archivo es local, ignorado por Git,
y contiene las rutas y hashes del grafo/validación de fase 3, pronóstico de fase 4
y directorio `data/maritime-routing/local-plans`. Mantiene los overrides de
variables de entorno. No se distribuye esa configuración local.

`MaritimeLandMask` tenía dos constructores sin seleccionar el de producción
para Spring. Se añadió `@Autowired` a ese constructor, sin cambiar la lógica
geométrica ni habilitar el cálculo heredado bloqueado por el filtro. La prueba
`MaritimeLandMaskWiringTest` crea el bean con la fuente local real.
El endpoint de lectura `/api/routes/popular` ya no falla al inicializar esa cadena.

Para iniciar desde la raíz del backend, o reiniciar solo el Java del proyecto
que escucha en 8080:

```powershell
.\scripts\maritime\start_local_research.ps1
# Solo si el backend ya está iniciado:
.\scripts\maritime\start_local_research.ps1 -Restart
```

El lanzador abre el proceso oculto, con heap máximo de 2.560 MiB y salida en
`target/local-research-server.log`. Comprueba el propietario del puerto antes
de detenerlo. El frontend de 4200 no se reinicia. Para usar el IDE, configurar
la raíz del proyecto como directorio de trabajo y reservar heap suficiente.
No iniciar dos backends contra el mismo almacén. La configuración opcional
requiere reiniciar el proceso; los catálogos no se recargan dentro de una petición.
En otras máquinas preparar el archivo local con las propiedades documentadas
en `phase4-runbook.md` y `phase7-api.md`.

## Comprobación sobre el servicio activo

Después del reinicio, respondieron HTTP 200:

- `/api/v2/maritime/graph`: versión
  `e6ac2932cd9c941202dc840322411105e84b88b4ccf9f83182ea9a0e57e63437`.
- `/api/v2/maritime/weather`: versión
  `fab2df3821b9ce075b61a669ecab2defb9a36287d6c2d7b97a524fa8acc649a8`.
- `/api/v2/maritime/ports`: 1.114 referencias conectadas, recuperadas en todas
  sus páginas sin mezclar versiones.
- `/api/v2/maritime/routes`: Dubai (`UNLOCODE:AEDXB`) a Mina Khalifa/Abu Dhabi
  (`UNLOCODE:AEKHL`), 80.417,2606 m, geometría `MultiLineString`, tiempo estimado
  nulo al no declarar velocidad. La respuesta permite el origen local del frontend.
- `/api/v2/maritime/weather-routes`: primera ruta del ejemplo de fase 7,
  `FOUND_RESEARCH_SCENARIO` en repetición histórica.
- `/api/v2/maritime/plans`: ejemplo sintético de fase 7,
  `INTEGRATED_RESEARCH_COMPARISON`, guardado en el almacén local de investigación.
- `/api/routes/popular?limit=8`: lectura heredada inicializada correctamente.

Respuestas en `target/local-simple-route-live.json`,
`target/local-research-live-weather.json` y `target/local-research-live-plan.json`.
El último contiene el ID/hash del plan sintético de comprobación. No es un viaje
comercial ni una calibración de probabilidades.

Validación Java: **23 pruebas**, sin fallos/errores/omisiones:
`MaritimeLandMaskWiringTest`, `MaritimeLandMaskRegressionTest`,
`MaritimeRoutingControllerTest`, `RouteSearchTest`. Los archivos vinculados
por el cierre de fase 7 permanecen inalterados; `MaritimeLandMask` no forma
parte de esas implementaciones congeladas.

Frontend: compilación de producción y **60 pruebas** en Chrome Headless.
Incluyen selección de puertos, errores sin rutas de respaldo, paginación completa,
modo sencillo sin pedir clima, y animación que no atraviesa el antimeridiano
artificialmente. No hay navegador interactivo conectado para revisión visual
manual; la disponibilidad HTTP sí se comprobó contra el proceso activo.
