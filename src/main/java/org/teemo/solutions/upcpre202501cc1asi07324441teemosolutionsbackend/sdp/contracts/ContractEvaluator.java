package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.contracts;

import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.contracts.ContractModel.*;

@Service
public final class ContractEvaluator {
    private static final BigDecimal MAX_MONEY = new BigDecimal("1000000000000000");
    public Result evaluate(Request r) {
        require(r != null, "REQUEST_REQUIRED");
        require(Boolean.TRUE.equals(r.acknowledgeResearchLimitations()), "RESEARCH_ACK_REQUIRED");
        require(IncotermRules.VERSION.equals(r.expectedRuleVersion()), "RULE_VERSION_MISMATCH");
        require(r.term() != null && r.mode() != null, "TERM_AND_MODE_REQUIRED");
        text(r.saleContractReference(), "SALE_CONTRACT_REFERENCE_REQUIRED");
        require(r.currency() != null && r.currency().matches("[A-Z]{3}"), "INVALID_CURRENCY");
        int scale;
        try { scale = Currency.getInstance(r.currency()).getDefaultFractionDigits(); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("INVALID_CURRENCY"); }
        require(scale >= 0 && scale <= 4, "UNSUPPORTED_CURRENCY");
        var rule = IncotermRules.rule(r.term());
        require(!rule.seaOnly() || r.mode() == Mode.SEA_INLAND_WATERWAY, "SEA_TERM_REQUIRES_SEA_MODE");
        require(r.events() != null && r.events().size() >= 2 && r.events().size() <= 64, "EVENTS_REQUIRED_MAX_64");
        require(r.legs() != null && r.legs().size() == r.events().size() - 1, "CONTIGUOUS_LEGS_REQUIRED");
        Map<String, Integer> positions = new HashMap<>();
        int unloading = -1;
        for (int i = 0; i < r.events().size(); i++) {
            var event = r.events().get(i);
            require(event != null && event.type() != null, "INVALID_EVENT");
            text(event.id(), "EVENT_ID_REQUIRED"); text(event.place(), "PRECISE_EVENT_PLACE_REQUIRED");
            require(positions.put(event.id(), i) == null, "DUPLICATE_EVENT_ID");
            if (event.type() == EventType.DESTINATION_UNLOADED) {
                require(unloading == -1 && i > 0, "SINGLE_DESTINATION_UNLOADING_REQUIRED");
                var previous = r.events().get(i - 1);
                require(previous.type() == EventType.DESTINATION_READY_FOR_UNLOADING
                        && previous.place().equals(event.place()), "UNLOADING_MUST_FOLLOW_READY_AT_SAME_PLACE");
                unloading = i - 1;
            }
        }
        require(unloading >= 0, "DESTINATION_UNLOADING_EVENT_REQUIRED");
        Integer delivery = positions.get(r.deliveryEventId()), named = positions.get(r.namedEventId());
        require(delivery != null && named != null, "NAMED_AND_DELIVERY_EVENTS_REQUIRED");
        require(rule.deliveryTypes().contains(r.events().get(delivery).type()), "DELIVERY_EVENT_INCOMPATIBLE_WITH_TERM");
        if (IncotermRules.cTerm(r.term())) {
            require(named > delivery && named == unloading, "C_TERM_REQUIRES_SEPARATE_NAMED_DESTINATION_READY_EVENT");
        } else {
            require(named.equals(delivery), "NAMED_PLACE_MUST_BE_DELIVERY_POINT");
        }
        if (r.term() == Term.DPU) require(delivery == unloading + 1, "DPU_DELIVERY_AFTER_UNLOADING_REQUIRED");
        else if (IncotermRules.dTerm(r.term())) require(delivery == unloading, "D_TERM_DELIVERY_BEFORE_UNLOADING_REQUIRED");
        else require(delivery <= unloading, "DELIVERY_AFTER_DESTINATION_INVALID");
        require(r.carriage() != null && r.carriage().destinationUnloadingIncluded() != null, "CARRIAGE_TERMS_REQUIRED");
        text(r.carriage().contractReference(), "CARRIAGE_CONTRACT_REFERENCE_REQUIRED");
        require(r.cargo() != null, "CARGO_REQUIRED"); text(r.cargo().description(), "CARGO_DESCRIPTION_REQUIRED");
        if (r.cargo().saleValue() != null) {
            money(r.cargo().saleValue(), scale); evidence(r.cargo().valueEvidence());
        } else require(r.cargo().valueEvidence() == null, "VALUE_EVIDENCE_WITHOUT_VALUE");

        Party unloadPayer = r.term() == Term.DPU || (rule.mainCarriageArranger() == Party.SELLER
                && r.carriage().destinationUnloadingIncluded()) ? Party.SELLER : Party.BUYER;
        int carriageCutoff = IncotermRules.cTerm(r.term()) ? named : delivery;
        List<LegAllocation> legs = new ArrayList<>(); Map<String, LegAllocation> byLeg = new HashMap<>();
        LinkedHashMap<String, Party> requiredFees = new LinkedHashMap<>();
        requiredFees.put(FeeKind.CHECK_PACK_MARK.name(), Party.SELLER);
        requiredFees.put(FeeKind.EXPORT_CLEARANCE.name(), rule.exportClearance());
        requiredFees.put(FeeKind.TRANSIT_CLEARANCE.name(), rule.transitClearance());
        requiredFees.put(FeeKind.IMPORT_CLEARANCE_DUTY_TAX.name(), rule.importClearance());
        requiredFees.put(FeeKind.DESTINATION_UNLOADING.name(), unloadPayer);
        boolean insuredTerm = r.term() == Term.CIP || r.term() == Term.CIF;
        if (insuredTerm) requiredFees.put(FeeKind.INSURANCE.name(), Party.SELLER);
        String unloadLegId = null;
        for (int i = 0; i < r.legs().size(); i++) {
            var leg = r.legs().get(i);
            require(leg != null && leg.routingControl() != null, "LEG_AND_EXPLICIT_ROUTING_CONTROL_REQUIRED");
            text(leg.id(), "LEG_ID_REQUIRED");
            require(r.events().get(i).id().equals(leg.fromEventId())
                    && r.events().get(i + 1).id().equals(leg.toEventId()), "LEGS_MUST_FOLLOW_EVENT_ORDER");
            if (leg.routingControl() != Control.UNKNOWN) evidence(leg.controlEvidence());
            else require(leg.controlEvidence() == null, "UNKNOWN_CONTROL_CANNOT_HAVE_EVIDENCE");
            Party payer = i == unloading ? unloadPayer : i + 1 <= carriageCutoff ? Party.SELLER : Party.BUYER;
            Party risk = i + 1 <= delivery ? Party.SELLER : Party.BUYER;
            var allocation = new LegAllocation(leg.id(), payer, risk, leg.routingControl(), leg.controlEvidence(), r.cargo().saleValue());
            require(byLeg.put(leg.id(), allocation) == null, "DUPLICATE_LEG_ID"); legs.add(allocation);
            if (i == unloading) unloadLegId = leg.id();
            else requiredFees.put("TRANSPORT:" + leg.id(), payer);
        }
        require(r.fees() != null && r.fees().size() <= 128, "FEES_REQUIRED_MAX_128");
        Map<Party, BigDecimal> totals = new EnumMap<>(Party.class);
        Map<Party, List<String>> missing = new EnumMap<>(Party.class);
        for (Party p : Party.values()) { totals.put(p, BigDecimal.ZERO.setScale(scale)); missing.put(p, new ArrayList<>()); }
        Set<String> feeIds = new HashSet<>(), feeKeys = new HashSet<>();
        List<FeeAllocation> fees = new ArrayList<>();
        for (var fee : r.fees()) {
            require(fee != null && fee.kind() != null, "INVALID_FEE"); text(fee.id(), "FEE_ID_REQUIRED");
            require(feeIds.add(fee.id()), "DUPLICATE_FEE_ID");
            String key = fee.kind().name(); Party payer;
            if (fee.kind() == FeeKind.TRANSPORT) {
                require(byLeg.containsKey(fee.legId()) && !fee.legId().equals(unloadLegId), "TRANSPORT_LEG_INVALID_OR_UNLOADING_DUPLICATE");
                key += ":" + fee.legId(); payer = byLeg.get(fee.legId()).transportPayer();
            } else {
                require(fee.legId() == null, "NON_TRANSPORT_FEE_CANNOT_REFERENCE_LEG");
                payer = requiredFees.get(key);
            }
            if (fee.kind() == FeeKind.OTHER_AGREED || (fee.kind() == FeeKind.INSURANCE && !insuredTerm)) {
                require(fee.agreedPayer() != null, "AGREED_PAYER_REQUIRED"); evidence(fee.evidence());
                payer = fee.agreedPayer();
                if (fee.kind() == FeeKind.OTHER_AGREED) key += ":" + fee.id();
            } else require(fee.agreedPayer() == null, "BASE_RULE_PAYER_OVERRIDE_UNSUPPORTED");
            require(feeKeys.add(key), "DUPLICATE_COST_CATEGORY_OR_LEG");
            require(payer != null, "UNALLOCATED_FEE");
            if (fee.amount() == null) missing.get(payer).add(key);
            else {
                money(fee.amount(), scale); evidence(fee.evidence());
                totals.put(payer, totals.get(payer).add(fee.amount()));
            }
            fees.add(new FeeAllocation(fee.id(), fee.kind(), fee.legId(), payer, fee.amount(), fee.evidence()));
        }
        requiredFees.forEach((key, party) -> { if (!feeKeys.contains(key)) missing.get(party).add(key); });
        require(r.lossScenarios() != null && r.lossScenarios().size() <= 64, "LOSS_SCENARIOS_REQUIRED_MAX_64");
        List<ScenarioResult> scenarios = new ArrayList<>(); Set<String> scenarioIds = new HashSet<>();
        for (var scenario : r.lossScenarios()) {
            require(scenario != null, "INVALID_SCENARIO"); text(scenario.id(), "SCENARIO_ID_REQUIRED");
            require(scenarioIds.add(scenario.id()), "DUPLICATE_SCENARIO_ID");
            var leg = byLeg.get(scenario.legId()); require(leg != null, "SCENARIO_LEG_UNKNOWN");
            require(scenario.damageFraction() != null && scenario.damageFraction().signum() >= 0
                    && scenario.damageFraction().compareTo(BigDecimal.ONE) <= 0
                    && scenario.damageFraction().scale() <= 6, "INVALID_DAMAGE_FRACTION");
            money(scenario.buyerAdditionalLoss(), scale); money(scenario.sellerAdditionalLoss(), scale); evidence(scenario.evidence());
            BigDecimal loss = r.cargo().saleValue() == null ? null
                    : r.cargo().saleValue().multiply(scenario.damageFraction()).setScale(scale, RoundingMode.HALF_EVEN);
            BigDecimal buyer = loss == null && leg.cargoRiskBearer() == Party.BUYER ? null
                    : scenario.buyerAdditionalLoss().add(leg.cargoRiskBearer() == Party.BUYER ? loss : BigDecimal.ZERO);
            BigDecimal seller = loss == null && leg.cargoRiskBearer() == Party.SELLER ? null
                    : scenario.sellerAdditionalLoss().add(leg.cargoRiskBearer() == Party.SELLER ? loss : BigDecimal.ZERO);
            scenarios.add(new ScenarioResult(scenario.id(), scenario.legId(), leg.cargoRiskBearer(), loss, buyer, seller, null, scenario.evidence()));
        }
        List<String> missingData = new ArrayList<>(List.of("ACTUAL_INSURANCE_POLICY_AND_RECOVERY", "VALIDATED_LOSS_PROBABILITIES"));
        if (r.cargo().saleValue() == null) missingData.add("CARGO_SALE_VALUE");
        for (var leg : legs) if (leg.routingControl() == Control.UNKNOWN) missingData.add("ROUTING_CONTROL:" + leg.legId());
        for (Party p : Party.values()) for (String key : missing.get(p)) missingData.add(p + "_COST:" + key);
        var insurance = new InsuranceRequirement(insuredTerm ? Party.SELLER : null, rule.minimumInsuranceCover(),
                insuredTerm && r.cargo().saleValue() != null
                        ? r.cargo().saleValue().multiply(new BigDecimal("1.10")).setScale(scale, RoundingMode.CEILING) : null,
                "NOT_VERIFIED_NO_RECOVERY_ASSUMED");
        return new Result(IncotermRules.VERSION, missing.values().stream().allMatch(List::isEmpty)
                ? "DECLARED_COSTS_COMPLETE" : "PARTIAL_COSTS", r.currency(), r, rule, rule.mainCarriageArranger(), insurance,
                List.copyOf(legs), List.copyOf(fees), costs(Party.BUYER, totals, missing), costs(Party.SELLER, totals, missing),
                List.copyOf(scenarios), List.copyOf(missingData), List.of(
                "Ordinary performance of unmodified Incoterms 2020; default, notice failures and local legal exceptions are outside this model",
                "Named places and delivery events are declarations, not validated route positions or observed delivery",
                "Sale obligations do not establish title, payment terms, carrier liability or actual routing authority",
                "Costs exclude purchase price and unlisted charges; completeness means the declared model categories only",
                "Transport amounts must exclude separately itemised unloading, insurance and clearance to prevent double counting",
                "Cargo value at risk is repeated per leg and must not be summed across legs",
                "Loss scenarios are separate conditional outcomes; no expected loss, probabilities, insurance recovery or recommendation",
                "Currency is not converted; loss rounding is HALF_EVEN and minimum insurance amount rounds upward to currency minor units"));
    }
    private static ActorCosts costs(Party p, Map<Party, BigDecimal> totals, Map<Party, List<String>> missing) {
        return new ActorCosts(totals.get(p), missing.get(p).isEmpty() ? totals.get(p) : null, List.copyOf(missing.get(p)));
    }
    private static void money(BigDecimal amount, int scale) {
        require(amount != null && amount.signum() >= 0 && amount.compareTo(MAX_MONEY) <= 0
                && amount.scale() <= scale && amount.scale() >= -15 && amount.precision() <= 20, "INVALID_MONEY_AMOUNT_OR_MINOR_UNITS");
    }
    private static void evidence(Evidence evidence) {
        require(evidence != null && evidence.kind() != null, "EVIDENCE_REQUIRED"); text(evidence.reference(), "EVIDENCE_REFERENCE_REQUIRED");
    }
    private static void text(String value, String code) { require(value != null && !value.isBlank() && value.length() <= 512, code); }
    private static void require(boolean valid, String code) { if (!valid) throw new IllegalArgumentException(code); }
}
