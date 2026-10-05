package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.contracts;

import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;
import static org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.contracts.ContractModel.*;

class ContractEvaluatorTest {
    static final Evidence SCENARIO = new Evidence(EvidenceKind.USER_DECLARED_SCENARIO, "Synthetic unit fixture; not a tariff or policy");
    static final ObjectMapper JSON = new ObjectMapper();
    static final ContractEvaluator ENGINE = new ContractEvaluator();
    static BigDecimal n(String value) { return new BigDecimal(value); }
    static Request fixture(Term term, int delivery) {
        var types = List.of(EventType.BEFORE_DELIVERY, EventType.GOODS_AVAILABLE, EventType.LOADED_AT_SELLER,
                EventType.READY_AT_OTHER_PLACE, EventType.HANDED_TO_CARRIER, EventType.ALONGSIDE_VESSEL,
                EventType.ON_BOARD_VESSEL, EventType.DESTINATION_READY_FOR_UNLOADING,
                EventType.DESTINATION_UNLOADED, EventType.AFTER_DESTINATION);
        List<Event> events = new ArrayList<>(); List<Leg> legs = new ArrayList<>(); List<Fee> fees = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) events.add(new Event("e" + i, types.get(i), i == 8 ? "point7" : "point" + i));
        for (int i = 0; i < 9; i++) {
            legs.add(new Leg("l" + i, "e" + i, "e" + (i + 1), Control.CARRIER, SCENARIO));
            if (i != 7) fees.add(new Fee("transport" + i, FeeKind.TRANSPORT, "l" + i, n("10.00"), SCENARIO, null));
        }
        for (var kind : List.of(FeeKind.CHECK_PACK_MARK, FeeKind.EXPORT_CLEARANCE, FeeKind.TRANSIT_CLEARANCE,
                FeeKind.IMPORT_CLEARANCE_DUTY_TAX, FeeKind.DESTINATION_UNLOADING))
            fees.add(new Fee(kind.name(), kind, null, n("100.00"), SCENARIO, null));
        if (term == Term.CIF || term == Term.CIP) fees.add(new Fee("insurance", FeeKind.INSURANCE, null, n("50.00"), SCENARIO, null));
        int named = Set.of(Term.CPT, Term.CIP, Term.CFR, Term.CIF).contains(term) ? 7 : delivery;
        return new Request(IncotermRules.VERSION, true, term, Mode.SEA_INLAND_WATERWAY,
                "Synthetic sale v1", "e" + named, "e" + delivery, "USD", events, legs,
                new Carriage("Independent synthetic carriage v1", false), new Cargo("Test cargo", n("10000.00"), SCENARIO),
                fees, List.of(new LossScenario("ocean-damage", "l6", n("0.10"), n("25.00"), n("5.00"), SCENARIO)));
    }
    static Request change(Request request, java.util.function.Consumer<JsonNode> edit) throws Exception {
        JsonNode tree = JSON.valueToTree(request); edit.accept(tree); return JSON.treeToValue(tree, Request.class);
    }
    @ParameterizedTest
    @CsvSource({"EXW,1,110,470", "FCA,2,220,360", "FCA,3,230,350", "FAS,5,250,330", "FOB,6,260,320",
            "CPT,4,270,310", "CIP,4,320,310", "CFR,6,270,310", "CIF,6,320,310", "DAP,7,370,210", "DPU,8,470,110", "DDP,7,470,110"})
    void allElevenTermsAndBothFcaVariants(Term term, int delivery, String seller, String buyer) {
        var result = ENGINE.evaluate(fixture(term, delivery));
        assertThat(result.sellerCosts().total()).isEqualByComparingTo(seller);
        assertThat(result.buyerCosts().total()).isEqualByComparingTo(buyer);
        assertThat(result.status()).isEqualTo("DECLARED_COSTS_COMPLETE");
        assertThat(result.fees().stream().map(FeeAllocation::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(result.sellerCosts().total().add(result.buyerCosts().total()));
        for (int i = 0; i < result.legs().size(); i++) {
            assertThat(result.legs().get(i).cargoRiskBearer()).isEqualTo(i < delivery ? Party.SELLER : Party.BUYER);
            assertThat(result.legs().get(i).routingControl()).isEqualTo(Control.CARRIER);
        }
    }
    @Test void cifSellerPaysWhileBuyerBearsOceanCargoRiskAndCarrierControlsRouting() {
        var result = ENGINE.evaluate(fixture(Term.CIF, 6)); var ocean = result.legs().get(6);
        assertThat(ocean.transportPayer()).isEqualTo(Party.SELLER);
        assertThat(ocean.cargoRiskBearer()).isEqualTo(Party.BUYER);
        assertThat(ocean.routingControl()).isEqualTo(Control.CARRIER);
        assertThat(result.conditionalLosses().get(0).buyerGrossLoss()).isEqualByComparingTo("1025");
        assertThat(result.conditionalLosses().get(0).sellerGrossLoss()).isEqualByComparingTo("5");
        assertThat(result.conditionalLosses().get(0).insuranceRecovery()).isNull();
        assertThat(result.insurance().minimumCover()).contains("CLAUSES_C");
        assertThat(result.insurance().minimumInsuredAmount()).isEqualByComparingTo("11000");
        assertThat(ENGINE.evaluate(fixture(Term.CIP, 4)).insurance().minimumCover()).contains("CLAUSES_A");
    }
    @ParameterizedTest @CsvSource({"CIF,6", "CIP,4", "CFR,6", "CPT,4", "DAP,7", "DDP,7"})
    void includedUnloadingChangesPayerWithoutMovingRisk(Term term, int delivery) throws Exception {
        var before = ENGINE.evaluate(fixture(term, delivery));
        var after = ENGINE.evaluate(change(fixture(term, delivery), j -> ((com.fasterxml.jackson.databind.node.ObjectNode) j.path("carriage")).put("destinationUnloadingIncluded", true)));
        assertThat(after.sellerCosts().total()).isEqualByComparingTo(before.sellerCosts().total().add(n("100")));
        assertThat(after.buyerCosts().total()).isEqualByComparingTo(before.buyerCosts().total().subtract(n("100")));
        assertThat(after.legs().get(7).cargoRiskBearer()).isEqualTo(Party.BUYER);
        assertThat(after.legs().get(7).transportPayer()).isEqualTo(Party.SELLER);
    }
    @Test void missingValuesAndUnknownControlRemainMissing() throws Exception {
        var request = change(fixture(Term.FOB, 6), j -> {
            ((com.fasterxml.jackson.databind.node.ObjectNode) j.path("cargo")).putNull("saleValue").putNull("valueEvidence");
            ((com.fasterxml.jackson.databind.node.ArrayNode) j.path("fees")).removeAll();
            ((com.fasterxml.jackson.databind.node.ObjectNode) j.path("legs").get(6)).put("routingControl", "UNKNOWN").putNull("controlEvidence");
        });
        var result = ENGINE.evaluate(request);
        assertThat(result.buyerCosts().total()).isNull(); assertThat(result.sellerCosts().total()).isNull();
        assertThat(result.buyerCosts().knownSubtotal()).isZero();
        assertThat(result.legs().get(6).grossCargoValueAtRisk()).isNull();
        assertThat(result.conditionalLosses().get(0).buyerGrossLoss()).isNull();
        assertThat(result.conditionalLosses().get(0).sellerGrossLoss()).isEqualByComparingTo("5");
        assertThat(result.missingData()).contains("CARGO_SALE_VALUE", "ROUTING_CONTROL:l6");
    }
    @Test void zeroWithProvenanceIsKnownAndMissingFeeAffectsOnlyItsActor() throws Exception {
        var request = change(fixture(Term.CIF, 6), j -> {
            ((com.fasterxml.jackson.databind.node.ObjectNode) j.path("fees").get(0)).putNull("amount");
            ((com.fasterxml.jackson.databind.node.ObjectNode) j.path("fees").get(7)).put("amount", 0);
        });
        var result = ENGINE.evaluate(request);
        assertThat(result.sellerCosts().total()).isNull();
        assertThat(result.sellerCosts().missingItems()).containsExactly("TRANSPORT:l0");
        assertThat(result.buyerCosts().total()).isEqualByComparingTo("300");
    }
    @Test void deterministicRoundingAndNoCrossScenarioSummation() throws Exception {
        var request = change(fixture(Term.DPU, 8), j -> {
            ((com.fasterxml.jackson.databind.node.ObjectNode) j.path("cargo")).put("saleValue", n("0.05"));
            ((com.fasterxml.jackson.databind.node.ObjectNode) j.path("lossScenarios").get(0)).put("damageFraction", n("0.5"));
            var copy = j.path("lossScenarios").get(0).deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) copy).put("id", "separate-alternative");
            ((com.fasterxml.jackson.databind.node.ArrayNode) j.path("lossScenarios")).add(copy);
        });
        var first = ENGINE.evaluate(request); assertThat(ENGINE.evaluate(request)).isEqualTo(first);
        assertThat(first.conditionalLosses()).hasSize(2);
        assertThat(first.conditionalLosses().get(0).cargoLoss()).isEqualByComparingTo("0.02");
        assertThat(first.conditionalLosses().get(0).sellerGrossLoss()).isEqualByComparingTo("5.02");
    }
    @Test void rejectsModeDeliveryPlaceGapsDuplicatesOverriddenPayersAndUnprovenMoney() throws Exception {
        var baseline = fixture(Term.CIF, 6);
        List<java.util.function.Consumer<JsonNode>> edits = List.of(
                j -> ((com.fasterxml.jackson.databind.node.ObjectNode) j).put("mode", "AIR"),
                j -> ((com.fasterxml.jackson.databind.node.ObjectNode) j).put("deliveryEventId", "e5"),
                j -> ((com.fasterxml.jackson.databind.node.ObjectNode) j).put("namedEventId", "e6"),
                j -> ((com.fasterxml.jackson.databind.node.ObjectNode) j.path("legs").get(0)).put("toEventId", "e2"),
                j -> ((com.fasterxml.jackson.databind.node.ObjectNode) j.path("events").get(1)).put("id", "e0"),
                j -> ((com.fasterxml.jackson.databind.node.ObjectNode) j.path("fees").get(0)).put("agreedPayer", "BUYER"),
                j -> ((com.fasterxml.jackson.databind.node.ObjectNode) j.path("fees").get(0)).putNull("evidence"),
                j -> ((com.fasterxml.jackson.databind.node.ObjectNode) j.path("fees").get(0)).put("amount", n("-1")),
                j -> ((com.fasterxml.jackson.databind.node.ObjectNode) j.path("fees").get(0)).put("amount", n("1.001")),
                j -> ((com.fasterxml.jackson.databind.node.ObjectNode) j.path("fees").get(0)).put("legId", "l7"),
                j -> ((com.fasterxml.jackson.databind.node.ArrayNode) j.path("fees")).add(j.path("fees").get(0).deepCopy()),
                j -> ((com.fasterxml.jackson.databind.node.ObjectNode) j.path("lossScenarios").get(0)).put("damageFraction", n("1.01")),
                j -> ((com.fasterxml.jackson.databind.node.ObjectNode) j.path("events").get(8)).put("place", "different destination"));
        for (var edit : edits) {
            var invalid = change(baseline, edit);
            assertThatThrownBy(() -> ENGINE.evaluate(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void additionalLossPayerIsExplicitAndIndependentOfIncoterm() throws Exception {
        var result = ENGINE.evaluate(fixture(Term.DAP, 7));
        assertThat(result.conditionalLosses().get(0).buyerGrossLoss()).isEqualByComparingTo("25");
        assertThat(result.conditionalLosses().get(0).sellerGrossLoss()).isEqualByComparingTo("1005");
        var request = change(fixture(Term.FOB, 6), j -> {
            ((com.fasterxml.jackson.databind.node.ArrayNode) j.path("fees")).add(JSON.valueToTree(
                    new Fee("agreed-delay-charge", FeeKind.OTHER_AGREED, null, n("12.00"), SCENARIO, Party.SELLER)));
        });
        assertThat(ENGINE.evaluate(request).sellerCosts().total()).isEqualByComparingTo("272");
    }
}
