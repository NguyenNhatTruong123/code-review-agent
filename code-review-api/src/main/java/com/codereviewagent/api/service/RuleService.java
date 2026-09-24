package com.codereviewagent.api.service;

import com.codereviewagent.api.model.RuleEntity;
import com.codereviewagent.api.model.RuleSetEntity;
import com.codereviewagent.api.repository.RuleRepository;
import com.codereviewagent.api.repository.RuleSetRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Validates user rules and creates immutable snapshots for each review. */
@Service
public class RuleService {
    /**
     * Client input for creating or updating one rule.
     *
     * @param name display name
     * @param description human-readable rule description
     * @param category rule category
     * @param severity configured severity
     * @param languages comma-separated supported languages
     * @param instruction semantic instruction
     * @param matchText optional case-sensitive literal match
     * @param suggestedFix remediation guidance
     * @param enabled whether the rule participates in snapshots
     */
    public record RuleInput(
            String name,
            String description,
            String category,
            String severity,
            String languages,
            String instruction,
            String matchText,
            String suggestedFix,
            boolean enabled) {}

    /**
     * Rule data exposed to the authenticated owner.
     *
     * @param id rule identifier
     * @param name display name
     * @param description rule description
     * @param category rule category
     * @param severity configured severity
     * @param languages supported languages
     * @param instruction semantic instruction
     * @param matchText optional literal match
     * @param suggestedFix remediation guidance
     * @param enabled whether the rule is active
     * @param version current rule version
     */
    public record RuleView(
            String id,
            String name,
            String description,
            String category,
            String severity,
            String languages,
            String instruction,
            String matchText,
            String suggestedFix,
            boolean enabled,
            int version) {}

    /**
     * Client input for creating or updating a rule set.
     *
     * @param name display name
     * @param description rule-set description
     * @param ruleIds ordered owner-scoped rule identifiers
     * @param enabled whether the set can be selected for a review
     */
    public record SetInput(
            String name, String description, List<String> ruleIds, boolean enabled) {}

    /**
     * Rule set data exposed to the authenticated owner.
     *
     * @param id rule-set identifier
     * @param name display name
     * @param description rule-set description
     * @param ruleIds ordered member rule identifiers
     * @param enabled whether the set is active
     */
    public record SetView(
            String id, String name, String description, List<String> ruleIds, boolean enabled) {}

    /**
     * Versioned rule data embedded in a review to prevent later edits changing its meaning.
     *
     * @param id rule identifier
     * @param version rule version at snapshot time
     * @param name rule name
     * @param severity finding severity
     * @param languages supported languages
     * @param instruction semantic instruction
     * @param matchText optional literal match
     * @param suggestedFix remediation guidance
     */
    public record RuleSnapshot(
            String id,
            int version,
            String name,
            String severity,
            String languages,
            String instruction,
            String matchText,
            String suggestedFix) {}

    private static final Set<String> SEVERITIES =
            Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO");
    private static final Set<String> LANGUAGES =
            Set.of(
                    "ALL",
                    "JAVA",
                    "JAVASCRIPT",
                    "TYPESCRIPT",
                    "JSX",
                    "TSX",
                    "PYTHON",
                    "GO",
                    "CSHARP",
                    "RUBY",
                    "PHP",
                    "RUST",
                    "KOTLIN",
                    "SWIFT",
                    "C",
                    "CPP",
                    "VUE",
                    "SQL");
    private final RuleRepository rules;
    private final RuleSetRepository sets;
    private final ObjectMapper mapper;

    /**
     * Creates the rule use-case service and its persistence/serialization collaborators.
     *
     * @param rules rule repository
     * @param sets rule-set repository
     * @param mapper JSON mapper for persisted rule IDs and snapshots
     */
    public RuleService(RuleRepository rules, RuleSetRepository sets, ObjectMapper mapper) {
        this.rules = rules;
        this.sets = sets;
        this.mapper = mapper;
    }

