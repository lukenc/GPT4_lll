package com.wmsay.gpt4_lll.component;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import git4idea.GitCommit;
import git4idea.config.GitConfigUtil;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GitCommitFetcher {
    public static List<String> fetchCommits(Project project, String authorsInput, boolean useCurrentUser,
                                            LocalDate fromDate, LocalDate toDate) {
        List<String> commitMessages = new ArrayList<>();

        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
            try {
                GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);
                GitRepository repository = repositoryManager.getRepositories().stream().findFirst().orElse(null);
                if (repository == null) {
                    throw new IllegalStateException("No Git repository found in project");
                }

                String gitUserName = GitConfigUtil.getValue(project, repository.getRoot(), "user.name");
                String authorFilter = authorsInput;
                if (useCurrentUser) {
                    authorFilter = gitUserName;
                }

                List<String> parameters = new ArrayList<>();
                parameters.add("--author=" + authorFilter);
                if (fromDate != null) {
                    parameters.add("--since=" + fromDate.toString());
                }
                if (toDate != null) {
                    parameters.add("--until=" + toDate.toString());
                }

                List<GitCommit> commits = GitHistoryUtils.history(project, repository.getRoot(),
                        parameters.toArray(new String[0]));

                commitMessages.addAll(commits.stream()
                        .map(commit -> commit.getId().asString() + " - " + commit.getSubject())
                        .collect(Collectors.toList()));

            } catch (Exception ex) {
                Messages.showErrorDialog(project, ex.getMessage(), "Error Fetching Commits");
            }
        }, "Fetching Git Commits", true, project);

        return commitMessages;
    }
}
