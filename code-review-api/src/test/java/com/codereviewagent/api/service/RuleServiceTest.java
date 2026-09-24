package com.codereviewagent.api.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.codereviewagent.api.model.RuleEntity;
import com.codereviewagent.api.model.RuleSetEntity;
import com.codereviewagent.api.repository.RuleRepository;
import com.codereviewagent.api.repository.RuleSetRepository;
import com.codereviewagent.api.service.RuleService.RuleInput;
import com.codereviewagent.api.service.RuleService.SetInput;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

class RuleServiceTest {
    private RuleRepository rules;
    private RuleSetRepository sets;
    private RuleService service;

    @BeforeEach
    void setup() {
        rules = mock(RuleRepository.class);
        sets = mock(RuleSetRepository.class);
        service = new RuleService(rules, sets, new ObjectMapper());
        when(rules.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sets.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static RuleInput input(String match) {
        return new RuleInput(
                "No console",
                "Description",
                "STYLE",
                "LOW",
                "JAVASCRIPT",
                "Avoid console output",
                match,
                "Remove logging",
                true);
    }

    @Test
    void createsRuleAndIncrementsVersionOnEdit() {
        var created = service.saveRule("owner", null, input("console.log("));
        assertEquals(1, created.version());
        RuleEntity stored = new RuleEntity(created.id(), "owner");
        stored.version = 1;
        when(rules.findById(created.id())).thenReturn(Optional.of(stored));
        assertEquals(2, service.saveRule("owner", created.id(), input("console.log(")).version());
    }

    @Test
    void rejectsInvalidSeverityAndMultilineLiteral() {
        assertThrows(
                ResponseStatusException.class,
                () ->
                        service.saveRule(
                                "owner",
                                null,
                                new RuleInput(
                                        "X", "", "", "UNKNOWN", "ALL", "Check", "x", "Fix", true)));
        assertThrows(
                ResponseStatusException.class,
                () -> service.saveRule("owner", null, input("line\nbreak")));
    }

    @Test
    void deniesAccessToOtherUsersRule() {
        when(rules.findById("rule")).thenReturn(Optional.of(new RuleEntity("rule", "other")));
        assertThrows(ResponseStatusException.class, () -> service.ownedRule("rule", "owner"));
    }

    @Test
    void snapshotsOnlyEnabledOwnedRulesAndKeepsVersion() {
        RuleEntity enabled = new RuleEntity("one", "owner");
        enabled.name = "Check";
        enabled.severity = "HIGH";
        enabled.languages = "ALL";
        enabled.instruction = "Look for x";
        enabled.matchText = "x";
        enabled.enabled = true;
        enabled.version = 3;
        RuleEntity disabled = new RuleEntity("two", "owner");
        disabled.enabled = false;
        RuleSetEntity set = new RuleSetEntity("set", "owner");
        set.enabled = true;
        set.ruleIdsJson = "[\"one\",\"two\"]";
        when(sets.findById("set")).thenReturn(Optional.of(set));
        when(rules.findById("one")).thenReturn(Optional.of(enabled));
        when(rules.findById("two")).thenReturn(Optional.of(disabled));
        var snapshot = service.snapshot("owner", "set");
        assertEquals(1, snapshot.size());
        assertEquals(3, snapshot.get(0).version());
        assertEquals("one", snapshot.get(0).id());
    }

    @Test
    void rejectsEmptyOrDisabledSet() {
        assertThrows(
                ResponseStatusException.class,
                () -> service.saveSet("owner", null, new SetInput("X", "", List.of(), true)));
        RuleSetEntity set = new RuleSetEntity("set", "owner");
        set.enabled = false;
        set.ruleIdsJson = "[]";
        when(sets.findById("set")).thenReturn(Optional.of(set));
        assertThrows(ResponseStatusException.class, () -> service.snapshot("owner", "set"));
    }

    @Test
    void cannotDeleteRuleUsedBySet() {
        RuleEntity rule = new RuleEntity("rule", "owner");
        RuleSetEntity set = new RuleSetEntity("set", "owner");
        set.ruleIdsJson = "[\"rule\"]";
        when(rules.findById("rule")).thenReturn(Optional.of(rule));
        when(sets.findByOwnerIdOrderByNameAsc("owner")).thenReturn(List.of(set));
        assertThrows(ResponseStatusException.class, () -> service.deleteRule("owner", "rule"));
        verify(rules, never()).delete(any());
    }

    @Test
    void createsAndDeletesSetWithOwnedRule() {
        when(rules.findById("one")).thenReturn(Optional.of(new RuleEntity("one", "owner")));
        var created =
                service.saveSet("owner", null, new SetInput("Team set", "", List.of("one"), true));
        assertEquals(List.of("one"), created.ruleIds());
        RuleSetEntity stored = new RuleSetEntity(created.id(), "owner");
        stored.ruleIdsJson = "[\"one\"]";
        when(sets.findById(created.id())).thenReturn(Optional.of(stored));
        service.deleteSet("owner", created.id());
        verify(sets).delete(stored);
    }

    @Test
    void createsDefaultRulesAndSet() {
        java.util.Map<String, RuleEntity> saved = new java.util.HashMap<>();
        when(rules.save(any()))
                .thenAnswer(
                        inv -> {
                            RuleEntity rule = inv.getArgument(0);
                            saved.put(rule.id, rule);
                            return rule;
                        });
        when(rules.findById(any()))
                .thenAnswer(inv -> Optional.ofNullable(saved.get(inv.getArgument(0))));
        service.createDefaults("owner");
        verify(rules, times(3)).save(any());
        verify(sets).save(argThat(set -> set.enabled && set.name.equals("Starter rules")));
    }

    @Test
    void rejectsRuleSetWithDuplicateOrForeignRule() {
        assertThrows(
                ResponseStatusException.class,
                () ->
                        service.saveSet(
                                "owner", null, new SetInput("X", "", List.of("one", "one"), true)));
        when(rules.findById("other"))
                .thenReturn(Optional.of(new RuleEntity("other", "someone-else")));
        assertThrows(
                ResponseStatusException.class,
                () ->
                        service.saveSet(
                                "owner", null, new SetInput("X", "", List.of("other"), true)));
    }
}