    /**
     * Lists rules belonging to one owner.
     *
     * @param owner authenticated application user ID
     * @return owner-scoped rules ordered by name
     */
    public List<RuleView> listRules(String owner) {
        return rules.findByOwnerIdOrderByNameAsc(owner).stream().map(this::view).toList();
    }

    /**
     * Lists rule sets belonging to one owner.
     *
     * @param owner authenticated application user ID
     * @return owner-scoped rule sets ordered by name
     */
    public List<SetView> listSets(String owner) {
        return sets.findByOwnerIdOrderByNameAsc(owner).stream().map(this::view).toList();
    }

    /**
     * Loads a rule and hides whether a missing ID belongs to another owner.
     *
     * @param id rule identifier
     * @param owner authenticated application user ID
     * @return owned rule entity
     * @throws ResponseStatusException when the rule is missing or foreign
     */
    public RuleEntity ownedRule(String id, String owner) {
        RuleEntity rule = rules.findById(id).orElseThrow(() -> missing("Rule"));
        if (!rule.ownerId.equals(owner)) {
            throw missing("Rule");
        }
        return rule;
    }

    /**
     * Loads a rule set with the same owner isolation as rules.
     *
     * @param id rule-set identifier
     * @param owner authenticated application user ID
     * @return owned rule-set entity
     * @throws ResponseStatusException when the set is missing or foreign
     */
    public RuleSetEntity ownedSet(String id, String owner) {
        RuleSetEntity set = sets.findById(id).orElseThrow(() -> missing("Rule set"));
        if (!set.ownerId.equals(owner)) {
            throw missing("Rule set");
        }
        return set;
    }

    /**
     * Validates and saves a rule, incrementing its snapshot version on every mutation.
     *
     * @param owner authenticated application user ID
     * @param id existing rule ID, or {@code null} to create
     * @param input rule fields to validate and persist
     * @return persisted rule view
     * @throws ResponseStatusException when fields are invalid or the rule is foreign
     */
    @Transactional
    public RuleView saveRule(String owner, String id, RuleInput input) {
        if (input == null
                || blank(input.name())
                || input.name().length() > 120
                || blank(input.instruction())
                || input.instruction().length() > 4000
                || input.severity() == null
                || !SEVERITIES.contains(input.severity())
                || blank(input.languages())
                || input.languages().length() > 250
                || !input.languages().matches("(?i)[A-Z]+(\\s*,\\s*[A-Z]+)*")
                || Arrays.stream(input.languages().toUpperCase(java.util.Locale.ROOT).split(","))
                        .map(String::trim)
                        .anyMatch(language -> !LANGUAGES.contains(language))
                || (input.description() != null && input.description().length() > 2000)
                || (input.category() != null && input.category().length() > 200)
                || (input.suggestedFix() != null && input.suggestedFix().length() > 2000)
                || (input.matchText() != null
                        && (input.matchText().length() > 500
                                || input.matchText().contains("\n")))) {
            throw invalid("Invalid rule fields");
        }
        RuleEntity rule =
                id == null
                        ? new RuleEntity(UUID.randomUUID().toString(), owner)
                        : ownedRule(id, owner);
        rule.name = input.name().trim();
        rule.description = input.description();
        rule.category = input.category();
        rule.severity = input.severity();
        rule.languages = input.languages().trim().toUpperCase(java.util.Locale.ROOT);
        rule.instruction = input.instruction().trim();
        rule.matchText = input.matchText();
        rule.suggestedFix = input.suggestedFix();
        rule.enabled = input.enabled();
        rule.version++;
        rule.updatedAt = Instant.now();
        return view(rules.save(rule));
    }

