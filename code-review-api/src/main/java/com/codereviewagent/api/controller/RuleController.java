package com.codereviewagent.api.controller;

import com.codereviewagent.api.service.CurrentUser;
import com.codereviewagent.api.service.RuleService;
import com.codereviewagent.api.service.RuleService.RuleInput;
import com.codereviewagent.api.service.RuleService.RuleView;
import com.codereviewagent.api.service.RuleService.SetInput;
import com.codereviewagent.api.service.RuleService.SetView;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/** Maps CRUD operations for user-owned rules and rule sets. */
@RestController
@RequestMapping("/api/v1")
public class RuleController {
    private final RuleService rules;
    private final CurrentUser current;

    /**
     * Creates the rule endpoint adapter.
     *
     * @param rules rule use-case service
     * @param current authenticated-user resolver
     */
    public RuleController(RuleService rules, CurrentUser current) {
        this.rules = rules;
        this.current = current;
    }

    /**
     * Lists the authenticated user's rules.
     *
     * @param p authenticated principal
     * @return owner-scoped rules ordered by name
     */
    @GetMapping("/rules")
    public List<RuleView> listRules(Principal p) {
        return rules.listRules(current.id(p));
    }

    /**
     * Creates an owner-scoped rule.
     *
     * @param p authenticated principal
     * @param body rule definition and evaluation settings
     * @return created rule view
     */
    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    public RuleView createRule(Principal p, @RequestBody RuleInput body) {
        return rules.saveRule(current.id(p), null, body);
    }

    /**
     * Updates an owner-scoped rule and its version.
     *
     * @param p authenticated principal
     * @param id rule identifier
     * @param body replacement rule definition
     * @return updated rule view
     */
    @PutMapping("/rules/{id}")
    public RuleView updateRule(Principal p, @PathVariable String id, @RequestBody RuleInput body) {
        return rules.saveRule(current.id(p), id, body);
    }

    /**
     * Deletes an owner-scoped rule when no rule set references it.
     *
     * @param p authenticated principal
     * @param id rule identifier
     */
    @DeleteMapping("/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(Principal p, @PathVariable String id) {
        rules.deleteRule(current.id(p), id);
    }

    /**
     * Lists the authenticated user's rule sets.
     *
     * @param p authenticated principal
     * @return owner-scoped rule sets ordered by name
     */
    @GetMapping("/rule-sets")
    public List<SetView> listSets(Principal p) {
        return rules.listSets(current.id(p));
    }

    /**
     * Creates an owner-scoped rule set.
     *
     * @param p authenticated principal
     * @param body rule-set definition and member rule IDs
     * @return created rule-set view
     */
    @PostMapping("/rule-sets")
    @ResponseStatus(HttpStatus.CREATED)
    public SetView createSet(Principal p, @RequestBody SetInput body) {
        return rules.saveSet(current.id(p), null, body);
    }

    /**
     * Updates an owner-scoped rule set.
     *
     * @param p authenticated principal
     * @param id rule-set identifier
     * @param body replacement rule-set definition
     * @return updated rule-set view
     */
    @PutMapping("/rule-sets/{id}")
    public SetView updateSet(Principal p, @PathVariable String id, @RequestBody SetInput body) {
        return rules.saveSet(current.id(p), id, body);
    }

    /**
     * Deletes an owner-scoped rule set.
     *
     * @param p authenticated principal
     * @param id rule-set identifier
     */
    @DeleteMapping("/rule-sets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSet(Principal p, @PathVariable String id) {
        rules.deleteSet(current.id(p), id);
    }
}
