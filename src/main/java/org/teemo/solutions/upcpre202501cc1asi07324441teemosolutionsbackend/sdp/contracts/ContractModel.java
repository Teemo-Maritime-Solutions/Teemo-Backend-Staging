package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.contracts;

import java.math.BigDecimal;
import java.util.List;

/** Inputs are contractual declarations, never inferred from physical route nodes. */
public final class ContractModel {
    private ContractModel() { }
    public enum Term { EXW, FCA, FAS, FOB, CPT, CIP, CFR, CIF, DAP, DPU, DDP }
    public enum Party { BUYER, SELLER }
    public enum Mode { SEA_INLAND_WATERWAY, MULTIMODAL, ROAD, RAIL, AIR }
    public enum Control { BUYER, SELLER, CARRIER, SHARED, UNKNOWN }
    public enum EventType {
        BEFORE_DELIVERY, GOODS_AVAILABLE, LOADED_AT_SELLER, READY_AT_OTHER_PLACE,
        HANDED_TO_CARRIER, ALONGSIDE_VESSEL, ON_BOARD_VESSEL, IN_TRANSIT,
        DESTINATION_READY_FOR_UNLOADING, DESTINATION_UNLOADED, AFTER_DESTINATION
    }
    public enum EvidenceKind { USER_DECLARED_SCENARIO, DOCUMENT_REFERENCE }
    public enum FeeKind {
        CHECK_PACK_MARK, EXPORT_CLEARANCE, TRANSIT_CLEARANCE, IMPORT_CLEARANCE_DUTY_TAX,
        TRANSPORT, DESTINATION_UNLOADING, INSURANCE, OTHER_AGREED
    }
    public record Evidence(EvidenceKind kind, String reference) { }
    public record Event(String id, EventType type, String place) { }
    public record Leg(String id, String fromEventId, String toEventId,
                      Control routingControl, Evidence controlEvidence) { }
    public record Carriage(String contractReference, Boolean destinationUnloadingIncluded) { }
    public record Cargo(String description, BigDecimal saleValue, Evidence valueEvidence) { }
    public record Fee(String id, FeeKind kind, String legId, BigDecimal amount,
                      Evidence evidence, Party agreedPayer) { }
    /** Each scenario is a separate hypothetical outcome, without probability or insurance recovery. */
    public record LossScenario(String id, String legId, BigDecimal damageFraction,
                               BigDecimal buyerAdditionalLoss, BigDecimal sellerAdditionalLoss,
                               Evidence evidence) { }
    public record Request(String expectedRuleVersion, Boolean acknowledgeResearchLimitations,
                          Term term, Mode mode, String saleContractReference,
                          String namedEventId, String deliveryEventId, String currency,
                          List<Event> events, List<Leg> legs, Carriage carriage, Cargo cargo,
                          List<Fee> fees, List<LossScenario> lossScenarios) { }
    public record LegAllocation(String legId, Party transportPayer, Party cargoRiskBearer,
                                Control routingControl, Evidence controlEvidence,
                                BigDecimal grossCargoValueAtRisk) { }
    public record FeeAllocation(String feeId, FeeKind kind, String legId, Party payer,
                                BigDecimal amount, Evidence evidence) { }
    public record ActorCosts(BigDecimal knownSubtotal, BigDecimal total, List<String> missingItems) { }
    public record ScenarioResult(String id, String legId, Party cargoRiskBearer,
                                 BigDecimal cargoLoss, BigDecimal buyerGrossLoss,
                                 BigDecimal sellerGrossLoss, BigDecimal insuranceRecovery,
                                 Evidence evidence) { }
    public record InsuranceRequirement(Party requiredArranger, String minimumCover,
                                       BigDecimal minimumInsuredAmount, String actualPolicyStatus) { }
    public record Result(String ruleVersion, String status, String currency,
                         Request declaredInputs, IncotermRules.Rule rule,
                         Party mainCarriageArranger, InsuranceRequirement insurance,
                         List<LegAllocation> legs, List<FeeAllocation> fees,
                         ActorCosts buyerCosts, ActorCosts sellerCosts,
                         List<ScenarioResult> conditionalLosses, List<String> missingData,
                         List<String> limitations) { }
}