    /**
     * Deletes an owned rule unless a rule set still references it.
     *
     * @param owner authenticated application user ID
     * @param id rule identifier
     * @throws ResponseStatusException when the rule is foreign or still referenced
     */
    @Transactional
    public void deleteRule(String owner, String id) {
        RuleEntity rule = ownedRule(id, owner);
        if (sets.findByOwnerIdOrderByNameAsc(owner).stream()
                .anyMatch(set -> ids(set).contains(id))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Remove rule from rule sets first");
        }
        rules.delete(rule);
    }

    /**
     * Validates and saves an owner-scoped rule set with unique rule IDs.
     *
     * @param owner authenticated application user ID
     * @param id existing set ID, or {@code null} to create
     * @param input rule-set fields and member IDs
     * @return persisted rule-set view
     * @throws ResponseStatusException when fields or member ownership are invalid
     */
    @Transactional
    public SetView saveSet(String owner, String id, SetInput input) {
        if (input == null
                || blank(input.name())
                || input.name().length() > 120
                || input.ruleIds() == null
                || input.ruleIds().isEmpty()
                || input.ruleIds().size() > 100
                || input.ruleIds().stream()
                        .anyMatch(idValue -> idValue == null || idValue.isBlank())
                || input.ruleIds().stream().distinct().count() != input.ruleIds().size()
                || (input.description() != null && input.description().length() > 2000)) {
            throw invalid("Rule set needs a name and unique rules");
        }
        for (String ruleId : input.ruleIds()) {
            ownedRule(ruleId, owner);
        }
        RuleSetEntity set =
                id == null
                        ? new RuleSetEntity(UUID.randomUUID().toString(), owner)
                        : ownedSet(id, owner);
        set.name = input.name().trim();
        set.description = input.description();
        set.ruleIdsJson = write(input.ruleIds());
        set.enabled = input.enabled();
        set.updatedAt = Instant.now();
        return view(sets.save(set));
    }

    /**
     * Deletes an owned rule set.
     *
     * @param owner authenticated application user ID
     * @param id rule-set identifier
     * @throws ResponseStatusException when the set is missing or foreign
     */
    @Transactional
    public void deleteSet(String owner, String id) {
        sets.delete(ownedSet(id, owner));
    }

    /**
     * Captures enabled, versioned rules used by a review run.
     *
     * @param owner authenticated application user ID
     * @param setId selected rule-set identifier
     * @return immutable list of enabled rule snapshots
     * @throws ResponseStatusException when the set is disabled, foreign, or has no enabled rules
     */
    public List<RuleSnapshot> snapshot(String owner, String setId) {
        // Snapshot only enabled, owner-checked rules so a running review has stable inputs.
        RuleSetEntity set = ownedSet(setId, owner);
        if (!set.enabled) {
            throw invalid("Rule set is disabled");
        }
        List<RuleSnapshot> result = new ArrayList<>();
        for (String id : ids(set)) {
            RuleEntity rule = ownedRule(id, owner);
            if (rule.enabled) {
                result.add(
                        new RuleSnapshot(
                                rule.id,
                                rule.version,
                                rule.name,
                                rule.severity,
                                rule.languages,
                                rule.instruction,
                                rule.matchText,
                                rule.suggestedFix));
            }
        }
        if (result.isEmpty()) {
            throw invalid("Rule set has no enabled rules");
        }
        return List.copyOf(result);
    }

    /**
     * Captures an immutable snapshot of specifically selected enabled rules.
     *
     * @param owner authenticated application user ID
     * @param ruleIds ordered owner-scoped rule identifiers
     * @return immutable list of selected rule snapshots
     * @throws ResponseStatusException when no rules are selected, an ID is invalid, or a selected
     *     rule is disabled or foreign
     */
    public List<RuleSnapshot> snapshotRules(String owner, List<String> ruleIds) {
        if (ruleIds == null
                || ruleIds.isEmpty()
                || ruleIds.size() > 100
                || ruleIds.stream().anyMatch(id -> id == null || id.isBlank())
                || ruleIds.stream().distinct().count() != ruleIds.size()) {
            throw invalid("Select one or more unique rules");
        }

        List<RuleSnapshot> result = new ArrayList<>();
        for (String id : ruleIds) {
            RuleEntity rule = ownedRule(id, owner);
            if (!rule.enabled) {
                throw invalid("Selected rules must be enabled");
            }
            result.add(
                    new RuleSnapshot(
                            rule.id,
                            rule.version,
                            rule.name,
                            rule.severity,
                            rule.languages,
                            rule.instruction,
                            rule.matchText,
                            rule.suggestedFix));
        }

        return List.copyOf(result);
    }

