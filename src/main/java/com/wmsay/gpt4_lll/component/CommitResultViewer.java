package com.wmsay.gpt4_lll.component;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.wmsay.gpt4_lll.GenerateAction;
import groovy.util.logging.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Slf4j
public class CommitResultViewer {

    private static final Logger log = LoggerFactory.getLogger(CommitResultViewer.class);

    public static void showResults(Project project, List<String> commitMessages) {
        StringBuilder builder = new StringBuilder();
        for (String message : commitMessages) {
            builder.append(message).append("\n");
        }
        log.info("Commit messages: {}", commitMessages);
//        GenerateAction.chat(builder.toString(), project, false, true, "");

    }

    public static void showError(Project project, String errorMessage) {
        Messages.showErrorDialog(project, "Error: " + errorMessage, "Git Commit Retrieval Error");
    }
}
