package com.wmsay.gpt4_lll.fc.core;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * AgentSession 的生命周期状态枚举。
 * <p>
 * 包含合法状态转换表，定义了每个状态可以转换到的目标状态集合。
 */
public enum SessionState {
    CREATED, RUNNING, PAUSED, COMPLETED, ERROR, DESTROYED;

    /** 合法状态转换表 */
    private static final Map<SessionState, Set<SessionState>> VALID_TRANSITIONS;

    static {
        VALID_TRANSITIONS = new EnumMap<>(SessionState.class);
        VALID_TRANSITIONS.put(CREATED, EnumSet.of(RUNNING, DESTROYED));
        VALID_TRANSITIONS.put(RUNNING, EnumSet.of(PAUSED, COMPLETED, ERROR));
        VALID_TRANSITIONS.put(PAUSED, EnumSet.of(RUNNING));
        VALID_TRANSITIONS.put(COMPLETED, EnumSet.of(RUNNING, DESTROYED));
        VALID_TRANSITIONS.put(ERROR, EnumSet.of(RUNNING, DESTROYED));
        VALID_TRANSITIONS.put(DESTROYED, EnumSet.noneOf(SessionState.class));
    }

    /**
     * 检查从当前状态到目标状态的转换是否合法。
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return 如果转换合法返回 true
     */
    public static boolean isValidTransition(SessionState from, SessionState to) {
        Set<SessionState> allowed = VALID_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }
}
