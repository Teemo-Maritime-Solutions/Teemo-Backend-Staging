package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.contracts;

import java.util.*;
import static org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.contracts.ContractModel.*;

/** Original compact encoding of ordinary performance; not the ICC rulebook. */
public final class IncotermRules {
    public static final String VERSION = "INCOTERMS_2020_RESEARCH_1";
    private static final String ICC = "https://iccwbo.org/business-solutions/incoterms-rules/incoterms-2020/";
    private static final String ANY = "https://library.iccwbo.org/content/tfb/BOOKS/BK_0049/BK_0049_04_RulesAny.htm";
    private static final String SEA = "https://library.iccwbo.org/content/tfb/BOOKS/BK_0049/BK_0049_05_RulesSea.htm";
    public record Rule(Term term, boolean seaOnly, Set<EventType> deliveryTypes,
                       boolean namedPlaceIsDestination, Party mainCarriageArranger,
                       Party exportClearance, Party transitClearance, Party importClearance,
                       String minimumInsuranceCover, List<String> sources) { }
    private IncotermRules() { }
    public static boolean cTerm(Term term) { return Set.of(Term.CPT, Term.CIP, Term.CFR, Term.CIF).contains(term); }
    public static boolean dTerm(Term term) { return Set.of(Term.DAP, Term.DPU, Term.DDP).contains(term); }
    public static Rule rule(Term term) {
        Objects.requireNonNull(term, "term");
        Set<EventType> delivery = switch (term) {
            case EXW -> Set.of(EventType.GOODS_AVAILABLE);
            case FCA -> Set.of(EventType.LOADED_AT_SELLER, EventType.READY_AT_OTHER_PLACE);
            case FAS -> Set.of(EventType.ALONGSIDE_VESSEL);
            case FOB, CFR, CIF -> Set.of(EventType.ON_BOARD_VESSEL);
            case CPT, CIP -> Set.of(EventType.HANDED_TO_CARRIER);
            case DAP, DDP -> Set.of(EventType.DESTINATION_READY_FOR_UNLOADING);
            case DPU -> Set.of(EventType.DESTINATION_UNLOADED);
        };
        boolean sea = Set.of(Term.FAS, Term.FOB, Term.CFR, Term.CIF).contains(term);
        return new Rule(term, sea, delivery, cTerm(term) || dTerm(term),
                cTerm(term) || dTerm(term) ? Party.SELLER : Party.BUYER,
                term == Term.EXW ? Party.BUYER : Party.SELLER,
                dTerm(term) ? Party.SELLER : Party.BUYER,
                term == Term.DDP ? Party.SELLER : Party.BUYER,
                term == Term.CIP ? "INSTITUTE_CARGO_CLAUSES_A_OR_EQUIVALENT"
                        : term == Term.CIF ? "INSTITUTE_CARGO_CLAUSES_C_OR_EQUIVALENT" : "NO_MUTUAL_OBLIGATION",
                List.of(ICC, sea ? SEA : ANY));
    }
    public static List<Rule> catalogue() { return Arrays.stream(Term.values()).map(IncotermRules::rule).toList(); }
}
