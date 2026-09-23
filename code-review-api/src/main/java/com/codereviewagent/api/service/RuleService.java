package com.codereviewagent.api.service;

import com.codereviewagent.api.model.RuleEntity;
import com.codereviewagent.api.model.RuleSetEntity;
import com.codereviewagent.api.repository.RuleRepository;
import com.codereviewagent.api.repository.RuleSetRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RuleService {
    public record RuleInput(String name, String description, String category, String severity,
                            String languages, String instruction, String matchText, String suggestedFix, boolean enabled) {}
    public record RuleView(String id, String name, String description, String category, String severity,
                           String languages, String instruction, String matchText, String suggestedFix,
                           boolean enabled, int version) {}
    public record SetInput(String name, String description, List<String> ruleIds, boolean enabled) {}
    public record SetView(String id, String name, String description, List<String> ruleIds, boolean enabled) {}
    public record RuleSnapshot(String id, int version, String name, String severity, String languages,
                               String instruction, String matchText, String suggestedFix) {}

    private static final Set<String> SEVERITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO");
    private static final Set<String> LANGUAGES = Set.of("ALL", "JAVA", "JAVASCRIPT", "TYPESCRIPT", "JSX", "TSX",
            "PYTHON", "GO", "CSHARP", "RUBY", "PHP", "RUST", "KOTLIN", "SWIFT", "C", "CPP", "VUE", "SQL");
    private final RuleRepository rules;
    private final RuleSetRepository sets;
    private final ObjectMapper mapper;
    public RuleService(RuleRepository rules, RuleSetRepository sets, ObjectMapper mapper) {
        this.rules = rules; this.sets = sets; this.mapper = mapper;
    }

    public List<RuleView> listRules(String owner) { return rules.findByOwnerIdOrderByNameAsc(owner).stream().map(this::view).toList(); }
    public List<SetView> listSets(String owner) { return sets.findByOwnerIdOrderByNameAsc(owner).stream().map(this::view).toList(); }
    public RuleEntity ownedRule(String id, String owner) {
        RuleEntity rule = rules.findById(id).orElseThrow(() -> missing("Rule"));
        if (!rule.ownerId.equals(owner)) throw missing("Rule");
        return rule;
    }
    public RuleSetEntity ownedSet(String id, String owner) {
        RuleSetEntity set = sets.findById(id).orElseThrow(() -> missing("Rule set"));
        if (!set.ownerId.equals(owner)) throw missing("Rule set");
        return set;
    }

    @Transactional
    public RuleView saveRule(String owner, String id, RuleInput input) {
        if (input == null || blank(input.name()) || input.name().length() > 120 || blank(input.instruction())
                || input.instruction().length() > 4000 || input.severity() == null || !SEVERITIES.contains(input.severity())
                || blank(input.languages()) || input.languages().length() > 250
                || !input.languages().matches("(?i)[A-Z]+(\\s*,\\s*[A-Z]+)*")
                || Arrays.stream(input.languages().toUpperCase(java.util.Locale.ROOT).split(","))
                    .map(String::trim).anyMatch(language -> !LANGUAGES.contains(language))
                || (input.description() != null && input.description().length() > 2000)
                || (input.category() != null && input.category().length() > 200)
                || (input.suggestedFix() != null && input.suggestedFix().length() > 2000)
                || (input.matchText() != null && (input.matchText().length() > 500 || input.matchText().contains("\n")))) {
            throw invalid("Invalid rule fields");
        }
        RuleEntity rule = id == null ? new RuleEntity(UUID.randomUUID().toString(), owner) : ownedRule(id, owner);
        rule.name = input.name().trim(); rule.description = input.description(); rule.category = input.category();
        rule.severity = input.severity(); rule.languages = input.languages().trim().toUpperCase(java.util.Locale.ROOT);
        rule.instruction = input.instruction().trim(); rule.matchText = input.matchText();
        rule.suggestedFix = input.suggestedFix(); rule.enabled = input.enabled();
        rule.version++; rule.updatedAt = Instant.now();
        return view(rules.save(rule));
    }

    @Transactional
    public void deleteRule(String owner, String id) {
        RuleEntity rule = ownedRule(id, owner);
        if (sets.findByOwnerIdOrderByNameAsc(owner).stream().anyMatch(set -> ids(set).contains(id))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Remove rule from rule sets first");
        }
        rules.delete(rule);
    }

    @Transactional
    public SetView saveSet(String owner, String id, SetInput input) {
        if (input == null || blank(input.name()) || input.name().length() > 120 || input.ruleIds() == null
                || input.ruleIds().isEmpty() || input.ruleIds().size() > 100
                || input.ruleIds().stream().anyMatch(idValue -> idValue == null || idValue.isBlank())
                || input.ruleIds().stream().distinct().count() != input.ruleIds().size()
                || (input.description() != null && input.description().length() > 2000)) {
            throw invalid("Rule set needs a name and unique rules");
        }
        for (String ruleId : input.ruleIds()) ownedRule(ruleId, owner);
        RuleSetEntity set = id == null ? new RuleSetEntity(UUID.randomUUID().toString(), owner) : ownedSet(id, owner);
        set.name = input.name().trim(); set.description = input.description();
        set.ruleIdsJson = write(input.ruleIds()); set.enabled = input.enabled(); set.updatedAt = Instant.now();
        return view(sets.save(set));
    }

    @Transactional
    public void deleteSet(String owner, String id) { sets.delete(ownedSet(id, owner)); }

    public List<RuleSnapshot> snapshot(String owner, String setId) {
        RuleSetEntity set = ownedSet(setId, owner);
        if (!set.enabled) throw invalid("Rule set is disabled");
        List<RuleSnapshot> result = new ArrayList<>();
        for (String id : ids(set)) {
            RuleEntity rule = ownedRule(id, owner);
            if (rule.enabled) result.add(new RuleSnapshot(rule.id, rule.version, rule.name, rule.severity,
                    rule.languages, rule.instruction, rule.matchText, rule.suggestedFix));
        }
        if (result.isEmpty()) throw invalid("Rule set has no enabled rules");
        return List.copyOf(result);
    }

    public List<String> ids(RuleSetEntity set) {
        try { return mapper.readValue(set.ruleIdsJson, new TypeReference<List<String>>() {}); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid stored rule set", e); }
    }
    public String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not serialize rules", e); }
    }
    private RuleView view(RuleEntity r) { return new RuleView(r.id, r.name, r.description, r.category,
            r.severity, r.languages, r.instruction, r.matchText, r.suggestedFix, r.enabled, r.version); }
    private SetView view(RuleSetEntity s) { return new SetView(s.id, s.name, s.description, ids(s), s.enabled); }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException missing(String resource) { return new ResponseStatusException(HttpStatus.NOT_FOUND, resource + " not found"); }

    @Transactional
    public void createDefaults(String owner) {
        RuleView java = saveRule(owner, null, new RuleInput("Avoid System.out", "Use a logger instead of console output",
                "MAINTAINABILITY", "LOW", "JAVA", "Flag direct System.out printing", "System.out.print", "Use a logger", true));
        RuleView js = saveRule(owner, null, new RuleInput("Avoid console.log", "Remove debug logging before release",
                "MAINTAINABILITY", "LOW", "JAVASCRIPT,TYPESCRIPT,JSX,TSX", "Flag console.log calls", "console.log(", "Use application logging or remove debug output", true));
        RuleView todo = saveRule(owner, null, new RuleInput("TODO marker", "Resolve unfinished work",
                "MAINTAINABILITY", "INFO", "ALL", "Flag TODO markers", "TODO", "Complete or track this work", true));
        saveSet(owner, null, new SetInput("Starter rules", "Basic deterministic checks",
                List.of(java.id(), js.id(), todo.id()), true));
    }
}
