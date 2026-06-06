package com.toggletalk.android.agent;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public class AgentManager {
    private static AgentManager instance;
    private final List<AgentCLI> agents = new ArrayList<>();
    private String activeAgentId = "antigravity";

    private AgentManager(Context context) {
        agents.add(new AntigravityAgent(context));
        agents.add(new GeminiAgent(context));
        
        activeAgentId = context.getSharedPreferences("ToggleTalkPrefs", Context.MODE_PRIVATE)
                .getString("active_agent_cli", "antigravity");
    }

    public static synchronized AgentManager getInstance(Context context) {
        if (instance == null) {
            instance = new AgentManager(context.getApplicationContext());
        }
        return instance;
    }

    public List<AgentCLI> getAgents() {
        return agents;
    }

    public AgentCLI getActiveAgent() {
        for (AgentCLI agent : agents) {
            if (agent.getId().equals(activeAgentId)) {
                return agent;
            }
        }
        return agents.get(0); // Fallback to first agent
    }

    public void setActiveAgent(Context context, String agentId) {
        this.activeAgentId = agentId;
        context.getSharedPreferences("ToggleTalkPrefs", Context.MODE_PRIVATE)
                .edit()
                .putString("active_agent_cli", agentId)
                .apply();
    }
}
