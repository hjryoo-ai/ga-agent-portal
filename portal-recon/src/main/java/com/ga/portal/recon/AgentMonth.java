package com.ga.portal.recon;

import java.util.Objects;

/** 대사의 단위 — 설계사 × 귀속월. */
public record AgentMonth(String agentId, String ym) implements Comparable<AgentMonth> {

    public AgentMonth {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(ym, "ym");
    }

    @Override
    public int compareTo(AgentMonth o) {
        int c = agentId.compareTo(o.agentId);
        return c != 0 ? c : ym.compareTo(o.ym);
    }
}