    /**
     * Decodes the persisted rule ID list and surfaces corrupt storage as an application error.
     *
     * @param set persisted rule set
     * @return ordered member rule IDs
     * @throws IllegalStateException when stored JSON is invalid
     */
    public List<String> ids(RuleSetEntity set) {
        try {
            return mapper.readValue(set.ruleIdsJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid stored rule set", e);
        }
    }

    /**
     * Serializes review rule data for persistence in a snapshot.
     *
     * @param value rule data to serialize
     * @return JSON representation
     * @throws IllegalStateException when the value cannot be serialized
     */
    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize rules", e);
        }
    }

    /**
     * Decodes the immutable rule snapshot recorded for a completed review.
     *
     * @param value persisted rule snapshot JSON
     * @return immutable versioned rule snapshots
     * @throws IllegalStateException when the stored snapshot is unavailable or invalid
     */
    public List<RuleSnapshot> readSnapshot(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Review rule snapshot is unavailable");
        }

        try {
            RuleSnapshot[] snapshots = mapper.readValue(value, RuleSnapshot[].class);
            if (snapshots == null
                    || snapshots.length == 0
                    || Arrays.stream(snapshots).anyMatch(snapshot -> snapshot == null)) {
                throw new IllegalStateException("Review rule snapshot is unavailable");
            }
            return List.copyOf(Arrays.asList(snapshots));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Review rule snapshot is invalid", e);
        }
    }

    private RuleView view(RuleEntity r) {
        return new RuleView(
                r.id,
                r.name,
                r.description,
                r.category,
                r.severity,
                r.languages,
                r.instruction,
                r.matchText,
                r.suggestedFix,
                r.enabled,
                r.version);
    }

    private SetView view(RuleSetEntity s) {
        return new SetView(s.id, s.name, s.description, ids(s), s.enabled);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException missing(String resource) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, resource + " not found");
    }

    /**
     * Creates the starter rules and set for a newly registered owner.
     *
     * @param owner newly registered application user ID
     */
    @Transactional
    public void createDefaults(String owner) {
        RuleView java =
                saveRule(
                        owner,
                        null,
                        new RuleInput(
                                "Avoid System.out",
                                "Use a logger instead of console output",
                                "MAINTAINABILITY",
                                "LOW",
                                "JAVA",
                                "Flag direct System.out printing",
                                "System.out.print",
                                "Use a logger",
                                true));
        RuleView js =
                saveRule(
                        owner,
                        null,
                        new RuleInput(
                                "Avoid console.log",
                                "Remove debug logging before release",
                                "MAINTAINABILITY",
                                "LOW",
                                "JAVASCRIPT,TYPESCRIPT,JSX,TSX",
                                "Flag console.log calls",
                                "console.log(",
                                "Use application logging or remove debug output",
                                true));
        RuleView todo =
                saveRule(
                        owner,
                        null,
                        new RuleInput(
                                "TODO marker",
                                "Resolve unfinished work",
                                "MAINTAINABILITY",
                                "INFO",
                                "ALL",
                                "Flag TODO markers",
                                "TODO",
                                "Complete or track this work",
                                true));
        saveSet(
                owner,
                null,
                new SetInput(
                        "Starter rules",
                        "Basic deterministic checks",
                        List.of(java.id(), js.id(), todo.id()),
                        true));
    }
}
