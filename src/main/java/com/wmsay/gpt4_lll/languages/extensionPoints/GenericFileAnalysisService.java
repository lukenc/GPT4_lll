package com.wmsay.gpt4_lll.languages.extensionPoints;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.wmsay.gpt4_lll.languages.FileAnalysisService;
import com.wmsay.gpt4_lll.model.Message;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

// 2. 通用实现
@Service
public final class GenericFileAnalysisService implements FileAnalysisService {

    @Override
    public boolean canHandle(VirtualFile file) {
        return true; // 可以处理任何文件
    }

    @Override
    public List<Message> analyze(Project project, PsiFile psiFile) {
        // 通用的文件分析逻辑
        String fileName = psiFile.getName();
        String content = psiFile.getText();

        // 使用通用 PSI API
        psiFile.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                super.visitElement(element);
                // 通用元素处理
            }
        });
        return new ArrayList<>();
    }

    @Override
    public int getPriority() {
        return 0; // 最低优先级，作为后备
    }

    @Override
    public List<Message> analyzeInfoToMessage(Project project, Editor editor) {
        return List.of();
    }

    @Override
    public List<Message> analyzeCurrentEditorInfoToMessage(Project project, Editor editor) {
        return List.of();
    }
}
