package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.Coordinates;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.entities.Port;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.documents.PortDocument;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.documents.RouteDocument;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.repositories.PortRepository;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.repositories.RouteRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Lazy(false)
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.seed.mapping.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements ApplicationRunner {

    private final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    private static final double COORDINATE_EPSILON = 0.000001;

    private final PortRepository portRepository;
    private final RouteRepository routeRepository;

    @Value("${app.seed.mapping.reset:false}")
    private boolean resetOnStartup;

    @Autowired
    public DataInitializer(PortRepository portRepository, RouteRepository routeRepository) {
        this.portRepository = portRepository;
        this.routeRepository = routeRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        init();
    }

    public void init() {
        if (resetOnStartup) {
            try {
                logger.info("Reiniciando colecciones de rutas y puertos por app.seed.mapping.reset=true...");
                routeRepository.deleteAll();
                portRepository.deleteAll();
                logger.info("Colecciones reiniciadas exitosamente.");
            } catch (Exception e) {
                logger.error("Error limpiando las colecciones: {}", e.getMessage(), e);
            }
        }

        // =================================================================================
        // PASO 2: INICIALIZAR LOS DATOS DESDE CERO
        // =================================================================================
        logger.info("Iniciando la inserción de datos...");
        initPorts();
        initRoutes();

        // =================================================================================
        // PASO 3: VALIDAR LOS DATOS RECIÉN INSERTADOS
        // =================================================================================
        logger.info("Validando los datos insertados...");
        validateRoutes();
        logger.info("¡Datos inicializados y validados correctamente!");
    }

    private void initPorts() {
        try {
            List<Port> ports = List.of(
                    // ========== ASIA ==========
                    new Port("Goto", new Coordinates(32.697052, 128.852815), "Asia"),
                    new Port("Miyazaki", new Coordinates(31.17, 130.19), "Asia"),
                    new Port("Osaka", new Coordinates(34.631243, 135.407996), "Asia"),
                    new Port("Tokyo", new Coordinates(35.651981, 139.764228), "Asia"),
                    new Port("Busan", new Coordinates(35.1796, 129.0756), "Asia"),
                    new Port("Shanghai", new Coordinates(31.2304, 121.4737), "Asia"),
                    new Port("Tianjin", new Coordinates(39.0842, 117.2010), "Asia"),
                    new Port("Hong Kong", new Coordinates(22.3193, 114.1694), "Asia"),
                    new Port("Quanzhou", new Coordinates(24.9139, 118.5858), "Asia"),
                    new Port("Zhanjiang", new Coordinates(21.1967, 110.4031), "Asia"),
                    new Port("Muscat", new Coordinates(23.6142, 58.5458), "Asia"),
                    new Port("Singapore", new Coordinates(1.3521, 103.8198), "Asia"),
                    new Port("Jakarta", new Coordinates(-6.2088, 106.8456), "Asia"),
                    new Port("Mumbai", new Coordinates(19.0760, 72.8777), "Asia"),
                    new Port("Chennai", new Coordinates(13.0827, 80.2707), "Asia"),
                    new Port("Tuticorin", new Coordinates(8.7642, 78.1348), "Asia"),
                    new Port("Dubai", new Coordinates(25.2769, 55.2962), "Asia"),
                    new Port("Aden", new Coordinates(12.8000, 45.0333), "Asia"),
                    new Port("Vladivostok", new Coordinates(43.1056, 131.8735), "Asia"),
                    new Port("Uelen", new Coordinates(66.1667, -169.8000), "Asia"),
                    new Port("Macasar", new Coordinates(-5.112648, 119.409072), "Asia"),//hacer ruta no te olvides
                    new Port("Muara Port", new Coordinates(5.024628, 115.072012), "Asia"),//hacer ruta no te olvides
                    new Port("Semayang", new Coordinates(-1.273471, 116.805472), "Asia"),//hacer ruta no te olvides
                    new Port("Jeddah", new Coordinates(21.491871, 39.121831), "Asia"),
                    new Port("Nah Trang", new Coordinates(12.204523, 109.218363), "Asia"),

                    // ========== AMÉRICA ==========
                    new Port("Santos", new Coordinates(-23.9500, -46.3333), "América"),
                    new Port("Montevideo", new Coordinates(-34.9033, -56.2000), "América"),
                    new Port("Veracruz", new Coordinates(19.1800, -96.1333), "América"),
                    new Port("Lázaro Cárdenas", new Coordinates(17.9583, -102.2000), "América"),
                    new Port("Seattle", new Coordinates(47.6062, -122.3321), "América"),
                    new Port("Tacoma", new Coordinates(47.2529, -122.4443), "América"),
                    new Port("Savannah", new Coordinates(32.0835, -81.0998), "América"),
                    new Port("Norfolk", new Coordinates(36.8508, -76.2859), "América"),
                    new Port("Kingston", new Coordinates(17.9714, -76.7920), "América"),
                    new Port("Freeport", new Coordinates(26.5333, -78.7000), "América"),
                    new Port("Caucedo", new Coordinates(18.4210, -69.6120), "América"),
                    new Port("Arkits", new Coordinates(73.0, -128.0), "América"),
                    new Port("Callao", new Coordinates(-12.0564, -77.1319), "América"),
                    new Port("Buenos Aires", new Coordinates(-34.6037, -58.3816), "América"),
                    new Port("Valparaíso", new Coordinates(-33.0472, -71.6128), "América"),
                    new Port("Rio de Janeiro", new Coordinates(-22.9068, -43.1729), "América"),
                    new Port("Fort Lauderdale", new Coordinates(26.1224, -80.1373), "América"),
                    new Port("Guayaquil", new Coordinates(-2.1962, -79.8862), "América"),
                    new Port("Balboa", new Coordinates(8.939008, -79.555637), "América"),
                    new Port("Manzanillo", new Coordinates(19.0514, -104.3158), "América"),
                    new Port("Long Beach", new Coordinates(33.7709, -118.1937), "América"),
                    new Port("New York", new Coordinates(40.702714, -74.005678), "América"),
                    new Port("Houston", new Coordinates(29.7604, -95.3698), "América"),
                    new Port("San Francisco", new Coordinates(37.7749, -122.4194), "América"),
                    new Port("Vancouver", new Coordinates(49.2827, -123.1207), "América"),
                    new Port("Prince Rupert", new Coordinates(54.3150, -130.3208), "América"),
                    new Port("Cayena", new Coordinates(4.9372, -52.3260), "América"),
                    new Port("Cartagena", new Coordinates(10.403063, -75.533398), "América"),
                    new Port("Puerto Cabello", new Coordinates(10.476070, -67.995836), "América"),
                    new Port("San Antonio", new Coordinates(-33.5983, -71.6123), "América"),
                    new Port("Montreal", new Coordinates(45.5017, -73.5673), "América"),
                    new Port("Rio Grande", new Coordinates(-32.0351, -52.0986), "América"),
                    new Port("Puerto Montt", new Coordinates(-41.4718, -72.9396), "América"),
                    new Port("Chancay", new Coordinates(-11.5903, -77.2761), "América"),
                    new Port("Necochea y Quequén", new Coordinates(-38.5780, -58.700), "América"),
                    new Port("Ushuaia", new Coordinates(-54.810668,-68.296487), "América"),
                    new Port("Stanley", new Coordinates(-51.692358, -57.860840), "América"),
                    new Port("Santo Domingo", new Coordinates(18.453811, -69.948417), "América"),
                    new Port("La Habana", new Coordinates(23.149916, -82.372406), "América"),

                    // ========== ÁFRICA ==========
                    new Port("Ciudad del Cabo", new Coordinates(-33.9249, 18.4241), "África"),
                    new Port("Abiyán", new Coordinates(5.3599, -4.0084), "África"),
                    new Port("Durban", new Coordinates(-29.8833, 31.0500), "África"),
                    new Port("Mombasa", new Coordinates(-4.0435, 39.6682), "África"),
                    new Port("Toamasina", new Coordinates(-18.1443, 49.3958), "África"),
                    new Port("Port-Gentil", new Coordinates(-0.7167, 8.7833), "África"),
                    new Port("Nuakchot", new Coordinates(18.0731, -15.9582), "África"),
                    new Port("Dakar", new Coordinates(14.7167, -17.4677), "África"),
                    new Port("Lagos", new Coordinates(6.4541, 3.3947), "África"),
                    new Port("Walvis Bay", new Coordinates(-22.9587, 14.5058), "África"),
                    new Port("Luanda", new Coordinates(-8.8383, 13.2344), "África"),
                    new Port("Alexandría", new Coordinates(31.2001, 29.9187), "África"),
                    new Port("Casablanca", new Coordinates(33.5731, -7.5898), "África"),
                    new Port("Mogadishu", new Coordinates(2.0469, 45.3182), "África"),
                    new Port("Freetown", new Coordinates(8.497950, -13.291969), "África"),
                    new Port("Tema", new Coordinates(5.6333, 0.0167), "África"),
                    new Port("Monrovia", new Coordinates(6.345634, -10.797214), "África"),
                    new Port("Conakri", new Coordinates(9.523226, -13.705952), "África"),
                    new Port("Lomé", new Coordinates(6.1375, 1.2228), "África"),
                    new Port("Cotonou", new Coordinates(6.3654, 2.4183), "África"),
                    new Port("Beira", new Coordinates(-19.8436, 34.8389), "África"),
                    new Port("Maputo", new Coordinates(-25.9653, 32.5892), "África"),
                    new Port("Tanger Med", new Coordinates(35.8844, -5.5036), "África"),

                    // ========== EUROPA ==========
                    new Port("Livorno", new Coordinates(43.550306, 10.298719), "Europa"),
                    new Port("Odessa", new Coordinates(46.4825, 30.7233), "Europa"),
                    new Port("Murmansk", new Coordinates(68.9585, 33.0827), "Europa"),
                    new Port("Estambul", new Coordinates(41.0082, 28.9784), "Europa"),
                    new Port("Constanza", new Coordinates(44.1598, 28.6348), "Europa"),
                    new Port("Mersin", new Coordinates(36.7950, 34.6179), "Europa"),
                    new Port("Lisboa", new Coordinates(38.7223, -9.1393), "Europa"),
                    new Port("Valencia", new Coordinates(39.4699, -0.3763), "Europa"),
                    new Port("Hamburgo", new Coordinates(53.5488, 9.9872), "Europa"),
                    new Port("Rotterdam", new Coordinates(51.9244, 4.4777), "Europa"),
                    new Port("Le Havre", new Coordinates(49.4944, 0.1089), "Europa"),
                    new Port("Genova", new Coordinates(44.4056, 8.9463), "Europa"),
                    new Port("Venecia", new Coordinates(45.442374, 12.301968), "Europa"),
                    new Port("Bari", new Coordinates(41.126746, 16.888232), "Europa"),
                    new Port("Napoli", new Coordinates(40.833109, 14.268100), "Europa"),
                    new Port("Palermo", new Coordinates(38.128761, 13.367153), "Europa"),
                    new Port("Atenas", new Coordinates(37.9838, 23.7275), "Europa"),
                    new Port("San Petersburgo", new Coordinates(59.9343, 30.3351), "Europa"),
                    new Port("Copenhague", new Coordinates(55.6761, 12.5683), "Europa"),
                    new Port("Stavanger", new Coordinates(58.9699, 5.7331), "Europa"),
                    new Port("Eupatoria", new Coordinates(45.2000, 33.3583), "Europa"),

                    // ========== OCEANÍA ==========
                    new Port("Sídney", new Coordinates(-33.8688, 151.2093), "Oceanía"),
                    new Port("Brisbane", new Coordinates(-27.4698, 153.0251), "Oceanía"),
                    new Port("Fremantle", new Coordinates(-32.0564, 115.7417), "Oceanía"),
                    new Port("Darwin", new Coordinates(-12.4634, 130.8456), "Oceanía"),
                    new Port("Port Moresby", new Coordinates(-9.4438, 147.1803), "Oceanía"),
                    new Port("Melbourne", new Coordinates(-37.8136, 144.9631), "Oceanía"),
                    new Port("Auckland", new Coordinates(-36.8509, 174.7645), "Oceanía"),
                    new Port("Hedland", new Coordinates(-20.297171, 118.595167), "Oceanía"),

                    // ... (después de los puertos existentes de América)
                    new Port("Colón", new Coordinates(9.3582, -79.9015), "América"),
                    new Port("Acajutla", new Coordinates(13.574724, -89.834303), "América"),
                    // ... (después de los puertos existentes de África)
                    new Port("Trípoli", new Coordinates(32.8872, 13.1913), "África"),
                    new Port("Túnez", new Coordinates(36.8065, 10.1815), "África"),

                    // ... (después de los puertos existentes de Asia)
                    new Port("Pyongyang", new Coordinates(39.0392, 125.7625), "Asia"),
                    new Port("Taiwán", new Coordinates(25.143604, 121.756390), "Asia"),
                    new Port("Puerto de Beirut", new Coordinates(33.8938, 35.5018), "Asia"),
                    new Port("Latakia", new Coordinates(35.5236, 35.7877), "Asia"),
                    new Port("Puerto de Haifa", new Coordinates(32.8184, 34.9895), "Asia"),
                    new Port("Puerto Said", new Coordinates(31.2653, 32.3019), "Asia"),
                    new Port("Puerto de Crimea", new Coordinates(45.3481, 34.4993), "Europa")
            );

            List<PortDocument> portsToSave = new ArrayList<>();
            int inserted = 0;
            int updated = 0;
            int unchanged = 0;
            for (Port port : ports) {
                Optional<PortDocument> existing = portRepository.findByNameAndContinent(port.getName(), port.getContinent());
                if (existing.isEmpty()) {
                    portsToSave.add(toPortDocument(port));
                    inserted++;
                    continue;
                }

                PortDocument document = existing.get();
                if (coordinatesChanged(document.getCoordinates(), port.getCoordinates())) {
                    document.setCoordinates(new PortDocument.CoordinatesDocument(
                            port.getCoordinates().latitude(),
                            port.getCoordinates().longitude()
                    ));
                    portsToSave.add(document);
                    updated++;
                } else {
                    unchanged++;
                }
            }

            if (!portsToSave.isEmpty()) {
                portRepository.saveAll(portsToSave);
                logger.info("Puertos semilla sincronizados. insertados={} actualizados={} sinCambios={} total={}",
                        inserted, updated, unchanged, ports.size());
            } else {
                logger.info("Puertos semilla ya sincronizados. total={}", ports.size());
            }
        } catch (Exception e) {
            logger.error("Error inicializando puertos: {}", e.getMessage(), e);
        }
    }

    private void initRoutes() {
        try {
            List<RouteDocument> routes = List.of(
                    // ========== Rutas Asiáticas ==========
                    new RouteDocument("Shanghai", "Asia", "Busan", "Asia", 850.0),
                    new RouteDocument("Shanghai", "Asia", "Quanzhou", "Asia", 700.0),
                    new RouteDocument("Hong Kong", "Asia", "Quanzhou", "Asia", 800.0),
                    new RouteDocument("Hong Kong", "Asia", "Singapore", "Asia", 2600.0),
                    new RouteDocument("Singapore", "Asia", "Chennai", "Asia", 2700.0),
                    new RouteDocument("Tuticorin", "Asia", "Chennai", "Asia", 2700.0),
                    new RouteDocument("Singapore", "Asia", "Jakarta", "Asia", 900.0),
                    new RouteDocument("Goto", "Asia", "Busan", "Asia",  145.4),
                    new RouteDocument("Nah Trang", "Asia", "Zhanjiang", "Asia", 548.2),
                    new RouteDocument("Shanghai", "Asia", "Goto", "Asia", 382.3),
                    new RouteDocument("Miyazaki", "Asia", "Goto", "Asia", 140.7),
                    new RouteDocument("Osaka", "Asia", "Tokyo", "Asia", 222.7),
                    new RouteDocument("Osaka", "Asia", "Miyazaki", "Asia", 257.1),
                    new RouteDocument("Mumbai", "Asia", "Muscat", "Asia", 1800.0),
                    new RouteDocument("Mumbai", "Asia", "Tuticorin", "Asia", 284.4),
                    new RouteDocument("Mumbai", "Asia", "Dubai", "Asia", 1592.0),
                    new RouteDocument("Aden", "Asia", "Muscat", "Asia", 1500.0),
                    new RouteDocument("Semayang", "Asia", "Macasar", "Asia", 281.3),
                    new RouteDocument("Muara Port", "Asia", "Singapore", "Asia", 709.1),
                    new RouteDocument("Zhanjiang", "Asia", "Singapore", "Asia", 1262.2),
                    new RouteDocument("Zhanjiang", "Asia", "Hong Kong", "Asia", 220.0),
                    new RouteDocument("Zhanjiang", "Asia", "Taiwán", "Asia", 659.2),
                    new RouteDocument("Taiwán", "Asia", "Tokyo", "Asia", 1138.1),
                    new RouteDocument("Taiwán", "Asia", "Hong Kong", "Asia", 439.3),

                    // ========== Rutas Oceánicas ==========
                    new RouteDocument("Jakarta", "Asia", "Darwin", "Oceanía", 2700.0),
                    new RouteDocument("Darwin", "Oceanía", "Brisbane", "Oceanía", 3300.0),
                    new RouteDocument("Darwin", "Oceanía", "Port Moresby", "Oceanía", 1800.0),
                    new RouteDocument("Port Moresby", "Oceanía", "Brisbane", "Oceanía", 2500.0),
                    new RouteDocument("Brisbane", "Oceanía", "Sídney", "Oceanía", 900.0),
                    new RouteDocument("Hedland", "Oceanía", "Darwin", "Oceanía", 847.4),


                    // ========== Rutas Transpacíficas ==========
                    new RouteDocument("San Francisco", "América", "Tokyo", "Asia", 8500.0),
                    new RouteDocument("Tokyo", "Asia", "Balboa", "América", 12500.0),
                    new RouteDocument("Singapore", "Asia", "Buenos Aires", "América", 8581.4),

                    // ========== Rutas Americanas ==========
                    new RouteDocument("San Francisco", "América", "Balboa", "América", 3200.0),
                    new RouteDocument("San Francisco", "América", "Manzanillo", "América", 2100.0),
                    new RouteDocument("Long Beach", "América", "San Francisco", "América", 400.0),
                    new RouteDocument("Balboa", "América", "Guayaquil", "América", 2100.0),
                    new RouteDocument("Guayaquil", "América", "Callao", "América", 1150.0),
                    new RouteDocument("Puerto Cabello", "América", "Cartagena", "América", 200.0),
                    new RouteDocument("San Antonio", "América", "Valparaíso", "América", 1100.0),
                    new RouteDocument("Santo Domingo","América","Cartagena","América",581.9),
                    new RouteDocument("Santo Domingo","América","Puerto Cabello","América", 494.0),
                    new RouteDocument("La Habana","América","Santo Domingo","América", 753.7),
                    new RouteDocument("La Habana","América","Fort Lauderdale","América", 216.5),
                    new RouteDocument("La Habana","América","Houston","América", 803.4),
                    new RouteDocument("Cartagena", "América", "Balboa", "América", 800.0),
                    new RouteDocument("Callao", "América", "Valparaíso", "América", 1500.0),
                    new RouteDocument("Cayena","América","Rio de Janeiro","América",4462.0),
                    new RouteDocument("Cayena","América","Puerto Cabello","América",3131.0),
                    new RouteDocument("Balboa", "América", "Manzanillo", "América", 2700.0),

                    new RouteDocument("Manzanillo", "América", "Long Beach", "América", 1152.6),
                    new RouteDocument("Lázaro Cárdenas", "América", "Acajutla", "América",  763.2),
                    new RouteDocument("Balboa", "América", "Acajutla", "América",  665.9),

                    new RouteDocument("Manzanillo", "América", "Lázaro Cárdenas", "América", 2800.0),
                    new RouteDocument("New York", "América", "Fort Lauderdale", "América", 1600.0),
                    new RouteDocument("Vancouver", "América", "San Francisco", "América", 1900.0),
                   //nuevo
                    new RouteDocument("Long Beach", "América", "San Francisco", "América", 400.0),
                    new RouteDocument("San Francisco", "América", "Vancouver", "América", 1300.0),
                    new RouteDocument("Vancouver", "América", "Prince Rupert", "América", 800.0),
                    new RouteDocument("Prince Rupert", "América", "Uelen", "Asia", 1342.3),
                    new RouteDocument("Balboa", "América", "Guayaquil", "América", 800.0),
                    new RouteDocument("Chancay", "América", "Callao", "América",  29.7),
                    new RouteDocument("Chancay", "América", "Guayaquil", "América",  585.6),

                    // Vía estrecho de Magallanes
                    new RouteDocument("San Antonio", "América", "Puerto Montt", "América", 476.0),
                    new RouteDocument("Buenos Aires", "América", "Necochea y Quequén", "América", 239.5),
                    new RouteDocument("Necochea y Quequén", "América", "Stanley", "América",  788.9),
                    new RouteDocument("Stanley", "América", "Ushuaia", "América",  419.3),
                    new RouteDocument("Ushuaia", "América", "Puerto Montt", "América",  824.2),
                    new RouteDocument("Santos", "América", "Rio de Janeiro", "América",186.8),
                    new RouteDocument("Santos", "América", "Rio Grande", "América",570.7),
                    new RouteDocument("Rio Grande", "América", "Montevideo", "América",269.9),
                    new RouteDocument("Buenos Aires", "América", "Montevideo", "América",105.5),
                    new RouteDocument("New York", "América", "Montreal", "América", 600.0),
                    new RouteDocument("New York", "América", "Houston", "América", 2300.0),
                    new RouteDocument("Houston", "América", "Colón", "América", 2500.0),
                    new RouteDocument("Colón", "América", "Balboa", "América", 32.6),
                    new RouteDocument("Colón", "América", "Cartagena", "América", 500.0),
                    new RouteDocument("Cartagena", "América", "Puerto Cabello", "América", 600.0),
                    new RouteDocument("Puerto Cabello", "América", "Cayena", "América", 1500.0),

                    // ========== Rutas Áfricanas ==========
                    new RouteDocument("Walvis Bay", "África", "Luanda", "África", 1800.0),
                    new RouteDocument("Mogadishu", "África", "Aden", "Asia", 1200.0),
                    new RouteDocument("Ciudad del Cabo", "África", "Walvis Bay", "África", 1300.0),
                    new RouteDocument("Dakar", "África", "Casablanca", "África", 2600.0),
                    new RouteDocument("Lagos", "África", "Luanda", "África", 2400.0),
                    new RouteDocument("Tanger Med", "África", "Lisboa", "Europa", 237.8),
                    new RouteDocument("Tanger Med", "África", "Valencia", "Europa",  341.3),
                    new RouteDocument("Houston", "América", "New York", "América", 2500.0),
                    new RouteDocument("Walvis Bay", "África", "Luanda", "África", 1800.0),
                    new RouteDocument("Cotonou", "África", "Lagos", "África", 58.1),
                    new RouteDocument("Cotonou", "África", "Lomé", "África", 81.6),
                    new RouteDocument("Tema", "África", "Abiyán", "África", 242.2),
                    new RouteDocument("Mogadishu", "África", "Aden", "Asia", 1200.0),
                    new RouteDocument("Ciudad del Cabo", "África", "Walvis Bay", "África", 1300.0),
                    new RouteDocument("Dakar", "África", "Casablanca", "África", 2600.0),
                    new RouteDocument("Lagos", "África", "Luanda", "África", 2400.0),
                    new RouteDocument("Dakar", "África", "Casablanca", "África", 2600.0),
                    new RouteDocument("Lagos", "África", "Luanda", "África", 2400.0),
                    new RouteDocument("Abiyán", "África", "Lagos", "África", 1000.0),
                    new RouteDocument("Luanda", "África", "Ciudad del Cabo", "África", 2500.0),
                    new RouteDocument("Ciudad del Cabo", "África", "Durban", "África", 1300.0),
                    new RouteDocument("Durban", "África", "Mombasa", "África", 3200.0),
                    new RouteDocument("Mombasa", "África", "Mogadishu", "África", 1000.0),
                    new RouteDocument("Monrovia", "África", "Freetown", "África", 194.2),
                    new RouteDocument("Monrovia", "África", "Abiyán", "África", 410.1),
                    new RouteDocument("Freetown", "África", "Conakri", "África", 66.6),
                    // --- RUTA PUENTE ---
                    new RouteDocument("Luanda", "África", "Ciudad del Cabo", "África", 2500.0), // Conecta el centro-oeste con el sur
                    new RouteDocument("Ciudad del Cabo", "África", "Durban", "África", 1300.0), // Conecta el sur con el este
                    new RouteDocument("Durban", "África", "Mombasa", "África", 3200.0),
                    new RouteDocument("Mombasa", "África", "Mogadishu", "África", 1000.0),

                    // ========== Rutas Europeas ==========
                    new RouteDocument("Stavanger", "Europa", "Murmansk", "Europa", 2800.0),
                    new RouteDocument("Stavanger", "Europa", "Rotterdam", "Europa", 426.0),
                    new RouteDocument("Eupatoria", "Europa", "Estambul", "Europa", 800.0),
                    new RouteDocument("Lisboa", "Europa", "Casablanca", "África", 900.0),
                    new RouteDocument("Genova", "Europa", "Atenas", "Europa", 1800.0),
                    new RouteDocument("Livorno", "Europa", "Napoli", "Europa",240.7),
                    new RouteDocument("Genova", "Europa", "Napoli", "Europa", 318.6),
                    new RouteDocument("Palermo", "Europa", "Napoli", "Europa", 169.3),
                    new RouteDocument("Venecia", "Europa", "Bari", "Europa", 327.0),
                    new RouteDocument("Hamburgo", "Europa", "Rotterdam", "Europa", 600.0),
                    new RouteDocument("San Petersburgo", "Europa", "Murmansk", "Europa", 1700.0),
                    new RouteDocument("Hamburgo", "Europa", "Rotterdam", "Europa", 450.0),
                    new RouteDocument("Rotterdam", "Europa", "Le Havre", "Europa", 500.0),
                    new RouteDocument("Le Havre", "Europa", "Lisboa", "Europa", 1300.0),
                    new RouteDocument("Livorno", "Europa", "Genova", "Europa", 78.9),
                    new RouteDocument("Valencia", "Europa", "Genova", "Europa", 900.0),
                    new RouteDocument("Hamburgo", "Europa", "Copenhague", "Europa", 500.0),
                    new RouteDocument("Copenhague", "Europa", "San Petersburgo", "Europa", 1200.0),
                    new RouteDocument("San Petersburgo", "Europa", "Murmansk", "Europa", 1400.0),
                    new RouteDocument("Copenhague", "Europa", "Stavanger", "Europa", 500.0),

                    // ========== Rutas África-Asia ==========
                    new RouteDocument("Mombasa", "África", "Mumbai", "Asia", 4300.0),
                    new RouteDocument("Durban", "África", "Chennai", "Asia", 6800.0),

                    // ========== Rutas Mediterráneas ==========
                    new RouteDocument("Atenas", "Europa", "Alexandría", "África", 1100.0),

                    // Rutas Árticas (Uelen/Arkits)
                    new RouteDocument("Uelen", "Asia", "Arkits", "América", 2500.0),
                    new RouteDocument("Uelen", "Asia", "Tokyo", "Asia", 2527.1),
                    new RouteDocument("Uelen", "Asia", "Vancouver", "América", 1745.3),
                    new RouteDocument("Murmansk", "Europa", "Arkits", "América", 2200.0),
                    // --- CONEXIONES PARA LOS PUERTOS AISLADOS ---
                    new RouteDocument("Nuakchot", "África", "Dakar", "África", 500.0),
                    new RouteDocument("Abiyán", "África", "Lagos", "África", 1000.0),

                    // --- PUENTES DENTRO DE ÁFRICA ---
                    new RouteDocument("Casablanca", "África", "Lisboa", "Europa", 900.0),
                    new RouteDocument("Dakar", "África", "Cayena", "América", 4000.0), // Un puente transatlántico alternativo
                    new RouteDocument("Lagos", "África", "Port-Gentil", "África", 800.0),
                    new RouteDocument("Port-Gentil", "África", "Luanda", "África", 900.0),
                    new RouteDocument("Luanda", "África", "Ciudad del Cabo", "África", 2500.0),
                    new RouteDocument("Ciudad del Cabo", "África", "Durban", "África", 1300.0),
                    new RouteDocument("Durban", "África", "Toamasina", "África", 2600.0),
                    new RouteDocument("Durban", "África", "Mombasa", "África", 3200.0),
                    new RouteDocument("Mombasa", "África", "Mogadishu", "África", 1000.0),
                    // ========== Rutas Troncales Transoceánicas (Puentes Globales) ==========
                    new RouteDocument("San Francisco", "América", "Tokyo", "Asia",8500.0),
                    new RouteDocument("Lisboa", "Europa", "Cayena", "América", 3103.3),
                    new RouteDocument("Singapore", "Asia", "Rio de Janeiro", "América", 8496.7),
                    new RouteDocument("Lisboa", "Europa", "New York", "América",  2928.6),
                    new RouteDocument("Lisboa", "Europa", "Puerto Cabello", "América",  3566.0),

                    new RouteDocument("Dakar", "África", "Cayena", "América", 4000.0),
                    new RouteDocument("Ciudad del Cabo", "África", "Buenos Aires", "América", 6900.0),
                    new RouteDocument("Sídney", "Oceanía", "Valparaíso", "América", 11000.0),
                    new RouteDocument("Mombasa", "África", "Mumbai", "Asia", 4300.0),

                    // ========== Rutas Locales (Conectando los nuevos puertos) ==========
                    new RouteDocument("Puerto Said", "Asia", "Alexandría", "África", 200.0),
                    new RouteDocument("Puerto Said", "Asia", "Jeddah", "Asia", 692.2),
                    new RouteDocument("Jeddah", "Asia", "Aden", "Asia", 623.6),
                    new RouteDocument("Puerto de Haifa", "Asia", "Atenas", "Europa", 1200.0),
                    new RouteDocument("Latakia", "Asia", "Mersin", "Europa", 200.0),
                    new RouteDocument("Puerto de Beirut", "Asia", "Puerto de Haifa", "Asia", 150.0),
                    new RouteDocument("Puerto de Crimea", "Europa", "Estambul", "Europa", 600.0),
                    new RouteDocument("Odessa", "Europa", "Constanza", "Europa", 350.0),
                    new RouteDocument("Túnez", "África", "Valencia", "Europa", 900.0),
                    new RouteDocument("Túnez", "África", "Atenas", "Europa", 644.9),
                    new RouteDocument("Túnez", "África", "Palermo", "Europa", 169.3),
                    new RouteDocument("Trípoli", "África", "Genova", "Europa", 718.5),
                    new RouteDocument("Trípoli", "África", "Bari", "Europa", 526.6),
                    new RouteDocument("Trípoli", "África", "Túnez", "África", 277.5),
                    new RouteDocument("Taiwán", "Asia", "Shanghai", "Asia", 700.0),
                    new RouteDocument("Pyongyang", "Asia", "Tianjin", "Asia", 600.0),

                       // --- Conectando África ---
                    new RouteDocument("Casablanca", "África", "Dakar", "África", 2600.0),
                    new RouteDocument("Dakar", "África", "Abiyán", "África", 1800.0),
                    new RouteDocument("Abiyán", "África", "Lagos", "África", 1000.0),
                    new RouteDocument("Lagos", "África", "Luanda", "África", 2400.0),
                    new RouteDocument("Luanda", "África", "Walvis Bay", "África", 1300.0),
                    new RouteDocument("Walvis Bay", "África", "Ciudad del Cabo", "África", 1300.0),
                    new RouteDocument("Aden", "Asia", "Mogadishu", "África", 1200.0),
                    new RouteDocument("Atenas", "Europa", "Estambul", "Europa", 700.0),

                    new RouteDocument("Estambul", "Europa", "Constanza", "Europa", 400.0), // Conecta el Mediterráneo con el Mar Negro

                    new RouteDocument("Hamburgo", "Europa", "Copenhague", "Europa", 500.0)
            );

            List<RouteDocument> distinctRoutes = routes.stream()
                    .collect(Collectors.toMap(
                            this::routeKey,
                            route -> route,
                            (existing, replacement) -> replacement,
                            java.util.LinkedHashMap::new
                    ))
                    .values()
                    .stream()
                    .toList();

            List<RouteDocument> routesToSave = new ArrayList<>();
            int inserted = 0;
            int updated = 0;
            int unchanged = 0;
            int duplicatesRemoved = 0;
            for (RouteDocument route : distinctRoutes) {
                List<RouteDocument> existingRoutes = routeRepository.findAllByHomePortAndHomePortContinentAndDestinationPortAndDestinationPortContinent(
                        route.getHomePort(),
                        route.getHomePortContinent(),
                        route.getDestinationPort(),
                        route.getDestinationPortContinent()
                );
                if (existingRoutes.isEmpty()) {
                    routesToSave.add(route);
                    inserted++;
                    continue;
                }

                RouteDocument document = existingRoutes.get(0);
                if (existingRoutes.size() > 1) {
                    List<RouteDocument> duplicates = existingRoutes.subList(1, existingRoutes.size());
                    routeRepository.deleteAll(duplicates);
                    duplicatesRemoved += duplicates.size();
                    logger.warn("Rutas semilla duplicadas removidas. route={} duplicates={}",
                            routeKey(route), duplicates.size());
                }

                if (routeChanged(document, route)) {
                    document.setHomePortContinent(route.getHomePortContinent());
                    document.setDestinationPortContinent(route.getDestinationPortContinent());
                    document.setDistance(route.getDistance());
                    routesToSave.add(document);
                    updated++;
                } else {
                    unchanged++;
                }
            }

            if (!routesToSave.isEmpty()) {
                routeRepository.saveAll(routesToSave);
            }
            logger.info("Rutas semilla sincronizadas. insertadas={} actualizadas={} sinCambios={} duplicadasRemovidas={} total={}",
                    inserted, updated, unchanged, duplicatesRemoved, distinctRoutes.size());

        } catch (Exception e) {
            logger.error("Error fatal inicializando rutas: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
    private void validateRoutes() {
        List<RouteDocument> routes = routeRepository.findAll();
        for (RouteDocument route : routes) {
            Optional<PortDocument> homePortExists = portRepository.findByNameAndContinent(route.getHomePort(), route.getHomePortContinent());
            Optional<PortDocument> destinationPortExists = portRepository.findByNameAndContinent(route.getDestinationPort(), route.getDestinationPortContinent());

            if (homePortExists.isEmpty()) {
                throw new IllegalStateException("Ruta inválida (Puerto de Origen no existe): " + route.getHomePort()
                        + " (" + route.getHomePortContinent() + ")");
            }

            if (destinationPortExists.isEmpty()) {
                throw new IllegalStateException("Ruta inválida (Puerto de Destino no existe): " + route.getDestinationPort()
                        + " (" + route.getDestinationPortContinent() + ")");
            }
        }
    }

    private PortDocument toPortDocument(Port port) {
        return new PortDocument(
                port.getName(),
                new PortDocument.CoordinatesDocument(
                        port.getCoordinates().latitude(),
                        port.getCoordinates().longitude()
                ),
                port.getContinent()
        );
    }

    private boolean coordinatesChanged(PortDocument.CoordinatesDocument current, Coordinates expected) {
        return current == null
                || Math.abs(current.getLatitude() - expected.latitude()) > COORDINATE_EPSILON
                || Math.abs(current.getLongitude() - expected.longitude()) > COORDINATE_EPSILON;
    }

    private String routeKey(RouteDocument route) {
        return route.getHomePort()
                + "|" + route.getHomePortContinent()
                + "|" + route.getDestinationPort()
                + "|" + route.getDestinationPortContinent();
    }

    private boolean routeChanged(RouteDocument current, RouteDocument expected) {
        return !java.util.Objects.equals(current.getHomePortContinent(), expected.getHomePortContinent())
                || !java.util.Objects.equals(current.getDestinationPortContinent(), expected.getDestinationPortContinent())
                || !java.util.Objects.equals(current.getDistance(), expected.getDistance());
    }
}
