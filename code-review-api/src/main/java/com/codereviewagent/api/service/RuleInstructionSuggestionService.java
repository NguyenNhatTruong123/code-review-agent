package com.codereviewagent.api.service;

import com.codereviewagent.ai.service.AiReviewService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.http.HttpTimeoutException;

/** Generates a user-reviewable rule instruction through the configured AI provider. */
@Service
public class RuleInstructionSuggestionService {
    /**
     * Input used to produce a draft rule instruction.
     *
     * @param name rule name supplied by the user
     * @param description rule description supplied by the user
     */
    public record SuggestionInput(String name, String description) {}

    /**
     * Draft instruction returned for user review without persisting it.
     *
     * @param instruction generated draft instruction
     */
    public record SuggestionView(String instruction) {}

    private final AiReviewService ai;

    /**
     * Creates the rule-instruction suggestion use case.
     *
     * @param ai configured AI provider adapter
     */
    public RuleInstructionSuggestionService(AiReviewService ai) {
        this.ai = ai;
    }

    /**
     * Generates a bounded instruction draft from a rule name and description.
     *
     * @param input user-supplied rule context
     * @return an unpersisted instruction draft for the user to review
     * @throws ResponseStatusException when input is invalid or the provider is unavailable
     */
    public SuggestionView suggest(SuggestionInput input) {
        if (input == null
                || blank(input.name())
                || input.name().length() > 120
                || blank(input.description())
                || input.description().length() > 2000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Rule name and description are required");
        }

        try {
            String instruction =
                    ai.suggestRuleInstruction(input.name().trim(), input.description().trim());
            if (instruction == null || instruction.isBlank() || instruction.length() > 4000) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "AI returned an invalid instruction suggestion");
            }
            return new SuggestionView(instruction);
        } catch (HttpTimeoutException e) {
            throw new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT, "AI suggestion timed out. Try again.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "AI suggestion was interrupted. Try again.");
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "AI suggestion is unavailable. Try again later.");
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI suggestions are unavailable: configure OPENROUTER_API_KEY and restart"
                            + " the backend");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
