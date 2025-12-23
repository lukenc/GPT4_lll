package com.wmsay.gpt4_lll.languages;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.wmsay.gpt4_lll.model.Message;

import java.util.List;

/**
 * 文件分析服务接口，定义了文件分析的相关方法。
 */
public interface FileAnalysisService {

    /**
     * 判断服务是否可以处理指定的虚拟文件。
     *
     * @param file 虚拟文件对象
     * @return 如果服务可以处理该文件，返回true；否则返回false。
     */
    boolean canHandle(VirtualFile file);

    /**
     * 分析指定的项目和Psi文件，并返回分析结果。
     *
     * @param project 项目对象
     * @param psiFile Psi文件对象
     * @return 分析结果，以Message对象的列表形式返回。
     */
    List<Message> analyze(Project project, PsiFile psiFile);


    /**
     * 获取此服务的处理优先级。
     *
     * @return 服务的处理优先级，用于决定在并发处理时的顺序。
     */
    int getPriority();

    /**
     * 分析信息并转换为消息列表的方法。
     *
     * @param project 项目对象，包含项目相关的上下文信息。
     * @param editor  编辑器对象，可能包含当前编辑器中的文件或文本信息。
     * @return 消息列表，每个消息包含分析的结果信息。
     */
    List<Message> analyzeInfoToMessage(Project project, Editor editor);

    /**
     * 分析当前编辑器信息并转换为消息列表的方法。
     *
     * @param project 项目对象，包含项目相关的上下文信息。
     * @param editor  编辑器对象，通常包含当前打开或选中文件的信息。
     * @return 消息列表，包含对当前编辑器信息的分析结果。
     */
    List<Message> analyzeCurrentEditorInfoToMessage(Project project, Editor editor);


}

