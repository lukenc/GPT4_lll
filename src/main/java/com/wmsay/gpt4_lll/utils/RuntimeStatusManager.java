package com.wmsay.gpt4_lll.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.wmsay.gpt4_lll.model.AgentPhase;
import com.wmsay.gpt4_lll.model.AgentStatusContext;
import com.wmsay.gpt4_lll.model.RuntimeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages per-project RuntimeStatus and notifies registered listeners on state changes.
 * Status and listeners are stored in project UserData, consistent with existing CommonUtil patterns.
 */
public class RuntimeStatusManager {

    private static final Logger log = LoggerFactory.getLogger(RuntimeStatusManager.class);

    public static final Key<RuntimeStatus> GPT_4_LLL_RUNTIME_STATUS =
            Key.create("GPT4lllRuntimeStatus");

    public static final Key<List<StatusListener>> GPT_4_LLL_STATUS_LISTENERS =
            Key.create("GPT4lllStatusListeners");

    /**
     * Listener interface for runtime status changes.
     */
    public interface StatusListener {
        void onStatusChanged(RuntimeStatus oldStatus, RuntimeStatus newStatus);
    }

    /**
     * Returns the current RuntimeStatus for the given project, defaulting to IDLE if unset.
     */
    public static RuntimeStatus getStatus(Project project) {
        RuntimeStatus status = project.getUserData(GPT_4_LLL_RUNTIME_STATUS);
        return status != null ? status : RuntimeStatus.IDLE;
    }

    /**
     * Sets the RuntimeStatus for the given project and notifies all registered listeners on EDT.
     * Each listener is invoked independently — an exception in one listener does not prevent others
     * from being notified.
     */
    public static void setStatus(Project project, RuntimeStatus status) {
        RuntimeStatus oldStatus = getStatus(project);
        project.putUserData(GPT_4_LLL_RUNTIME_STATUS, status);

        if (oldStatus == status) {
            return;
        }

        List<StatusListener> listeners = project.getUserData(GPT_4_LLL_STATUS_LISTENERS);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }

        // Snapshot the listener list to avoid concurrent modification during notification
        List<StatusListener> snapshot = new ArrayList<>(listeners);

        Runnable notifyAll = () -> {
            for (StatusListener listener : snapshot) {
                try {
                    listener.onStatusChanged(oldStatus, status);
                } catch (Exception e) {
                    log.error("Exception in StatusListener.onStatusChanged", e);
                }
            }
        };

        if (ApplicationManager.getApplication().isDispatchThread()) {
            notifyAll.run();
        } else {
            ApplicationManager.getApplication().invokeLater(notifyAll);
        }
    }

    /**
     * Registers a StatusListener for the given project.
     */
    public static void addListener(Project project, StatusListener listener) {
        List<StatusListener> listeners = project.getUserData(GPT_4_LLL_STATUS_LISTENERS);
        if (listeners == null) {
            listeners = new CopyOnWriteArrayList<>();
            project.putUserData(GPT_4_LLL_STATUS_LISTENERS, listeners);
        }
        listeners.add(listener);
    }

    /**
     * Removes a previously registered StatusListener for the given project.
     */
    public static void removeListener(Project project, StatusListener listener) {
        List<StatusListener> listeners = project.getUserData(GPT_4_LLL_STATUS_LISTENERS);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    // ========== AgentPhase extension ==========

    /**
     * Agent 阶段变化监听器。
     */
    public interface AgentPhaseListener {
        void onPhaseChanged(AgentStatusContext oldContext, AgentStatusContext newContext);
    }

    public static final Key<AgentStatusContext> GPT_4_LLL_AGENT_PHASE =
            Key.create("GPT4lllAgentPhase");

    public static final Key<List<AgentPhaseListener>> GPT_4_LLL_PHASE_LISTENERS =
            Key.create("GPT4lllPhaseListeners");

    /**
     * Returns the current AgentStatusContext for the given project, defaulting to IDLE_DEFAULT if unset.
     */
    public static AgentStatusContext getAgentPhase(Project project) {
        AgentStatusContext ctx = project.getUserData(GPT_4_LLL_AGENT_PHASE);
        return ctx != null ? ctx : AgentStatusContext.IDLE_DEFAULT;
    }

    /**
     * Sets the AgentPhase for the given project, syncs RuntimeStatus, and notifies all registered
     * AgentPhaseListeners when phase or detail changes.
     *
     * Phase-to-RuntimeStatus mapping:
     *   RUNNING   → RUNNING
     *   COMPLETED → COMPLETED
     *   ERROR     → ERROR
     *   IDLE      → IDLE
     *   STOPPED   → IDLE
     */
    public static void setAgentPhase(Project project, AgentStatusContext newContext) {
        AgentStatusContext oldContext = getAgentPhase(project);
        project.putUserData(GPT_4_LLL_AGENT_PHASE, newContext);

        // Sync RuntimeStatus for backward compatibility
        RuntimeStatus mappedStatus = mapPhaseToStatus(newContext.getPhase());
        setStatus(project, mappedStatus);

        // Only notify when phase OR detail changes
        if (oldContext.getPhase() == newContext.getPhase()
                && Objects.equals(oldContext.getDetail(), newContext.getDetail())) {
            return;
        }

        notifyPhaseListeners(project, oldContext, newContext);
    }

    /**
     * Registers an AgentPhaseListener for the given project.
     */
    public static void addPhaseListener(Project project, AgentPhaseListener listener) {
        List<AgentPhaseListener> listeners = project.getUserData(GPT_4_LLL_PHASE_LISTENERS);
        if (listeners == null) {
            listeners = new CopyOnWriteArrayList<>();
            project.putUserData(GPT_4_LLL_PHASE_LISTENERS, listeners);
        }
        listeners.add(listener);
    }

    /**
     * Removes a previously registered AgentPhaseListener for the given project.
     */
    public static void removePhaseListener(Project project, AgentPhaseListener listener) {
        List<AgentPhaseListener> listeners = project.getUserData(GPT_4_LLL_PHASE_LISTENERS);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    private static RuntimeStatus mapPhaseToStatus(AgentPhase phase) {
        return switch (phase) {
            case RUNNING -> RuntimeStatus.RUNNING;
            case COMPLETED -> RuntimeStatus.COMPLETED;
            case ERROR -> RuntimeStatus.ERROR;
            case IDLE, STOPPED -> RuntimeStatus.IDLE;
        };
    }

    private static void notifyPhaseListeners(Project project,
                                              AgentStatusContext oldContext,
                                              AgentStatusContext newContext) {
        List<AgentPhaseListener> listeners = project.getUserData(GPT_4_LLL_PHASE_LISTENERS);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }

        List<AgentPhaseListener> snapshot = new ArrayList<>(listeners);

        Runnable notifyAll = () -> {
            for (AgentPhaseListener listener : snapshot) {
                try {
                    listener.onPhaseChanged(oldContext, newContext);
                } catch (Exception e) {
                    log.error("Exception in AgentPhaseListener.onPhaseChanged", e);
                }
            }
        };

        if (ApplicationManager.getApplication().isDispatchThread()) {
            notifyAll.run();
        } else {
            ApplicationManager.getApplication().invokeLater(notifyAll);
        }
    }
}
