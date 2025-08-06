package com.wmsay.gpt4_lll;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@State(name = "Gpt4lllProjectSettings", storages = @Storage("gpt4lllProjectSettings.xml"))
public class ProjectSettings implements PersistentStateComponent<ProjectSettings.State> {

    public static class State {
        public String lastProvider = "";
        public String lastModelDisplayName = "";
    }

    private State state = new State();

    public static ProjectSettings getInstance(Project project) {
        return project.getService(ProjectSettings.class);
    }

    public String getLastProvider() {
        return state.lastProvider;
    }

    public void setLastProvider(String lastProvider) {
        state.lastProvider = lastProvider;
    }

    public String getLastModelDisplayName() {
        return state.lastModelDisplayName;
    }

    public void setLastModelDisplayName(String lastModelDisplayName) {
        state.lastModelDisplayName = lastModelDisplayName;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }
} 