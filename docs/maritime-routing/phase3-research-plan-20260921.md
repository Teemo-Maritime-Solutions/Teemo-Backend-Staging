# Fase 3: planificación mundial de investigación con controles regionales

Fecha: 2026-09-21. Versión del alcance: `PHASE3_GLOBAL_RESEARCH_V2`.
Estado: **COMPLETADO** el 2026-09-22 para este alcance de investigación.
La ejecución y el cierre **PASS** están en el [informe de resultados](phase3-research-results-20260921.md).

El usuario autorizó actualizar la fase tras revisar Chen et al. El objetivo es
calcular rutas marítimas de investigación entre referencias de origen y destino
conectadas a la malla mundial, evitando tierra según las fuentes disponibles y
conservando los controles detallados de Nueva York. La cobertura mundial describe
la extensión del modelo; no significa acceso a todos los puertos, canales o polos,
ni permiso o seguridad de navegación para un buque concreto.

Este alcance sustituye los requisitos pendientes de la fase 3 del plan anterior.
Su resultado histórico `phase3-status-20260921.json` permanece **NOT_PASSED**.
Los protocolos, resultados fallidos y registros de experimentos anteriores se
conservan sin modificación. El nuevo alcance tendrá su propio informe de aceptación.

## 1. Qué tomamos del artículo

