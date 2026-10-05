package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.planning;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.security.MessageDigest;
import java.util.HexFormat;

final class PlanJson {
    static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS,
                    DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .nodeFactory(JsonNodeFactory.withExactBigDecimals(true)).build();
    private PlanJson() { }
    static byte[] bytes(Object value) {
        try { return MAPPER.writeValueAsBytes(value); }
        catch (Exception invalid) { throw new IllegalStateException("PLAN_SERIALIZATION_FAILED", invalid); }
    }
    static String sha(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(canonical(MAPPER.readTree(bytes(value)))))); }
        catch (Exception impossible) { throw new IllegalStateException("PLAN_HASH_FAILED", impossible); }
    }
    private static JsonNode canonical(JsonNode value) {
        if (value.isObject()) {
            var result = MAPPER.createObjectNode(); var keys = new java.util.TreeSet<String>(); value.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) result.set(key, canonical(value.get(key))); return result;
        }
        if (value.isArray()) { var result = MAPPER.createArrayNode(); value.forEach(v -> result.add(canonical(v))); return result; }
        if (value.isNumber()) return com.fasterxml.jackson.databind.node.DecimalNode.valueOf(value.decimalValue().stripTrailingZeros());
        return value;
    }
}
