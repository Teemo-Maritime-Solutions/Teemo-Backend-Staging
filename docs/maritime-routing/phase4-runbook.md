# Reproducir la fase 4

Usar el entorno separado `.venv-weather`; no cambiar las dependencias congeladas
de `.venv-maritime`. Python requiere `eccodes==2.48.0`, `numpy==2.4.6` y
`certifi==2026.7.22`. La instalación usada incorpora ecCodes nativo 2.48.0 para Windows.

Los originales están en `data/maritime-routing/weather/ifs-20260922-00z-168h`.
ECMWF retiene pocas corridas en su servicio; preservar ese directorio y su recibo.
Una descarga futura usa una fecha publicada y **otro directorio**:

```powershell
.venv-weather/Scripts/python scripts/maritime/weather/acquire_forecast.py --date 20260922 --cycle 0 --end-hour 168 --step-hours 6 --output data/maritime-routing/weather/ifs-replay-new-source
.venv-weather/Scripts/python scripts/maritime/weather/build_forecast.py --source data/maritime-routing/weather/ifs-20260922-00z-168h --output data/maritime-routing/weather/ifs-replay-new-grid
.venv-weather/Scripts/python -m unittest discover -s scripts/maritime/weather -p test_*.py -v
```

El primer comando exige red y que esa corrida siga disponible. Los otros son
locales. Recalcular el manifiesto cambia su hash si cambia el código del constructor;
un manifiesto nuevo requiere un pin nuevo. No sobrescribir artefactos anteriores.

Configuración de la API con el artefacto comprobado:

```properties
routing.maritime.v2.artifact=data/maritime-routing/global-regional-research-v4-20260921.zip
routing.maritime.v2.max-entry-bytes=1000000000
routing.maritime.v2.sha256=1eed131c9bb2cb27596ac1aa451ffca555c4ca9ab448599297c8bd0226f7967c
routing.maritime.v2.validation-report=data/maritime-routing/global-regional-research-v4-20260921.validation.json
routing.maritime.weather.manifest=data/maritime-routing/weather/ifs-20260922-00z-168h-grid-v2/manifest.json
routing.maritime.weather.sha256=fab2df3821b9ce075b61a669ecab2defb9a36287d6c2d7b97a524fa8acc649a8
```

La validación usa heap máximo de 2.304 MiB y archivos meteorológicos mapeados fuera
del heap. Reservar memoria adicional para el sistema/JVM y las páginas del pronóstico.
Las pruebas de integración crean los catálogos directamente; no activan un servicio
de producción ni modifican las propiedades de despliegue.

Consultar [el contrato](phase4-api.md) para la petición y los modos de repetición.
La corrida conservada termina el 2026-09-29 00:00 UTC; después solo permite
repeticiones históricas dentro de ese intervalo, nunca extrapolación.

Validación reproducible del código y del protocolo final, desde la raíz del repositorio:

```powershell
$env:MAVEN_OPTS = '-Xmx256m'
mvn -q "-DargLine=-Xmx2304m" "-Dtest=WeatherRoutingTest,RealWeatherIntegrationTest,GraphArtifactLoaderTest,MaritimeRoutingControllerTest,RouteSearchTest" "-Dmaritime.weather.protocol=docs/maritime-routing/phase4-protocol-v3-20260922.json" "-Dmaritime.weather.report=target/maritime-routing/weather-replay-new" test
```

El test verifica las vinculaciones de implementación antes de cargar los datos y
ejecuta los tres casos y las peticiones negativas. Cambios de implementación exigen
un nuevo protocolo, conservando el anterior. No ejecutar simultáneamente con otro
proceso que cargue el mismo grafo completo.

Para volver a emitir el cierre a otro directorio, después de la validación:

```powershell
.venv-weather/Scripts/python -m unittest discover -s scripts/maritime/weather -p test_*.py -v > target/maritime-phase4-python-replay.log 2>&1
.venv-weather/Scripts/python scripts/maritime/summarize_weather_phase4.py --protocol docs/maritime-routing/phase4-protocol-v3-20260922.json --runtime target/maritime-routing/weather-replay-new --source data/maritime-routing/weather/ifs-20260922-00z-168h --python-log target/maritime-phase4-python-replay.log --output data/maritime-routing/phase4-evidence-replay-new
```

La evidencia original está en `data/maritime-routing/phase4-evidence-v1-20260922/`.
Su ZIP tiene SHA-256 `598b033a1a0c3a5c2b1c26ff1742bb2cbee986999cd4ce7f92a4b5dd2820b90a`.
El ZIP incluye informes y hashes; respaldar también los originales y las cuadrículas
que esos hashes identifican. No contiene copias de los grandes archivos del grafo
ni de los mensajes GRIB.