Chen et al., *Intelligent ship route planning via an A* search model enhanced
double-deep Q-network*, Ocean Engineering 327 (2025), 120956,
[DOI](https://doi.org/10.1016/j.oceaneng.2025.120956).
Documento revisado: `C:\Users\ASUS\Downloads\40 Articulos\chen.pdf`.

El artículo representa el espacio mediante celdas de agua y obstáculos, usa
48 vecinos y combina A* con DDQN; añade oleaje para modificar la velocidad y el
costo. Esa separación entre geometría, búsqueda y condiciones del viaje es útil.
El artículo cita CMEMS para el oleaje, pero no proporciona una identificación
cartográfica suficientemente precisa para reproducir todas sus máscaras de tierra
y arrecifes. Sus resultados regionales no validan una red mundial.

Reutilizaremos nuestra malla H3 y A* con alternativas. Cambiar toda la malla a una
cuadrícula de 48 vecinos o entrenar DDQN no es requisito para obtener cobertura
mundial. Se conserva la comprobación geométrica de cada segmento y las restricciones
duras antes de evaluar su costo. Una penalización de recompensa no sustituye un
bloqueo. La comparación con DDQN queda como experimento posterior opcional; no se
afirmará que el sistema replica el artículo ni que reproduce sus ahorros de combustible.

## 2. Datos que sí están a nuestro alcance

| Entrada | Disponibilidad comprobada | Uso en esta fase |
| --- | --- | --- |
| Costa GSHHG y malla mundial | Descargadas, con procedencia y artefacto validado | Agua/tierra y conectividad geométrica; no detectan todos los peligros sumergidos |
| UN/LOCODE y comprobación territorial | Disponibles; 1.114 referencias conectadas en el artefacto refinado | Extremos de rutas de investigación; no equivalen a atraques autorizados |
| NOAA Nueva York | Dos celdas, instantánea ampliada de 40 capas y piloto V2 disponibles | Conservar agua cubierta, exclusiones y restricciones; profundidad y permisos desconocidos siguen así |
| AIS regional y modelo O/D | Experimento registrado de septiembre/octubre aprobado | Evidencia regional de semejanza de trayectorias; no autorización de paso |
| UKHO y guías de pasos | Instantánea de 217 elementos y auditoría disponibles | Referencias de investigación y diagnóstico de resolución, respetando sus límites de uso; no autorización operacional |
| Oleaje Copernicus Marine | Producto mundial público identificado; descarga requiere cuenta, acceso local aún no configurado | Dependencia de fase 4, no bloqueo de la geometría mundial de fase 3 |
| Batimetría GEBCO | Distribución mundial pública identificada; integración pendiente | Opcional para investigación posterior; no se convertirá en calado mínimo autorizado |
| Accesos, cierres y permisos mundiales completos | No disponibles ni garantizados con las fuentes y accesos actuales | Fuera de la condición de cierre de esta fase; nunca se declararán verificados |

Fuentes de acceso: [producto de oleaje Copernicus](https://data.marine.copernicus.eu/product/GLOBAL_ANALYSISFORECAST_WAV_001_027/description),
[registro gratuito](https://help.marine.copernicus.eu/en/articles/4220332-how-to-sign-up-for-copernicus-marine-service),
[batimetría GEBCO](https://www.gebco.net/data-products/gridded-bathymetry-data).
No es necesario adquirir AIS mundial ni conseguir todas las cartas del mundo para
cerrar este nuevo alcance. Sí es necesario explicitar qué comprobaciones faltan.

## 3. Base que se conserva

- Global: `data/maritime-routing/global-coastal-r4-audited-v1.zip`, 98.811 nodos,
  2.335.476 aristas dirigidas y 1.114 referencias conectadas. SHA-256:
  `bcc127e48f59d6ff6b6081e8e94de8cff9bdc704873f98dc93be0876aa32a04a`.
- Regional: `noaa-new-york-pilot-v2-20260914.zip`, 2.951 nodos y 16.178 aristas
  dirigidas; 8.089 pares comprobados. El piloto está separado del grafo mundial;
  su integración en ejecución todavía no está hecha.
- Evidencia O/D: septiembre 19/20 casos medibles, máximo Fréchet 769,60 m;
  octubre 13/15, máximo 883,36 m. Ambos pasan sus criterios originales. Los tres
  casos no medibles permanecen en los denominadores. Ver [resultados](od-results-20260921.md).
- A*, alternativas acotadas, controles de calado/permisos y API de investigación
  ya existen. Las pruebas anteriores con artefacto mundial real constituyen una
  referencia; deben repetirse para cualquier nuevo artefacto integrado.

## 4. Trabajo en orden de ejecución

### A. Fijar la política y el contrato de integración

1. Registrar hashes de fuentes, artefactos y geometrías regionales que se usarán.
   Delimitar Nueva York con la cobertura real de las cartas, no con un rectángulo
   improvisado. Separar cobertura, agua admisible, obstáculos y restricciones.
2. Fuera de esa cobertura, permitir investigación geométrica cuando el usuario
   acepta sus límites. Permisos y profundidad ausentes mantienen `UNKNOWN`/`null`.
   `requireKnownLegalStatus=true` y calado solicitado siguen rechazando datos
   insuficientes. Las restricciones conocidas conservan su efecto.
3. Dentro de la cobertura, aplicar los controles regionales a toda geometría que
   la interseque, incluso si sus dos extremos están fuera. Las condiciones sin
   resolver y los accesos actualmente rechazados no se habilitan con el modo mundial.
4. Definir un manifiesto compuesto que vincule grafo y controles regionales a sus
   versiones e informes. Un componente ausente, corrupto o incompatible impide
   activar el conjunto; nunca activa silenciosamente el grafo mundial sin controles.

Entregable: política documentada y contrato versionado. Puntos de implementación:
`GraphCatalog`, `GraphArtifactLoader`, `PhysicalGraph` y `RouteCostPolicy` en
`mapping/routing/v2`. No debilitar la validación actual para cargar el piloto, que
todavía no tiene todos los campos de calidad exigidos por el catálogo mundial.

### B. Construir el conjunto mundial con prioridad regional

1. Crear un artefacto nuevo. Mantener el global y el piloto anteriores intactos.
   Recortar o sustituir los segmentos globales que atraviesen la cobertura regional;
   insertar geometría regional y conectores de frontera solo tras validarlos.
2. Comprobar la geometría completa de aristas y conectores contra tierra y, donde
   corresponde, contra agua/exclusiones regionales. No basta revisar los nodos.
   Aplicar la misma política al camino principal, las alternativas y cualquier
   futura recalculación que use este conjunto.
3. Conservar las referencias portuarias originales. Un punto sin acceso demostrado
   permanece no disponible; no se desplaza al agua ni se inventa una terminal.
4. Medir componentes y disponibilidad antes/después. Toda pérdida de conectividad
   tendrá causa y registro; no se ocultará quitando puertos del censo. Refinar pasos
   estrechos solo cuando exista geometría utilizable. Un canal sin representación
   comprobada puede resultar no disponible; no es obligatorio abrir Suez para cerrar
   esta fase ni se puede crear un atajo a través de tierra.

Entregable: artefacto compuesto, recibos, informe geométrico y censo de cobertura.
La comparación UKHO conserva sus vacíos de representación; no se convierte en una
prueba de cumplimiento mundial ni exige cubrir todas las 60 vías para este alcance.

### C. Exponer la cobertura y las limitaciones de cada ruta

1. Mantener los extremos por identificadores de origen de `/api/v2/maritime/ports`.
   La navegación entre coordenadas arbitrarias no forma parte de esta entrega.
2. Añadir a cada ruta el alcance de investigación, versiones aplicadas y tramos
   con controles regionales o solo comprobación global de costa. Indicar las capas
   ausentes sin asignarles una probabilidad de seguridad.
3. Conservar distancia y alternativas. Tiempo solo con velocidad declarada por el
   usuario, identificado como tiempo a velocidad constante. Oleaje, combustible,
   emisiones y costos contractuales no reciben valores simulados por defecto.
4. Distinguir acceso no disponible, falta de datos exigidos, desconexión y presupuesto
   de búsqueda agotado. Mantener geometrías separadas en el antimeridiano.

Entregable: contrato API actualizado, ejemplos verificables y pruebas HTTP.

### D. Verificar y cerrar el nuevo alcance

Antes de evaluar el conjunto integrado, registrar los casos, identificadores de
origen, reglas, presupuestos y versiones en un protocolo nuevo. Las ocho parejas
del ensayo mundial existente —referencias de JP, NZ, CL, ZA, GB, US, AU y BR— se
fijarán por ID y se conservarán como regresión. No cambiar los extremos después
de ver fallos. Cualquier indisponibilidad legítima deberá quedar diagnosticada y
visible; revisar el protocolo exige otra versión, conservando el resultado anterior.

| Condición obligatoria de cierre | Evidencia requerida |
| --- | --- |
| Geometría global y regional coherente | Comprobar todas las aristas y conectores del conjunto; cero intersecciones no permitidas según las máscaras y tolerancias declaradas. Verificar aristas dirigidas si el nuevo modelo rompe la reciprocidad |
| Cobertura mundial demostrada | Rutas reales entre referencias conectadas en Pacífico, Atlántico e Índico; cruces efectivos del antimeridiano y ecuador; censo completo de puertos y componentes, incluyendo excluidos |
| Nueva York no se puede eludir | Casos positivos de conectividad física donde corresponda y negativos con obstáculos, condiciones pendientes, entrada/salida y arista con extremos fuera que atraviesa la región; ninguna alternativa puede saltarse el control |
| Fallos de datos se detectan | Artefacto regional ausente, hash alterado y versiones incompatibles impiden activar el conjunto; permiso/profundidad desconocidos se rechazan cuando la consulta los exige |
| API coherente | Metadatos por ruta, trazabilidad, restricciones, errores y geometría de antimeridiano comprobados por HTTP; sin autorización implícita ni cifras de combustible inventadas |
| Búsqueda acotada | Casos mundiales registrados terminan dentro del presupuesto de 2.000.000 expansiones; alternativas con k=3, máximo 20 candidatos y separación solicitada de 50 km. Registrar cantidad real, tiempo y memoria; menos de k se explica, no se fabrica |
| Reproducibilidad | Nuevo informe enlaza hashes de entradas, artefacto, implementación, protocolo y resultados. Ejecución documentada y comprobaciones aplicables aprobadas |

La validación costera comparte origen GSHHG: ejecución separada no implica verdad
hidrográfica independiente. La comprobación geométrica mundial no demuestra que
las rutas coincidan con trayectorias reales mundiales. La evidencia AIS de Nueva
York conserva sus propios límites y no se extrapola.

No volver a ajustar el modelo O/D para superar el cierre mundial. Sus resultados
congelados sirven para el modelo y grafo originales; un modelo o grafo modificado
necesita evaluación propia, sin llamar de nuevo independientes a septiembre/octubre.

Entregables de cierre previstos: protocolo nuevo, informe legible y
`phase3-research-status-v2.json`, separado del informe histórico. Solo declarar
`PASS` cuando todas las condiciones obligatorias tengan evidencia del conjunto
real. Este documento no sustituye ese informe ni esas pruebas.

## 5. Dependencias posteriores y estado de ejecución

Con esta fase aprobada, la fase 4 podrá añadir oleaje y modelos de buque. Requiere
obtener acceso de descarga, fijar producto/corrida/fecha/unidades, tratar celdas y
tiempos sin cobertura, y documentar/calibrar la respuesta del buque. No basta copiar
un coeficiente del artículo ni asumir que GEBCO resuelve el margen bajo la quilla.
Las fases contractuales y de incertidumbre conservan sus propios requisitos de datos.

| Bloque | Estado al cierre del 2026-09-22 |
| --- | --- |
| Revisión del artículo, inventario y cambio de alcance | Completados y documentados |
| A. Política implementada y contrato compuesto | Completado; esquema 2, evidencia regional obligatoria y rechazo de incompatibilidades |
| B. Artefacto mundial con prioridad regional | Completado; artefacto v4, transición costera y auditoría completa PASS |
| C. Metadatos y contrato API integrados | Completado; cobertura por intervalos de aristas y pruebas HTTP |
| D. Evaluación y cierre `PHASE3_GLOBAL_RESEARCH_V2` | PASS; nueve recorridos mundiales, 117 pruebas Python y 25 Java |

La decisión práctica es avanzar con los datos existentes. La falta de cartas,
permisos y AIS completos del mundo deja de ser una dependencia de cierre de la
planificación de investigación; permanece como limitación explícita del resultado.
