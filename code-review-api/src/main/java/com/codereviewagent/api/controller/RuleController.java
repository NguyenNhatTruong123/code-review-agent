package com.codereviewagent.api.controller;

import com.codereviewagent.api.service.CurrentUser;
import com.codereviewagent.api.service.RuleService;
import com.codereviewagent.api.service.RuleService.RuleInput;
import com.codereviewagent.api.service.RuleService.RuleView;
import com.codereviewagent.api.service.RuleService.SetInput;
import com.codereviewagent.api.service.RuleService.SetView;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class RuleController {
    private final RuleService rules;
    private final CurrentUser current;
    public RuleController(RuleService rules, CurrentUser current) { this.rules = rules; this.current = current; }
    @GetMapping("/rules") public List<RuleView> listRules(Principal p) { return rules.listRules(current.id(p)); }
    @PostMapping("/rules") @ResponseStatus(HttpStatus.CREATED)
    public RuleView createRule(Principal p, @RequestBody RuleInput body) { return rules.saveRule(current.id(p), null, body); }
    @PutMapping("/rules/{id}")
    public RuleView updateRule(Principal p, @PathVariable String id, @RequestBody RuleInput body) { return rules.saveRule(current.id(p), id, body); }
    @DeleteMapping("/rules/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(Principal p, @PathVariable String id) { rules.deleteRule(current.id(p), id); }
    @GetMapping("/rule-sets") public List<SetView> listSets(Principal p) { return rules.listSets(current.id(p)); }
    @PostMapping("/rule-sets") @ResponseStatus(HttpStatus.CREATED)
    public SetView createSet(Principal p, @RequestBody SetInput body) { return rules.saveSet(current.id(p), null, body); }
    @PutMapping("/rule-sets/{id}")
    public SetView updateSet(Principal p, @PathVariable String id, @RequestBody SetInput body) { return rules.saveSet(current.id(p), id, body); }
    @DeleteMapping("/rule-sets/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSet(Principal p, @PathVariable String id) { rules.deleteSet(current.id(p), id); }
}
