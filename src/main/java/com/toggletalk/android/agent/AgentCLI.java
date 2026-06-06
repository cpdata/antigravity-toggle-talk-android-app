package com.toggletalk.android.agent;

public interface AgentCLI {
    /**
     * Executes the agent with the given prompt.
     * @param sessionId The existing session ID, or null for a new session.
     * @param transcript The user's prompt text.
     * @param targetDir The directory context for the agent.
     * @param continueSession Whether to resume the previous session.
     */
    void run(String sessionId, String transcript, String targetDir, boolean continueSession);

    /**
     * Forces termination of the agent process.
     * @param sessionId The active session ID to terminate.
     */
    void terminate(String sessionId);

    /**
     * Returns the human-readable name of the agent.
     */
    String getName();

    /**
     * Returns the internal ID of the agent (e.g., "antigravity", "gemini").
     */
    String getId();

    /**
     * Path to the Termux harness script.
     */
    String getScriptPath();
}
