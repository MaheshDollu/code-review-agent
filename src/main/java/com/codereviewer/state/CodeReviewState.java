package com.codereviewer.state;

import org.bsc.langgraph4j.state.AgentState;
import java.util.*;

/**
 * Shared state flowing through every node in the LangGraph4J pipeline.
 * Each node returns Map.of("key", newValue) to update only its slice of state.
 */
public class CodeReviewState extends AgentState {

    public CodeReviewState(Map<String, Object> initData) {
        super(initData);
    }

    // PR metadata
    public Optional<String> prUrl()          { return value("prUrl"); }
    public Optional<String> owner()          { return value("owner"); }
    public Optional<String> repo()           { return value("repo"); }
    public Optional<Integer> prNumber()      { return value("prNumber"); }
    public Optional<String> prTitle()        { return value("prTitle"); }
    public Optional<String> prDescription()  { return value("prDescription"); }

    // Diff content fetched from GitHub API
    public Optional<String> diffContent()        { return value("diffContent"); }
    public Optional<List<String>> changedFiles() { return value("changedFiles"); }

    // Raw findings from each parallel agent
    public Optional<String> securityFindings() { return value("securityFindings"); }
    public Optional<String> logicFindings()    { return value("logicFindings"); }
    public Optional<String> styleFindings()    { return value("styleFindings"); }

    // Supervisor merged + ranked output
    public Optional<String> mergedFindings() { return value("mergedFindings"); }

    // Final markdown review ready to post
    public Optional<String> renderedReview() { return value("renderedReview"); }

    // HitL
    public Optional<String> threadId()     { return value("threadId"); }
    public Optional<String> hitlDecision() { return value("hitlDecision"); }

    // Status tracking
    public Optional<String> status() { return value("status"); }
    public Optional<String> error()  { return value("error"); }
}
