package com.codereviewagent.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.codereviewagent.api.model.UserAccount;
import com.codereviewagent.api.repository.RuleSetRepository;
import com.codereviewagent.api.repository.UserRepository;
import com.codereviewagent.api.service.RuleService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:review-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ReviewApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RuleSetRepository sets;
    @Autowired RuleService ruleService;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void account() {
        if (users.findByUsername("alice").isEmpty()) {
            UserAccount user =
                    users.save(
                            new UserAccount(UUID.randomUUID().toString(), "alice", "unused-hash"));
            ruleService.createDefaults(user.id);
        }
    }

    @Test
    @WithMockUser(username = "alice")
    void createsPasteReviewAndListsOwnHistory() throws Exception {
        String owner = users.findByUsername("alice").orElseThrow().id;
        String setId = sets.findByOwnerIdOrderByNameAsc(owner).get(0).id;
        mvc.perform(
                        post("/api/v1/reviews/paste")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"code\":\"// TODO fix\\n"
                                                + "class X"
                                                + " {}\",\"fileName\":\"X.java\",\"ruleSetId\":\""
                                                + setId
                                                + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.inputType").value("PASTE"))
                .andExpect(jsonPath("$.language").value("JAVA"));
        mvc.perform(get("/api/v1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].inputType").value("PASTE"));
    }

    @Test
    @WithMockUser(username = "alice")
    void requiresCsrfForMutationAndHidesMissingReview() throws Exception {
        mvc.perform(
                        post("/api/v1/reviews/paste")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reviews/not-owned")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "alice")
    void listsStarterRulesAndRuleSets() throws Exception {
        mvc.perform(get("/api/v1/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/v1/rule-sets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Starter rules"));
    }

    @Test
    @WithMockUser(username = "alice")
    void createsUpdatesAndDeletesPersonalRuleAndSet() throws Exception {
        String ruleJson =
                "{\"name\":\"Check"
                    + " literal\",\"description\":\"Example\",\"category\":\"STYLE\",\"severity\":\"LOW\",\"languages\":\"ALL\",\"instruction\":\"Find"
                    + " a marker\",\"matchText\":\"FIXME\",\"suggestedFix\":\"Resolve"
                    + " marker\",\"enabled\":true}";
        String created =
                mvc.perform(
                                post("/api/v1/rules")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(ruleJson))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.version").value(1))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String ruleId = mapper.readTree(created).path("id").asText();
        String updated = ruleJson.replace("Check literal", "Check marker");
        mvc.perform(
                        put("/api/v1/rules/" + ruleId)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
        String setJson =
                "{\"name\":\"My set\",\"description\":\"Example\",\"ruleIds\":[\""
                        + ruleId
                        + "\"],\"enabled\":true}";
        String setResult =
                mvc.perform(
                                post("/api/v1/rule-sets")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(setJson))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String setId = mapper.readTree(setResult).path("id").asText();
        mvc.perform(delete("/api/v1/rules/" + ruleId).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/rule-sets/" + setId).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/rules/" + ruleId).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "alice")
    void rejectsMalformedPasteAndMissingRuleSet() throws Exception {
        mvc.perform(
                        post("/api/v1/reviews/paste")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"code\":\"x\",\"fileName\":\"x.java\",\"ruleSetId\":\"missing\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/v1/reviews/paste")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"code\":\"x\",\"fileName\":\"../x.java\",\"language\":\"JAVA\",\"ruleSetId\":\"missing\"}"))
                .andExpect(status().isBadRequest());
    }
}
