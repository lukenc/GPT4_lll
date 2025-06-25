package com.wmsay.gpt4_lll;


import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.wmsay.gpt4_lll.component.VCSAuthorSelectionDialog;
import org.jetbrains.annotations.NotNull;

public class WorkReportAction extends AnAction {



    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VCSAuthorSelectionDialog dialog = new VCSAuthorSelectionDialog(project);
        dialog.show();
    }
}
