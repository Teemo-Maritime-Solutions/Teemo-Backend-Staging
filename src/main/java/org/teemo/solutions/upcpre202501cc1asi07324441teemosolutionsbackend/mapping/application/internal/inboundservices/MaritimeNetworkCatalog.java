package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices;

import org.springframework.stereotype.Component;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.entities.Port;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.Coordinates;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNode;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNodeType;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class MaritimeNetworkCatalog {

    private final Map<String, MaritimeNode> coreNodes = new LinkedHashMap<>();
    private final List<EdgeDefinition> coreEdges;

    public MaritimeNetworkCatalog() {
        register("NORTH_PACIFIC_WEST", "North Pacific West Corridor", MaritimeNodeType.SEA_WAYPOINT, 37.0, 154.0);
        register("NORTH_PACIFIC_CENTRAL", "North Pacific Central Corridor", MaritimeNodeType.SEA_WAYPOINT, 34.0, 175.0);
        register("NORTH_PACIFIC_EAST", "North Pacific East Corridor", MaritimeNodeType.SEA_WAYPOINT, 33.0, -150.0);
        register("PACIFIC_NORTHEAST", "North America Pacific Approach", MaritimeNodeType.SEA_WAYPOINT, 31.0, -128.0);
        register("PACIFIC_TROPICAL_EAST", "Eastern Tropical Pacific Corridor", MaritimeNodeType.SEA_WAYPOINT, 13.0, -102.0);
        register("PACIFIC_SOUTH_EAST", "South America Pacific Corridor", MaritimeNodeType.SEA_WAYPOINT, -10.0, -88.0);
        register("SOUTH_PACIFIC_EAST", "South Pacific East Corridor", MaritimeNodeType.SEA_WAYPOINT, -28.0, -116.0);
        register("SOUTH_PACIFIC_CENTRAL", "South Pacific Central Corridor", MaritimeNodeType.SEA_WAYPOINT, -28.0, -155.0);
        register("SOUTH_PACIFIC_WEST", "South Pacific West Corridor", MaritimeNodeType.SEA_WAYPOINT, -24.0, 168.0);

        register("JAPAN_EAST_APPROACH", "Japan East Offshore", MaritimeNodeType.SEA_WAYPOINT, 36.0, 144.5);
        register("JAPAN_SOUTH_APPROACH", "Japan South Offshore", MaritimeNodeType.SEA_WAYPOINT, 31.5, 139.5);
        register("EAST_CHINA_SEA_COAST", "East China Sea Coastal Lane", MaritimeNodeType.SEA_WAYPOINT, 29.0, 126.0);
        register("TAIWAN_EAST_APPROACH", "Taiwan East Offshore", MaritimeNodeType.SEA_WAYPOINT, 23.0, 123.5);
        register("PHILIPPINE_SEA_NORTH", "Philippine Sea North", MaritimeNodeType.SEA_WAYPOINT, 18.0, 128.0);
        register("SOUTH_CHINA_SEA_NORTH", "South China Sea North", MaritimeNodeType.SEA_WAYPOINT, 18.0, 116.0);
        register("VIETNAM_COAST", "Vietnam Coastal Lane", MaritimeNodeType.SEA_WAYPOINT, 12.0, 109.0);
        register("ANDAMAN_SEA", "Andaman Sea Lane", MaritimeNodeType.SEA_WAYPOINT, 9.0, 96.0);
        register("MALACCA_WEST", "Malacca Western Approach", MaritimeNodeType.STRAIT, 5.0, 97.0);
        register("MALACCA_STRAIT", "Strait of Malacca", MaritimeNodeType.STRAIT, 2.5, 101.0);
        register("MALACCA_SOUTH", "Malacca Southern Exit", MaritimeNodeType.SEA_WAYPOINT, 1.3, 104.0);
        register("SOUTH_CHINA_SEA", "South China Sea Main Corridor", MaritimeNodeType.SEA_WAYPOINT, 10.0, 112.0);
        register("EAST_CHINA_SEA", "East China Sea Main Corridor", MaritimeNodeType.SEA_WAYPOINT, 27.0, 125.0);
        register("JAVA_SEA", "Java Sea Corridor", MaritimeNodeType.SEA_WAYPOINT, -6.0, 114.0);
        register("CELEBES_SEA", "Celebes Sea Corridor", MaritimeNodeType.SEA_WAYPOINT, 3.0, 121.0);
        register("ARAFURA_SEA", "Arafura Sea Corridor", MaritimeNodeType.SEA_WAYPOINT, -10.0, 132.0);
        register("CORAL_SEA", "Coral Sea Corridor", MaritimeNodeType.SEA_WAYPOINT, -16.0, 154.0);
        register("TASMAN_SEA", "Tasman Sea Corridor", MaritimeNodeType.SEA_WAYPOINT, -34.0, 152.0);

        register("GULF_OF_OMAN", "Gulf of Oman", MaritimeNodeType.SEA_WAYPOINT, 23.5, 60.5);
        register("ARABIAN_SEA", "Arabian Sea Main Corridor", MaritimeNodeType.SEA_WAYPOINT, 17.0, 65.0);
        register("SRI_LANKA_SOUTH", "Sri Lanka South Corridor", MaritimeNodeType.SEA_WAYPOINT, 5.5, 80.5);
        register("BAY_OF_BENGAL_WEST", "Bay of Bengal West Corridor", MaritimeNodeType.SEA_WAYPOINT, 13.0, 84.0);
        register("BAY_OF_BENGAL", "Bay of Bengal Main Corridor", MaritimeNodeType.SEA_WAYPOINT, 14.0, 88.0);
        register("BAY_OF_BENGAL_EAST", "Bay of Bengal East Corridor", MaritimeNodeType.SEA_WAYPOINT, 12.0, 91.0);
        register("INDIAN_OCEAN_CENTRAL", "Central Indian Ocean Corridor", MaritimeNodeType.SEA_WAYPOINT, -5.0, 74.0);
        register("INDIAN_OCEAN_EAST", "Eastern Indian Ocean Corridor", MaritimeNodeType.SEA_WAYPOINT, -10.0, 95.0);
        register("SOUTH_INDIAN_EAST", "South Indian Ocean East Corridor", MaritimeNodeType.SEA_WAYPOINT, -31.0, 105.0);
        register("MADAGASCAR_EAST", "Madagascar East Corridor", MaritimeNodeType.SEA_WAYPOINT, -21.0, 50.0);
        register("MOZAMBIQUE_CHANNEL", "Mozambique Channel", MaritimeNodeType.SEA_WAYPOINT, -18.0, 41.0);

        register("SUEZ_NORTH", "Suez Canal North Entry", MaritimeNodeType.CANAL, 31.3, 32.3);
        register("SUEZ_CANAL", "Suez Canal Transit", MaritimeNodeType.CANAL, 30.7, 32.35);
        register("SUEZ_SOUTH", "Suez Canal South Entry", MaritimeNodeType.CANAL, 29.7, 32.55);
        register("RED_SEA_NORTH", "Red Sea North Corridor", MaritimeNodeType.SEA_WAYPOINT, 24.0, 36.0);
        register("RED_SEA_CENTRAL", "Red Sea Central Corridor", MaritimeNodeType.SEA_WAYPOINT, 19.0, 39.0);
        register("RED_SEA_SOUTH", "Red Sea South Corridor", MaritimeNodeType.SEA_WAYPOINT, 14.0, 42.0);
        register("GULF_OF_ADEN", "Gulf of Aden", MaritimeNodeType.SEA_WAYPOINT, 12.2, 47.5);

        register("GIBRALTAR_WEST", "Gibraltar Atlantic Approach", MaritimeNodeType.STRAIT, 35.8, -8.0);
        register("GIBRALTAR_STRAIT", "Strait of Gibraltar", MaritimeNodeType.STRAIT, 35.95, -5.5);
        register("ALBORAN_SEA", "Alboran Sea", MaritimeNodeType.SEA_WAYPOINT, 35.7, -2.5);
        register("WEST_MEDITERRANEAN", "West Mediterranean Corridor", MaritimeNodeType.SEA_WAYPOINT, 38.0, 2.0);
        register("TYRRHENIAN_SEA", "Tyrrhenian Sea Corridor", MaritimeNodeType.SEA_WAYPOINT, 39.0, 12.0);
        register("CENTRAL_MEDITERRANEAN", "Central Mediterranean Corridor", MaritimeNodeType.SEA_WAYPOINT, 36.0, 14.0);
        register("IONIAN_SEA", "Ionian Sea Corridor", MaritimeNodeType.SEA_WAYPOINT, 36.5, 18.5);
        register("AEGEAN_SEA", "Aegean Sea Corridor", MaritimeNodeType.SEA_WAYPOINT, 38.0, 25.0);
        register("EAST_MEDITERRANEAN", "East Mediterranean Corridor", MaritimeNodeType.SEA_WAYPOINT, 34.5, 29.0);
        register("BOSPHORUS_STRAIT", "Bosphorus Strait", MaritimeNodeType.STRAIT, 41.1, 29.0);
        register("BLACK_SEA", "Black Sea Main Corridor", MaritimeNodeType.SEA_WAYPOINT, 43.0, 34.0);
        register("BLACK_SEA_WEST", "Black Sea West Corridor", MaritimeNodeType.SEA_WAYPOINT, 43.5, 30.5);
        register("NORTH_SEA_SOUTH", "South North Sea Corridor", MaritimeNodeType.SEA_WAYPOINT, 52.0, 3.0);
        register("NORTH_SEA", "North Sea Main Corridor", MaritimeNodeType.SEA_WAYPOINT, 55.0, 4.5);
        register("ENGLISH_CHANNEL", "English Channel", MaritimeNodeType.STRAIT, 49.2, -3.0);
        register("BAY_OF_BISCAY", "Bay of Biscay", MaritimeNodeType.SEA_WAYPOINT, 46.0, -7.0);
        register("IBERIA_WEST", "West Iberia Offshore", MaritimeNodeType.SEA_WAYPOINT, 39.0, -11.0);
        register("AZORES_CORRIDOR", "Azores Corridor", MaritimeNodeType.SEA_WAYPOINT, 38.0, -28.0);

        register("CARIBBEAN_WEST", "Western Caribbean Corridor", MaritimeNodeType.SEA_WAYPOINT, 16.5, -82.0);
        register("CARIBBEAN_SW", "Southwest Caribbean Approach", MaritimeNodeType.SEA_WAYPOINT, 11.5, -79.0);
        register("CARIBBEAN_COLOMBIA", "Colombia Basin Corridor", MaritimeNodeType.SEA_WAYPOINT, 13.5, -75.5);
        register("CARIBBEAN", "Central Caribbean Corridor", MaritimeNodeType.SEA_WAYPOINT, 17.0, -75.0);
        register("CARIBBEAN_ARC", "Eastern Caribbean Arc", MaritimeNodeType.SEA_WAYPOINT, 16.8, -69.0);
        register("CARIBBEAN_EAST", "Eastern Caribbean Corridor", MaritimeNodeType.SEA_WAYPOINT, 16.5, -66.0);
        register("LESSER_ANTILLES_OUTER", "Outer Lesser Antilles Corridor", MaritimeNodeType.SEA_WAYPOINT, 17.5, -61.5);
        register("PANAMA_PACIFIC", "Panama Canal Pacific Entry", MaritimeNodeType.CANAL, 8.80, -79.60);
        register("PANAMA_BALBOA_INNER", "Balboa Inner Channel", MaritimeNodeType.CANAL, 8.949, -79.566);
        register("PANAMA_MIRAFLORES_LOCKS", "Miraflores Locks", MaritimeNodeType.CANAL, 8.995, -79.585);
        register("PANAMA_PEDRO_MIGUEL_LOCKS", "Pedro Miguel Locks", MaritimeNodeType.CANAL, 9.036, -79.592);
        register("PANAMA_GAILLARD_CUT_SOUTH", "Gaillard Cut South", MaritimeNodeType.CANAL, 9.075, -79.615);
        register("PANAMA_CANAL", "Panama Canal Central Transit", MaritimeNodeType.CANAL, 9.108, -79.648);
        register("PANAMA_GAILLARD_CUT_NORTH", "Gaillard Cut North", MaritimeNodeType.CANAL, 9.144, -79.675);
        register("PANAMA_GATUN_LAKE_SOUTH", "Gatun Lake South", MaritimeNodeType.CANAL, 9.196, -79.726);
        register("PANAMA_GATUN_LAKE_CENTER", "Gatun Lake Center", MaritimeNodeType.CANAL, 9.247, -79.779);
        register("PANAMA_GATUN_LAKE_NORTH", "Gatun Lake North", MaritimeNodeType.CANAL, 9.297, -79.842);
        register("PANAMA_GATUN_LOCKS", "Gatun Locks", MaritimeNodeType.CANAL, 9.334, -79.886);
        register("PANAMA_COLON_INNER", "Colon Inner Channel", MaritimeNodeType.CANAL, 9.351, -79.903);
        register("PANAMA_ATLANTIC", "Panama Canal Atlantic Entry", MaritimeNodeType.CANAL, 9.35, -79.92);
        register("PANAMA_PACIFIC_OUTER", "Panama Pacific Outer Approach", MaritimeNodeType.SEA_WAYPOINT, 8.2, -84.0);
        register("PANAMA_CARIBBEAN_OUTER", "Panama Caribbean Outer Approach", MaritimeNodeType.SEA_WAYPOINT, 10.7, -78.6);
        register("FLORIDA_STRAITS", "Florida Straits", MaritimeNodeType.STRAIT, 24.0, -80.0);
        register("US_EAST_COAST", "US East Coast Corridor", MaritimeNodeType.SEA_WAYPOINT, 35.0, -74.0);
        register("ATLANTIC_TRANSITION_WEST", "West Atlantic Transition Corridor", MaritimeNodeType.SEA_WAYPOINT, 20.0, -57.0);
        register("ATLANTIC_TRANSITION_CENTRAL", "Central Atlantic Transition Corridor", MaritimeNodeType.SEA_WAYPOINT, 29.0, -44.0);
        register("NORTH_ATLANTIC_WEST", "North Atlantic West Corridor", MaritimeNodeType.SEA_WAYPOINT, 34.0, -68.0);
        register("NORTH_ATLANTIC_CENTRAL", "North Atlantic Central Corridor", MaritimeNodeType.SEA_WAYPOINT, 36.0, -42.0);
        register("NORTH_ATLANTIC_EAST", "North Atlantic East Corridor", MaritimeNodeType.SEA_WAYPOINT, 39.0, -18.0);
        register("AZORES_SOUTH", "South Azores Approach", MaritimeNodeType.SEA_WAYPOINT, 35.5, -28.5);
        register("MADEIRA_APPROACH", "Madeira Offshore Approach", MaritimeNodeType.SEA_WAYPOINT, 33.0, -16.5);
        register("PORTUGAL_APPROACH", "Portugal Atlantic Approach", MaritimeNodeType.SEA_WAYPOINT, 37.0, -11.5);
        register("ATLANTIC_EQUATOR_WEST", "Equatorial Atlantic West Corridor", MaritimeNodeType.SEA_WAYPOINT, 4.0, -42.0);
        register("BRAZIL_NORTH", "North Brazil Offshore", MaritimeNodeType.SEA_WAYPOINT, 2.0, -38.0);
        register("BRAZIL_SOUTHEAST", "Brazil Southeast Offshore", MaritimeNodeType.SEA_WAYPOINT, -23.0, -40.0);
        register("SOUTH_ATLANTIC_WEST", "South Atlantic West Corridor", MaritimeNodeType.SEA_WAYPOINT, -24.0, -44.0);
        register("RIO_PLATA_APPROACH", "Rio de la Plata Offshore", MaritimeNodeType.SEA_WAYPOINT, -35.0, -54.0);
        register("CAPE_HORN_WEST", "Cape Horn Pacific Side", MaritimeNodeType.SEA_WAYPOINT, -55.0, -74.0);
        register("CAPE_HORN", "Cape Horn", MaritimeNodeType.SEA_WAYPOINT, -56.0, -67.0);
        register("CAPE_HORN_EAST", "Cape Horn Atlantic Side", MaritimeNodeType.SEA_WAYPOINT, -54.0, -62.0);
        register("ATLANTIC_EQUATOR_EAST", "Equatorial Atlantic East Corridor", MaritimeNodeType.SEA_WAYPOINT, 6.0, -18.0);
        register("WEST_AFRICA_NW", "West Africa Northwest Offshore", MaritimeNodeType.SEA_WAYPOINT, 20.0, -18.0);
        register("WEST_AFRICA_CENTRAL", "West Africa Central Offshore", MaritimeNodeType.SEA_WAYPOINT, 6.0, -8.0);
        register("WEST_AFRICA_SOUTH", "West Africa South Offshore", MaritimeNodeType.SEA_WAYPOINT, -9.0, 6.0);
        register("SOUTH_ATLANTIC_EAST", "South Atlantic East Corridor", MaritimeNodeType.SEA_WAYPOINT, -30.0, 10.0);
        register("SOUTH_AFRICA_WEST", "South Africa West Offshore", MaritimeNodeType.SEA_WAYPOINT, -31.0, 14.0);
        register("CAPE_GOOD_HOPE", "Cape of Good Hope", MaritimeNodeType.SEA_WAYPOINT, -35.0, 19.0);
        register("SOUTH_AFRICA_EAST", "South Africa East Offshore", MaritimeNodeType.SEA_WAYPOINT, -31.0, 31.0);

        register("CALIFORNIA_OFFSHORE", "California Offshore Corridor", MaritimeNodeType.SEA_WAYPOINT, 34.0, -124.0);
        register("BAJA_OFFSHORE", "Baja Offshore Corridor", MaritimeNodeType.SEA_WAYPOINT, 24.0, -112.0);
        register("CENTRAL_AMERICA_WEST", "Central America West Corridor", MaritimeNodeType.SEA_WAYPOINT, 13.0, -90.0);
        register("PERU_NORTHBOUND", "Peru Northbound Offshore Lane", MaritimeNodeType.SEA_WAYPOINT, -6.5, -81.5);
        register("ECUADOR_APPROACH", "Ecuador Offshore", MaritimeNodeType.SEA_WAYPOINT, -1.0, -83.0);
        register("ECUADOR_OUTER", "Ecuador Outer Offshore Corridor", MaritimeNodeType.SEA_WAYPOINT, 2.0, -84.0);
        register("COLOMBIA_PACIFIC", "Colombia Pacific Offshore Corridor", MaritimeNodeType.SEA_WAYPOINT, 5.5, -81.5);
        register("PANAMA_COASTAL_APPROACH", "Panama Pacific Coastal Approach", MaritimeNodeType.SEA_WAYPOINT, 7.6, -80.6);
        register("PERU_APPROACH", "Peru Offshore", MaritimeNodeType.SEA_WAYPOINT, -12.0, -80.0);
        register("CHILE_APPROACH", "Chile Offshore", MaritimeNodeType.SEA_WAYPOINT, -33.0, -74.0);

        register("AUSTRALIA_WEST", "Australia West Offshore", MaritimeNodeType.SEA_WAYPOINT, -21.0, 114.0);
        register("AUSTRALIA_NORTH", "Australia North Offshore", MaritimeNodeType.SEA_WAYPOINT, -12.0, 129.0);
        register("AUSTRALIA_EAST", "Australia East Offshore", MaritimeNodeType.SEA_WAYPOINT, -28.0, 153.0);
        register("NEW_ZEALAND_NORTH", "New Zealand North Offshore", MaritimeNodeType.SEA_WAYPOINT, -36.0, 175.0);

        coreEdges = List.of(
                edge("JAPAN_EAST_APPROACH", "NORTH_PACIFIC_WEST"),
                edge("JAPAN_EAST_APPROACH", "JAPAN_SOUTH_APPROACH"),
                edge("JAPAN_SOUTH_APPROACH", "EAST_CHINA_SEA_COAST"),
                edge("EAST_CHINA_SEA_COAST", "EAST_CHINA_SEA"),
                edge("EAST_CHINA_SEA_COAST", "TAIWAN_EAST_APPROACH"),
                edge("TAIWAN_EAST_APPROACH", "PHILIPPINE_SEA_NORTH"),
                edge("TAIWAN_EAST_APPROACH", "SOUTH_CHINA_SEA_NORTH"),
                edge("PHILIPPINE_SEA_NORTH", "NORTH_PACIFIC_WEST"),
                edge("PHILIPPINE_SEA_NORTH", "NORTH_PACIFIC_CENTRAL"),
                edge("SOUTH_CHINA_SEA_NORTH", "SOUTH_CHINA_SEA"),
                edge("SOUTH_CHINA_SEA_NORTH", "VIETNAM_COAST"),
                edge("VIETNAM_COAST", "SOUTH_CHINA_SEA"),
                edge("VIETNAM_COAST", "ANDAMAN_SEA"),
                edge("ANDAMAN_SEA", "MALACCA_WEST"),
                edge("ANDAMAN_SEA", "BAY_OF_BENGAL_EAST"),
                edge("MALACCA_WEST", "MALACCA_STRAIT", false, true, false),
                edge("MALACCA_STRAIT", "MALACCA_SOUTH", false, true, false),
                edge("MALACCA_SOUTH", "SOUTH_CHINA_SEA"),
                edge("MALACCA_SOUTH", "JAVA_SEA"),
                edge("JAVA_SEA", "CELEBES_SEA"),
                edge("JAVA_SEA", "ARAFURA_SEA"),
                edge("CELEBES_SEA", "SOUTH_CHINA_SEA"),
                edge("CELEBES_SEA", "CORAL_SEA"),
                edge("CELEBES_SEA", "ARAFURA_SEA"),
                edge("ARAFURA_SEA", "AUSTRALIA_NORTH"),
                edge("ARAFURA_SEA", "CORAL_SEA"),
                edge("CORAL_SEA", "AUSTRALIA_EAST"),
                edge("CORAL_SEA", "TASMAN_SEA"),
                edge("TASMAN_SEA", "NEW_ZEALAND_NORTH"),
                edge("TASMAN_SEA", "SOUTH_PACIFIC_WEST"),
                edge("AUSTRALIA_WEST", "SOUTH_INDIAN_EAST"),
                edge("AUSTRALIA_NORTH", "INDIAN_OCEAN_EAST"),

                edge("SRI_LANKA_SOUTH", "ARABIAN_SEA"),
                edge("SRI_LANKA_SOUTH", "BAY_OF_BENGAL_WEST"),
                edge("BAY_OF_BENGAL_WEST", "BAY_OF_BENGAL"),
                edge("BAY_OF_BENGAL", "BAY_OF_BENGAL_EAST"),
                edge("BAY_OF_BENGAL_EAST", "ANDAMAN_SEA"),
                edge("GULF_OF_OMAN", "ARABIAN_SEA"),
                edge("ARABIAN_SEA", "INDIAN_OCEAN_CENTRAL"),
                edge("INDIAN_OCEAN_CENTRAL", "INDIAN_OCEAN_EAST"),
                edge("INDIAN_OCEAN_CENTRAL", "MADAGASCAR_EAST"),
                edge("MADAGASCAR_EAST", "MOZAMBIQUE_CHANNEL"),
                edge("MOZAMBIQUE_CHANNEL", "SOUTH_AFRICA_EAST"),
                edge("SOUTH_AFRICA_EAST", "CAPE_GOOD_HOPE"),
                edge("CAPE_GOOD_HOPE", "SOUTH_AFRICA_WEST"),
                edge("CAPE_GOOD_HOPE", "SOUTH_INDIAN_EAST"),
                edge("INDIAN_OCEAN_EAST", "SOUTH_INDIAN_EAST"),

                edge("SUEZ_NORTH", "SUEZ_CANAL", true, true, false),
                edge("SUEZ_CANAL", "SUEZ_SOUTH", true, true, false),
                edge("SUEZ_SOUTH", "RED_SEA_NORTH"),
                edge("RED_SEA_NORTH", "RED_SEA_CENTRAL"),
                edge("RED_SEA_CENTRAL", "RED_SEA_SOUTH"),
                edge("RED_SEA_SOUTH", "GULF_OF_ADEN", false, false, true),
                edge("GULF_OF_ADEN", "GULF_OF_OMAN", false, false, true),

                edge("GIBRALTAR_WEST", "GIBRALTAR_STRAIT", false, true, false),
                edge("GIBRALTAR_STRAIT", "ALBORAN_SEA", false, true, false),
                edge("ALBORAN_SEA", "WEST_MEDITERRANEAN"),
                edge("WEST_MEDITERRANEAN", "TYRRHENIAN_SEA"),
                edge("TYRRHENIAN_SEA", "CENTRAL_MEDITERRANEAN"),
                edge("CENTRAL_MEDITERRANEAN", "IONIAN_SEA"),
                edge("IONIAN_SEA", "AEGEAN_SEA"),
                edge("IONIAN_SEA", "EAST_MEDITERRANEAN"),
                edge("AEGEAN_SEA", "EAST_MEDITERRANEAN"),
                edge("EAST_MEDITERRANEAN", "SUEZ_NORTH"),
                edge("AEGEAN_SEA", "BOSPHORUS_STRAIT", false, true, false),
                edge("BOSPHORUS_STRAIT", "BLACK_SEA_WEST", false, true, false),
                edge("BLACK_SEA_WEST", "BLACK_SEA"),
                edge("IBERIA_WEST", "GIBRALTAR_WEST"),
                edge("IBERIA_WEST", "BAY_OF_BISCAY"),
                edge("BAY_OF_BISCAY", "ENGLISH_CHANNEL"),
                edge("ENGLISH_CHANNEL", "NORTH_SEA_SOUTH", false, true, false),
                edge("NORTH_SEA_SOUTH", "NORTH_SEA"),
                edge("NORTH_SEA", "NORTH_ATLANTIC_EAST"),
                edge("NORTH_SEA", "AZORES_CORRIDOR"),

                edge("CALIFORNIA_OFFSHORE", "PACIFIC_NORTHEAST"),
                edge("CALIFORNIA_OFFSHORE", "BAJA_OFFSHORE"),
                edge("BAJA_OFFSHORE", "PACIFIC_TROPICAL_EAST"),
                edge("PACIFIC_NORTHEAST", "NORTH_PACIFIC_EAST"),
                edge("PACIFIC_SOUTH_EAST", "PACIFIC_TROPICAL_EAST", List.of(
                        coord(-10.0, -88.0),
                        coord(-6.0, -92.0),
                        coord(-2.0, -96.0),
                        coord(3.0, -99.0),
                        coord(8.0, -101.0),
                        coord(13.0, -102.0)
                ), false, false, false),
                edge("PACIFIC_TROPICAL_EAST", "CENTRAL_AMERICA_WEST"),
                edge("PACIFIC_TROPICAL_EAST", "ECUADOR_APPROACH"),
                edge("ECUADOR_APPROACH", "ECUADOR_OUTER"),
                edge("ECUADOR_APPROACH", "PERU_NORTHBOUND"),
                edge("ECUADOR_APPROACH", "PERU_APPROACH"),
                edge("ECUADOR_OUTER", "PACIFIC_TROPICAL_EAST"),
                edge("ECUADOR_OUTER", "COLOMBIA_PACIFIC"),
                edge("COLOMBIA_PACIFIC", "PANAMA_PACIFIC_OUTER", panamaPath(
                        coord(5.5000, -81.5000),
                        coord(6.0000, -81.7000),
                        coord(6.5000, -81.9000),
                        coord(7.0000, -82.2000),
                        coord(7.4000, -82.6000),
                        coord(7.8000, -83.1000),
                        coord(8.0000, -83.5000),
                        coord(8.2000, -84.0000)
                ), false, false, false),
                edge("PERU_NORTHBOUND", "PERU_APPROACH"),
                edge("PERU_NORTHBOUND", "ECUADOR_OUTER"),
                edge("ECUADOR_APPROACH", "PACIFIC_SOUTH_EAST"),
                edge("PERU_APPROACH", "PACIFIC_SOUTH_EAST"),
                edge("PERU_APPROACH", "CHILE_APPROACH"),
                edge("CHILE_APPROACH", "CAPE_HORN_WEST"),
                edge("PACIFIC_SOUTH_EAST", "SOUTH_PACIFIC_EAST"),
                edge("SOUTH_PACIFIC_EAST", "SOUTH_PACIFIC_CENTRAL"),
                edge("SOUTH_PACIFIC_CENTRAL", "SOUTH_PACIFIC_WEST"),
                edge("NORTH_PACIFIC_WEST", "NORTH_PACIFIC_CENTRAL"),
                edge("NORTH_PACIFIC_CENTRAL", "NORTH_PACIFIC_EAST"),

                edge("CENTRAL_AMERICA_WEST", "PANAMA_PACIFIC_OUTER", panamaPath(
                        coord(13.0000, -90.0000),
                        coord(12.0000, -88.8000),
                        coord(10.8000, -87.2000),
                        coord(9.6000, -85.5000),
                        coord(8.8000, -84.6000),
                        coord(8.2000, -84.0000)
                ), false, false, false),
                edge("PANAMA_PACIFIC_OUTER", "PANAMA_PACIFIC", panamaPath(
                        coord(8.2000, -84.0000),
                        coord(8.2000, -83.8000),
                        coord(8.2000, -83.4000),
                        coord(8.2000, -83.0000),
                        coord(8.0000, -82.2000),
                        coord(7.6000, -81.6000),
                        coord(7.2000, -80.8000),
                        coord(7.2000, -80.4000),
                        coord(7.6000, -80.0000),
                        coord(8.0000, -79.8000),
                        coord(8.4000, -79.4000),
                        coord(8.6000, -79.4000),
                        coord(8.8000, -79.6000)
                ), false, false, false),
                edge("PANAMA_PACIFIC_OUTER", "PANAMA_BALBOA_INNER", panamaPath(
                        coord(8.2000, -84.0000),
                        coord(8.2000, -83.8000),
                        coord(8.2000, -83.4000),
                        coord(8.2000, -83.0000),
                        coord(8.0000, -82.2000),
                        coord(7.6000, -81.6000),
                        coord(7.2000, -80.8000),
                        coord(7.2000, -80.4000),
                        coord(7.6000, -80.0000),
                        coord(8.0000, -79.8000),
                        coord(8.4000, -79.4000),
                        coord(8.6000, -79.4000),
                        coord(8.8000, -79.6000),
                        coord(8.8740, -79.5840),
                        coord(8.9300, -79.5720),
                        coord(8.9480, -79.5580),
                        coord(8.9490, -79.5660)
                ), true, true, false),
                edge("PANAMA_PACIFIC", "PANAMA_BALBOA_INNER", panamaPath(
                        coord(8.8000, -79.6000),
                        coord(8.8740, -79.5840),
                        coord(8.9300, -79.5720),
                        coord(8.9480, -79.5580),
                        coord(8.9490, -79.5660)
                ), true, true, false),
                edge("PANAMA_BALBOA_INNER", "PANAMA_MIRAFLORES_LOCKS", panamaPath(
                        coord(8.9490, -79.5660),
                        coord(8.9650, -79.5730),
                        coord(8.9820, -79.5790),
                        coord(8.9950, -79.5850)
                ), true, true, false),
                edge("PANAMA_MIRAFLORES_LOCKS", "PANAMA_PEDRO_MIGUEL_LOCKS", panamaPath(
                        coord(8.9950, -79.5850),
                        coord(9.0120, -79.5890),
                        coord(9.0260, -79.5910),
                        coord(9.0360, -79.5920)
                ), true, true, false),
                edge("PANAMA_PEDRO_MIGUEL_LOCKS", "PANAMA_GAILLARD_CUT_SOUTH", panamaPath(
                        coord(9.0360, -79.5920),
                        coord(9.0490, -79.5980),
                        coord(9.0620, -79.6070),
                        coord(9.0750, -79.6150)
                ), true, true, false),
                edge("PANAMA_GAILLARD_CUT_SOUTH", "PANAMA_CANAL", panamaPath(
                        coord(9.0750, -79.6150),
                        coord(9.0880, -79.6280),
                        coord(9.0980, -79.6390),
                        coord(9.1080, -79.6480)
                ), true, true, false),
                edge("PANAMA_CANAL", "PANAMA_GAILLARD_CUT_NORTH", panamaPath(
                        coord(9.1080, -79.6480),
                        coord(9.1210, -79.6580),
                        coord(9.1330, -79.6670),
                        coord(9.1440, -79.6750)
                ), true, true, false),
                edge("PANAMA_GAILLARD_CUT_NORTH", "PANAMA_GATUN_LAKE_SOUTH", panamaPath(
                        coord(9.1440, -79.6750),
                        coord(9.1600, -79.6910),
                        coord(9.1770, -79.7080),
                        coord(9.1960, -79.7260)
                ), true, true, false),
                edge("PANAMA_GATUN_LAKE_SOUTH", "PANAMA_GATUN_LAKE_CENTER", panamaPath(
                        coord(9.1960, -79.7260),
                        coord(9.2140, -79.7450),
                        coord(9.2300, -79.7620),
                        coord(9.2470, -79.7790)
                ), true, true, false),
                edge("PANAMA_GATUN_LAKE_CENTER", "PANAMA_GATUN_LAKE_NORTH", panamaPath(
                        coord(9.2470, -79.7790),
                        coord(9.2640, -79.7980),
                        coord(9.2800, -79.8210),
                        coord(9.2970, -79.8420)
                ), true, true, false),
                edge("PANAMA_GATUN_LAKE_NORTH", "PANAMA_GATUN_LOCKS", panamaPath(
                        coord(9.2970, -79.8420),
                        coord(9.3110, -79.8590),
                        coord(9.3230, -79.8740),
                        coord(9.3340, -79.8860)
                ), true, true, false),
                edge("PANAMA_GATUN_LOCKS", "PANAMA_COLON_INNER", panamaPath(
                        coord(9.3340, -79.8860),
                        coord(9.3410, -79.8930),
                        coord(9.3460, -79.8990),
                        coord(9.3510, -79.9030)
                ), true, true, false),
                edge("PANAMA_COLON_INNER", "PANAMA_ATLANTIC", panamaPath(
                        coord(9.3510, -79.9030),
                        coord(9.3520, -79.9090),
                        coord(9.3510, -79.9150),
                        coord(9.3500, -79.9200)
                ), true, true, false),
                edge("PANAMA_ATLANTIC", "PANAMA_CARIBBEAN_OUTER", panamaPath(
                        coord(9.3500, -79.9200),
                        coord(9.5200, -79.8300),
                        coord(9.7600, -79.6200),
                        coord(10.0500, -79.2500),
                        coord(10.3600, -78.9200),
                        coord(10.7000, -78.6000)
                ), false, false, false),
                edge("PANAMA_CARIBBEAN_OUTER", "CARIBBEAN_SW", panamaPath(
                        coord(10.7000, -78.6000),
                        coord(10.9500, -78.8200),
                        coord(11.1800, -79.0200),
                        coord(11.5000, -79.0000)
                ), false, false, false),
                edge("PANAMA_CARIBBEAN_OUTER", "CARIBBEAN_WEST", panamaPath(
                        coord(10.7000, -78.6000),
                        coord(11.4000, -79.2500),
                        coord(12.3000, -80.1000),
                        coord(13.5000, -81.0000),
                        coord(14.9000, -81.7000),
                        coord(16.5000, -82.0000)
                ), false, false, false),
                edge("CARIBBEAN_SW", "CARIBBEAN_WEST"),
                edge("CARIBBEAN_SW", "CARIBBEAN_COLOMBIA"),
                edge("CARIBBEAN_COLOMBIA", "CARIBBEAN"),
                edge("CARIBBEAN_WEST", "CARIBBEAN"),
                edge("CARIBBEAN_WEST", "FLORIDA_STRAITS"),
                edge("CARIBBEAN", "CARIBBEAN_ARC"),
                edge("CARIBBEAN", "CARIBBEAN_EAST"),
                edge("CARIBBEAN_ARC", "CARIBBEAN_EAST"),
                edge("CARIBBEAN_ARC", "LESSER_ANTILLES_OUTER"),
                edge("CARIBBEAN_EAST", "LESSER_ANTILLES_OUTER"),
                edge("LESSER_ANTILLES_OUTER", "ATLANTIC_TRANSITION_WEST"),
                edge("ATLANTIC_TRANSITION_WEST", "NORTH_ATLANTIC_WEST"),
                edge("ATLANTIC_TRANSITION_WEST", "ATLANTIC_EQUATOR_WEST"),
                edge("CARIBBEAN_EAST", "ATLANTIC_EQUATOR_WEST"),
                edge("FLORIDA_STRAITS", "US_EAST_COAST", false, true, false),
                edge("US_EAST_COAST", "NORTH_ATLANTIC_WEST"),
                edge("NORTH_ATLANTIC_WEST", "ATLANTIC_TRANSITION_CENTRAL"),
                edge("ATLANTIC_TRANSITION_CENTRAL", "NORTH_ATLANTIC_CENTRAL"),
                edge("ATLANTIC_TRANSITION_CENTRAL", "AZORES_SOUTH"),
                edge("NORTH_ATLANTIC_CENTRAL", "AZORES_CORRIDOR"),
                edge("AZORES_CORRIDOR", "AZORES_SOUTH"),
                edge("AZORES_CORRIDOR", "NORTH_ATLANTIC_EAST"),
                edge("AZORES_SOUTH", "MADEIRA_APPROACH"),
                edge("MADEIRA_APPROACH", "PORTUGAL_APPROACH"),
                edge("PORTUGAL_APPROACH", "IBERIA_WEST"),
                edge("AZORES_CORRIDOR", "PORTUGAL_APPROACH"),
                edge("AZORES_CORRIDOR", "IBERIA_WEST"),

                edge("ATLANTIC_EQUATOR_WEST", "BRAZIL_NORTH"),
                edge("ATLANTIC_EQUATOR_WEST", "ATLANTIC_EQUATOR_EAST"),
                edge("BRAZIL_NORTH", "BRAZIL_SOUTHEAST"),
                edge("BRAZIL_SOUTHEAST", "SOUTH_ATLANTIC_WEST"),
                edge("BRAZIL_SOUTHEAST", "RIO_PLATA_APPROACH"),
                edge("RIO_PLATA_APPROACH", "CAPE_HORN_EAST"),
                edge("SOUTH_ATLANTIC_WEST", "CAPE_HORN_EAST"),
                edge("CAPE_HORN_WEST", "CAPE_HORN"),
                edge("CAPE_HORN", "CAPE_HORN_EAST"),
                edge("ATLANTIC_EQUATOR_EAST", "WEST_AFRICA_NW"),
                edge("ATLANTIC_EQUATOR_EAST", "WEST_AFRICA_CENTRAL"),
                edge("WEST_AFRICA_NW", "GIBRALTAR_WEST"),
                edge("WEST_AFRICA_CENTRAL", "WEST_AFRICA_SOUTH"),
                edge("WEST_AFRICA_CENTRAL", "SOUTH_ATLANTIC_EAST"),
                edge("WEST_AFRICA_SOUTH", "SOUTH_ATLANTIC_EAST"),
                edge("WEST_AFRICA_SOUTH", "SOUTH_AFRICA_WEST"),
                edge("SOUTH_ATLANTIC_EAST", "SOUTH_AFRICA_WEST"),
                edge("SOUTH_AFRICA_WEST", "CAPE_GOOD_HOPE")
        );
    }

    public List<MaritimeNode> coreNodes() {
        return List.copyOf(coreNodes.values());
    }

    public List<EdgeDefinition> coreEdges() {
        return coreEdges;
    }

    public Optional<MaritimeNode> findNode(String nodeId) {
        return Optional.ofNullable(coreNodes.get(nodeId));
    }

    public List<String> connectorIdsFor(Port port) {
        String name = normalize(port.getName());
        double lat = port.getCoordinates().latitude();
        double lon = port.getCoordinates().longitude();
        String continent = normalize(port.getContinent());

        switch (name) {
            case "tokyo":
            case "goto":
            case "miyazaki":
            case "osaka":
                return List.of("JAPAN_EAST_APPROACH", "JAPAN_SOUTH_APPROACH");
            case "busan":
            case "shanghai":
            case "tianjin":
            case "quanzhou":
            case "zhanjiang":
            case "taiwan":
            case "hong kong":
            case "nah trang":
                return List.of("EAST_CHINA_SEA_COAST", "SOUTH_CHINA_SEA_NORTH");
            case "singapore":
            case "jakarta":
            case "muara port":
            case "semayang":
            case "macasar":
                return List.of("MALACCA_SOUTH", "JAVA_SEA");
            case "mumbai":
            case "muscat":
            case "dubai":
                return List.of("GULF_OF_OMAN", "ARABIAN_SEA");
            case "chennai":
            case "tuticorin":
                return List.of("SRI_LANKA_SOUTH", "BAY_OF_BENGAL_WEST");
            case "jeddah":
            case "aden":
                return List.of("RED_SEA_SOUTH", "GULF_OF_ADEN");
            case "balboa":
                return List.of("PANAMA_BALBOA_INNER", "PANAMA_PACIFIC", "PANAMA_PACIFIC_OUTER", "CENTRAL_AMERICA_WEST");
            case "colon":
            case "colon ":
            case "colón":
                return List.of("PANAMA_COLON_INNER", "PANAMA_ATLANTIC", "PANAMA_CARIBBEAN_OUTER", "CARIBBEAN_WEST");
            case "callao":
            case "chancay":
            case "guayaquil":
                return List.of("PACIFIC_SOUTH_EAST");
            case "san antonio":
            case "valparaiso":
            case "valparaiso ":
            case "valparaíso":
            case "puerto montt":
                return List.of("CHILE_APPROACH", "CAPE_HORN_WEST");
            case "san francisco":
            case "long beach":
            case "manzanillo":
            case "lazaro cardenas":
            case "lázaro cárdenas":
            case "acajutla":
                return List.of("CALIFORNIA_OFFSHORE", "BAJA_OFFSHORE", "CENTRAL_AMERICA_WEST");
            case "new york":
            case "norfolk":
            case "savannah":
            case "fort lauderdale":
            case "montreal":
            case "houston":
                return List.of("US_EAST_COAST", "FLORIDA_STRAITS");
            case "cartagena":
            case "puerto cabello":
            case "santo domingo":
            case "la habana":
            case "caucedo":
            case "freeport":
            case "kingston":
                return List.of("PANAMA_CARIBBEAN_OUTER", "CARIBBEAN_SW", "CARIBBEAN", "CARIBBEAN_EAST", "LESSER_ANTILLES_OUTER");
            case "santos":
            case "rio de janeiro":
            case "rio grande":
            case "montevideo":
            case "buenos aires":
            case "necochea y quequen":
            case "necochea y quequén":
                return List.of("BRAZIL_SOUTHEAST", "RIO_PLATA_APPROACH");
            case "ushuaia":
            case "stanley":
                return List.of("CAPE_HORN_WEST", "CAPE_HORN_EAST");
            case "lisboa":
            case "tanger med":
            case "casablanca":
                return List.of("PORTUGAL_APPROACH", "MADEIRA_APPROACH", "IBERIA_WEST", "GIBRALTAR_WEST");
            case "hamburgo":
            case "rotterdam":
            case "le havre":
            case "copenhague":
            case "stavanger":
                return List.of("ENGLISH_CHANNEL", "NORTH_SEA_SOUTH");
            case "livorno":
            case "genova":
            case "napoli":
            case "palermo":
            case "valencia":
                return List.of("ALBORAN_SEA", "TYRRHENIAN_SEA", "CENTRAL_MEDITERRANEAN");
            case "atenas":
            case "alexandria":
            case "alexandría":
            case "haifa":
            case "puerto de haifa":
            case "latakia":
            case "puerto de beirut":
            case "mersin":
            case "bari":
            case "venecia":
                return List.of("IONIAN_SEA", "AEGEAN_SEA", "EAST_MEDITERRANEAN");
            case "estambul":
            case "constanza":
            case "odessa":
            case "eupatoria":
            case "puerto de crimea":
                return List.of("BOSPHORUS_STRAIT", "BLACK_SEA_WEST");
            case "dakar":
            case "nuakchot":
            case "freetown":
            case "conakri":
            case "abiyan":
            case "abiyán":
            case "tema":
            case "monrovia":
            case "lome":
            case "lomé":
            case "cotonou":
            case "lagos":
            case "port-gentil":
            case "luanda":
                return List.of("WEST_AFRICA_NW", "WEST_AFRICA_CENTRAL", "WEST_AFRICA_SOUTH");
            case "walvis bay":
            case "ciudad del cabo":
                return List.of("SOUTH_AFRICA_WEST", "CAPE_GOOD_HOPE");
            case "durban":
            case "mombasa":
            case "mogadishu":
            case "beira":
            case "maputo":
            case "toamasina":
                return List.of("SOUTH_AFRICA_EAST", "MOZAMBIQUE_CHANNEL", "MADAGASCAR_EAST");
            case "darwin":
                return List.of("AUSTRALIA_NORTH", "ARAFURA_SEA");
            case "brisbane":
            case "sydney":
            case "sídney":
            case "melbourne":
            case "port moresby":
            case "auckland":
                return List.of("AUSTRALIA_EAST", "CORAL_SEA", "TASMAN_SEA");
            case "fremantle":
            case "hedland":
                return List.of("AUSTRALIA_WEST", "SOUTH_INDIAN_EAST");
            default:
                break;
        }

        if (continent.contains("asia")) {
            if (lon >= 133 && lat >= 28) return List.of("JAPAN_EAST_APPROACH", "JAPAN_SOUTH_APPROACH");
            if (lon >= 120 && lat >= 20) return List.of("EAST_CHINA_SEA_COAST", "TAIWAN_EAST_APPROACH");
            if (lon >= 103 && lat >= 2) return List.of("SOUTH_CHINA_SEA_NORTH", "VIETNAM_COAST");
            if (lon >= 95 && lon < 110) return List.of("MALACCA_SOUTH", "ANDAMAN_SEA");
            if (lon >= 72 && lon < 95) return List.of("SRI_LANKA_SOUTH", "BAY_OF_BENGAL_WEST");
            if (lon >= 45 && lon < 72) return List.of("GULF_OF_OMAN", "ARABIAN_SEA");
            return List.of("RED_SEA_SOUTH", "GULF_OF_ADEN");
        }

        if (continent.contains("europa") || continent.contains("europe")) {
            if (lon >= 26) return List.of("BOSPHORUS_STRAIT", "BLACK_SEA_WEST");
            if (lat >= 50) return List.of("ENGLISH_CHANNEL", "NORTH_SEA_SOUTH");
            if (lon <= -5) return List.of("PORTUGAL_APPROACH", "MADEIRA_APPROACH", "IBERIA_WEST", "GIBRALTAR_WEST");
            if (lon < 14) return List.of("ALBORAN_SEA", "TYRRHENIAN_SEA");
            return List.of("IONIAN_SEA", "AEGEAN_SEA", "EAST_MEDITERRANEAN");
        }

        if (continent.contains("america")) {
            if (lon <= -118 && lat >= 28) return List.of("CALIFORNIA_OFFSHORE", "PACIFIC_NORTHEAST");
            if (lon <= -100 && lat >= 12) return List.of("BAJA_OFFSHORE", "CENTRAL_AMERICA_WEST");
            if (lon <= -76 && lat < 12) return List.of("PACIFIC_SOUTH_EAST", "CAPE_HORN_WEST");
            if (lat >= 20 && lon > -90) return List.of("US_EAST_COAST", "FLORIDA_STRAITS");
            if (lat >= 8 && lon > -85) return List.of("PANAMA_CARIBBEAN_OUTER", "CARIBBEAN_SW", "CARIBBEAN", "CARIBBEAN_EAST", "LESSER_ANTILLES_OUTER");
            if (lat < 8 && lat > -15) return List.of("BRAZIL_NORTH", "ATLANTIC_EQUATOR_WEST");
            if (lat <= -15 && lat > -45) return List.of("BRAZIL_SOUTHEAST", "RIO_PLATA_APPROACH");
            return List.of("CAPE_HORN_WEST", "CAPE_HORN_EAST");
        }

        if (continent.contains("africa")) {
            if (lat >= 28) return List.of("ALBORAN_SEA", "EAST_MEDITERRANEAN", "SUEZ_NORTH");
            if (lon < 10) return List.of("WEST_AFRICA_NW", "WEST_AFRICA_CENTRAL");
            if (lat <= -20 && lon < 25) return List.of("SOUTH_AFRICA_WEST", "CAPE_GOOD_HOPE");
            if (lat <= -20) return List.of("SOUTH_AFRICA_EAST", "CAPE_GOOD_HOPE");
            if (lon >= 30 && lat > 0) return List.of("RED_SEA_SOUTH", "GULF_OF_ADEN");
            return List.of("MOZAMBIQUE_CHANNEL", "MADAGASCAR_EAST");
        }

        if (continent.contains("oceania")) {
            if (lon >= 145) return List.of("AUSTRALIA_EAST", "CORAL_SEA", "TASMAN_SEA");
            if (lon >= 128) return List.of("AUSTRALIA_NORTH", "ARAFURA_SEA");
            return List.of("AUSTRALIA_WEST", "SOUTH_INDIAN_EAST");
        }

        return List.of("NORTH_ATLANTIC_CENTRAL");
    }

    public List<String> preferredOverlayRegionIdsFor(Port port) {
        String name = normalize(port.getName());
        double lat = port.getCoordinates().latitude();
        double lon = port.getCoordinates().longitude();
        String continent = normalize(port.getContinent());

        switch (name) {
            case "callao":
            case "chancay":
            case "guayaquil":
                return List.of("peru-ecuador-coast");
            case "valencia":
                return List.of("iberia-mediterranean-approach");
            case "lisboa":
            case "tanger med":
            case "casablanca":
                return List.of("iberia-atlantic-approach");
            default:
                break;
        }

        if (continent.contains("america") && lon <= -76 && lat >= -15 && lat <= 8) {
            return List.of("peru-ecuador-coast");
        }
        if ((continent.contains("europa") || continent.contains("europe")) && lon >= -5.5 && lon <= 2.5 && lat >= 35 && lat <= 41.5) {
            return List.of("iberia-mediterranean-approach");
        }
        if ((continent.contains("europa") || continent.contains("europe") || continent.contains("africa"))
                && lon >= -15 && lon <= -5 && lat >= 31 && lat <= 41.5) {
            return List.of("iberia-atlantic-approach");
        }
        return List.of();
    }

    private void register(String id, String name, MaritimeNodeType type, double latitude, double longitude) {
        coreNodes.put(id, MaritimeNode.seaNode(id, name, type, new Coordinates(latitude, longitude)));
    }

    private EdgeDefinition edge(String fromNodeId, String toNodeId) {
        return new EdgeDefinition(fromNodeId, toNodeId, false, false, false);
    }

    private EdgeDefinition edge(String fromNodeId, String toNodeId, boolean canal, boolean restricted, boolean highRisk) {
        return new EdgeDefinition(fromNodeId, toNodeId, canal, restricted, highRisk);
    }

    private EdgeDefinition edge(String fromNodeId,
                                String toNodeId,
                                List<Coordinates> geometry,
                                boolean canal,
                                boolean restricted,
                                boolean highRisk) {
        return new EdgeDefinition(fromNodeId, toNodeId, canal, restricted, highRisk, geometry);
    }

    private List<Coordinates> panamaPath(Coordinates... anchors) {
        return List.of(anchors);
    }

    private Coordinates coord(double latitude, double longitude) {
        return new Coordinates(latitude, longitude);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(Locale.ROOT).trim();
    }

    public record EdgeDefinition(
            String fromNodeId,
            String toNodeId,
            boolean canal,
            boolean restricted,
            boolean highRisk,
            List<Coordinates> geometry
    ) {
        public EdgeDefinition(String fromNodeId,
                              String toNodeId,
                              boolean canal,
                              boolean restricted,
                              boolean highRisk) {
            this(fromNodeId, toNodeId, canal, restricted, highRisk, List.of());
        }

        public EdgeDefinition {
            geometry = geometry == null ? List.of() : List.copyOf(geometry);
        }
    }
}
