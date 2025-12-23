package com.wmsay.gpt4_lll.languages.extensionPoints;

import com.intellij.lang.jvm.JvmClassKind;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.wmsay.gpt4_lll.languages.FileAnalysisService;
import com.wmsay.gpt4_lll.model.Message;
import org.apache.commons.collections.CollectionUtils;

import java.util.*;

@Service
public final class JavaFileAnalysisService implements FileAnalysisService {

    @Override
    public boolean canHandle(VirtualFile file) {
        return "java".equals(file.getExtension());
    }

    @Override
    public List<Message> analyze(Project project, PsiFile psiFile) {
        return List.of();
    }

    @Override
    public int getPriority() {
        return 10; // 高优先级
    }

    /**
     * 分析编辑器中选中的代码信息，并将其转换为Message对象列表
     * <p>
     * 该方法主要用于获取当前编辑器中用户选中的代码片段，解析其中涉及的类，
     * 并将这些类的信息封装成Message对象返回。主要用于代码理解、上下文分析等场景。
     *
     * @param project 当前项目对象，用于获取PsiFile和相关管理器
     * @param editor  当前编辑器对象，用于获取选中区域和文档内容
     * @return 包含选中代码涉及类信息的Message对象列表
     */
    @Override
    public List<Message> analyzeInfoToMessage(Project project, Editor editor) {
        List<Message> res = new ArrayList<>();

        // 获取当前编辑器对应的PsiFile
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        // 获取选中区域的起始和结束偏移量
        int selectionStart = editor.getSelectionModel().getSelectionStart();
        int selectionEnd = editor.getSelectionModel().getSelectionEnd();

        // 收集选中范围内的所有PsiElement
        PsiElement[] selectedElements = PsiTreeUtil.collectElements(psiFile,
                element -> element.getTextRange().getStartOffset() >= selectionStart &&
                        element.getTextRange().getEndOffset() <= selectionEnd);

        // 存储选中代码中涉及的类
        Set<PsiClass> involvedClasses = new HashSet<>();
        for (PsiElement element : selectedElements) {
            // 如果元素是引用，则尝试解析其指向的类
            if (element instanceof PsiReference psireference) {
                PsiElement resolvedElement = psireference.resolve();
                if (resolvedElement instanceof PsiClass psiClass) {
                    involvedClasses.add(psiClass);
                }
            }
        }

        // 遍历涉及的类，过滤出属于当前项目的类并转换为Message对象
        for (PsiClass psiClass : involvedClasses) {
            VirtualFile classFile = psiClass.getContainingFile().getVirtualFile();
            // 检查类是否属于当前项目的源码内容
            if (ProjectRootManager.getInstance(project).getFileIndex().isInSourceContent(classFile)) {
                Message message = processClass2Message(psiClass, project);
                if (message != null) {
                    res.add(message);
                }
            }
        }
        return res;
    }


    /**
     * 分析当前编辑器中选中的代码信息，并将其转换为Message对象列表
     * <p>
     * 该方法主要用于获取当前编辑器中用户选中的代码片段，定位到该片段所属的类，
     * 并将该类的信息封装成Message对象返回。主要用于代码理解、上下文分析等场景。
     *
     * @param project 当前项目对象，用于获取PsiFile等信息
     * @param editor  当前编辑器对象，用于获取选中区域和文档内容
     * @return 包含选中代码所属类信息的Message对象列表
     */
    @Override
    public List<Message> analyzeCurrentEditorInfoToMessage(Project project, Editor editor) {
        // 获取当前编辑器对应的PsiFile对象
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        List<Message> res = new ArrayList<>();
        if (psiFile != null) {
            // 获取选中区域的起始和结束偏移量
            int startOffset = editor.getSelectionModel().getSelectionStart();
            int endOffset = editor.getSelectionModel().getSelectionEnd();
            // 跳过选中区域开头的空白字符，确保能正确获取到选中元素
            while (startOffset < endOffset && Character.isWhitespace(psiFile.getText().charAt(startOffset))) {
                startOffset++;
            }
            // 获取当前选中元素的PsiElement对象
            PsiElement selectedElement = psiFile.findElementAt(startOffset);
            // 通过PsiTreeUtil获取当前选中元素所在的PsiClass对象
            PsiClass containingClass = PsiTreeUtil.getParentOfType(selectedElement, PsiClass.class, true);
            if (containingClass != null) {
                // 将当前选中的PsiClass对象转换为Message对象
                Message currentClassMessage = processCurentClass2Message(containingClass);
                // 将转换后的Message对象添加到结果列表中
                res.add(currentClassMessage);
            }
        }
        return res;
    }


    /**
     * 将PsiClass对象转换为Message对象
     *
     * @param psiClass 要转换的PsiClass对象
     * @param project  当前的项目对象
     * @return 转换后的Message对象，如果PsiClass对象不在源代码中则返回null
     */
    private static Message processClass2Message(PsiClass psiClass, Project project) {
        // 获取PsiClass对象所在的虚拟文件
        VirtualFile virtualFile = psiClass.getContainingFile().getVirtualFile();
        // 获取项目文件索引
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        // 判断虚拟文件是否在源代码中
        if (fileIndex.isInSourceContent(virtualFile)) {
            // 获取PsiClass对象的所有字段
            PsiField[] fields = psiClass.getFields();
            PsiMethod[] methods = psiClass.getMethods();
            // 创建一个新的Message对象
            Message classMessage = new Message();
            classMessage.setRole("user");
            classMessage.setName("owner");
            // 创建一个StringBuilder对象，用于存储类的信息
            StringBuilder classInfoSb = new StringBuilder();
            // 添加类名和属性信息到StringBuilder对象中
            classInfoSb.append("已知").append(psiClass.getName()).append("类包含以下属性：");
            for (PsiField field : fields) {
                // 添加字段类型和字段名到StringBuilder对象中
                classInfoSb.append(field.getType().getPresentableText()).append(" ").append(field.getName());
                // 如果字段有备注，则把备注也append进去
                PsiDocComment docComment = field.getDocComment();
                if (docComment != null) {
                    String commentText = extractContentFromDocComment(docComment);
                    classInfoSb.append("，描述为:").append(commentText);
                } else {
                    PsiAnnotation[] annotations = field.getAnnotations();
                    // 遍历注解数组
                    for (PsiAnnotation annotation : annotations) {
                        PsiAnnotationMemberValue value = annotation.findAttributeValue("description");
                        if (value != null) {
                            classInfoSb.append("，描述为:").append(value.getText());
                            break;
                        }
                    }
                }
                //每个字段需要分隔开
                classInfoSb.append(" \n");
            }
            if (methods.length > 0) {
                Set<String> getterAndSetterNames = new HashSet<>();
                for (PsiField field : psiClass.getFields()) {
                    String fieldName = field.getName();
                    String capitalizedFieldName = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                    getterAndSetterNames.add("get" + capitalizedFieldName);
                    getterAndSetterNames.add("set" + capitalizedFieldName);
                }

                List<PsiMethod> usefulMethod = Arrays.stream(psiClass.getMethods()).filter(psiMethod -> !getterAndSetterNames.contains(psiMethod.getName())).toList();
                if (CollectionUtils.isNotEmpty(usefulMethod)) {
                    classInfoSb.append(",包含如下方法：");
                    for (PsiMethod method : psiClass.getMethods()) {
                        String methodName = method.getName();
                        classInfoSb.append("\n方法名: ").append(methodName);
                        // 输出方法的注释
                        PsiDocComment docComment = method.getDocComment();
                        if (docComment != null) {
                            String extractedContent = extractContentFromDocComment(docComment);
                            classInfoSb.append(",用途描述: ").append(extractedContent);
                        }
                        // 输出方法的入参
                        PsiParameter[] parameters = method.getParameterList().getParameters();
                        if (parameters.length > 0) {
                            classInfoSb.append(" ,调用参数:");
                            for (PsiParameter parameter : parameters) {
                                classInfoSb.append("  ").append(parameter.getType().getPresentableText()).append(" ").append(parameter.getName());
                            }
                        }
                        // 输出方法的出参
                        PsiType returnType = method.getReturnType();
                        if (returnType != null) {
                            classInfoSb.append(",返回类型: ").append(returnType.getPresentableText());
                        }
                    }
                }
            }
            classInfoSb.append("上面这个类提供给你，有助于你更了解情况。");
            // 将StringBuffer对象的内容设置为Message对象的内容
            classMessage.setContent(classInfoSb.toString());
            // 返回转换后的Message对象
            return classMessage;
        }
        // 如果PsiClass对象不在源代码中，则返回null
        return null;
    }


    /**
     * 从 PsiDocComment 中提取文档注释的内容部分，去除注释符号和 Javadoc 标签。
     *
     * @param docComment PsiDocComment 对象，表示一个文档注释块
     * @return 提取后的纯文本内容，不包含注释符号和 Javadoc 标签
     */
    //todo 完善这个方法，使其能处理更多类型的docComment
    private static String extractContentFromDocComment(PsiDocComment docComment) {
        // 获取完整的注释文本
        String fullText = docComment.getText();
        // 去除起始和结束的注释符号
        String content = fullText.replace("/**", "").replace("*/", "").trim();
        // 按行分割注释内容
        String[] lines = content.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            // 去除行首的星号和空格
            String trimmedLine = line.trim().replaceFirst("^\\*", "").trim();
            // 过滤掉 Javadoc 标签（以 @ 开头的行）
            if (!trimmedLine.startsWith("@")) {
                sb.append(trimmedLine).append(" ");
            }
        }
        // 返回处理后的文本内容
        return sb.toString().trim();
    }


    private static Message processCurentClass2Message(PsiClass psiClass) {
        // 获取PsiClass对象的所有字段
        PsiField[] fields = psiClass.getFields();
        PsiMethod[] methods = psiClass.getMethods();
        // 创建一个新的Message对象
        Message classMessage = new Message();
        classMessage.setRole("user");
        classMessage.setName("owner");
        // 创建一个StringBuffer对象，用于存储类的信息
        StringBuffer classInfoSb = new StringBuffer();
        if (fields.length > 0 || methods.length > 0) {
            classInfoSb.append("当前处理的代码所处的类：").append(psiClass.getName());

            // 获取所有可以得到的类信息
            classInfoSb.append("\n\n类信息：\n");

            // 获取并打印类的修饰符
            classInfoSb.append("修饰符: ").append(psiClass.getModifierList().getText()).append("\n");
            classInfoSb.append("类型：").append(getClassKindName(psiClass)).append("\n");

            // 获取并打印类的文档注释
            PsiDocComment docComment = psiClass.getDocComment();
            if (docComment != null) {
                String content = extractContentFromDocComment(docComment);
                classInfoSb.append("描述与用途:\n").append(content).append("\n");
            }

            // 获取并打印类的父类

            PsiClass superClass = psiClass.getSuperClass();
            if (superClass != null) {
                classInfoSb.append("父类: ").append(superClass.getName()).append("\n");
                // 如果父类是项目内的类，则classInfoSb追加对应的类信息
                if (!superClass.getQualifiedName().startsWith("java.") &&
                        !superClass.getQualifiedName().startsWith("javax.") &&
                        !superClass.getQualifiedName().startsWith("com.sun.") &&
                        !superClass.getQualifiedName().startsWith("sun.")) {
                    // 项目内自定义类，追加类信息
                    classInfoSb.append("  类型: ").append(getClassKindName(superClass)).append("\n");
                    if (superClass.getDocComment() != null) {
                        String docContent = extractContentFromDocComment(superClass.getDocComment());
                        if (docContent != null && !docContent.isEmpty()) {
                            classInfoSb.append("  父类说明: ").append(docContent).append("\n");
                        }
                    }
                }
            }


            // 获取并打印类实现的接口
            PsiClassType[] interfaces = psiClass.getImplementsListTypes();
            if (interfaces.length > 0) {
                classInfoSb.append("实现的接口:\n");
                for (PsiClassType interfaceType : interfaces) {
                    classInfoSb.append("  - ").append(interfaceType.getCanonicalText()).append("\n");
                }
            }

        }

        // 添加类名和属性信息到StringBuffer对象中
        if (fields != null && fields.length > 0) {
            classInfoSb.append("包含以下属性：");
            for (PsiField field : fields) {
                // 添加字段类型和字段名到StringBuffer对象中
                classInfoSb.append(field.getType().getPresentableText()).append(" ").append(field.getName());
                // 如果字段有备注，则把备注也append进去
                PsiDocComment docComment = field.getDocComment();
                if (docComment != null) {
                    String commentText = extractContentFromDocComment(docComment);
                    classInfoSb.append("，描述为:").append(commentText);
                } else {
                    PsiAnnotation[] annotations = field.getAnnotations();
                    // 遍历注解数组
                    for (PsiAnnotation annotation : annotations) {
                        PsiAnnotationMemberValue value = annotation.findAttributeValue("description");
                        if (value != null) {
                            classInfoSb.append("，描述为:").append(value.getText());
                            break;
                        }
                    }
                }
                //每个字段需要分隔开
                classInfoSb.append(" \n");
            }
        }
        if (methods != null && methods.length > 0) {
            Set<String> getterAndSetterNames = new HashSet<>();
            for (PsiField field : psiClass.getFields()) {
                String fieldName = field.getName();
                String capitalizedFieldName = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                getterAndSetterNames.add("get" + capitalizedFieldName);
                getterAndSetterNames.add("set" + capitalizedFieldName);
            }

            List<PsiMethod> usefulMethod = Arrays.stream(psiClass.getMethods()).filter(psiMethod -> !getterAndSetterNames.contains(psiMethod.getName())).toList();
            if (CollectionUtils.isNotEmpty(usefulMethod)) {
                classInfoSb.append(",包含如下方法：");
                for (PsiMethod method : psiClass.getMethods()) {
                    String methodName = method.getName();
                    classInfoSb.append("\n方法名: ").append(methodName);
                    // 输出方法的注释
                    PsiDocComment docComment = method.getDocComment();
                    if (docComment != null) {
                        String extractedContent = extractContentFromDocComment(docComment);
                        classInfoSb.append("，用途描述: ").append(extractedContent);
                    }
                    // 输出方法的入参
                    PsiParameter[] parameters = method.getParameterList().getParameters();
                    if (parameters.length > 0) {
                        classInfoSb.append("，调用参数:");
                        for (PsiParameter parameter : parameters) {
                            classInfoSb.append("  ").append(parameter.getType().getPresentableText()).append(" ").append(parameter.getName());
                        }
                    }
                    // 输出方法的出参
                    PsiType returnType = method.getReturnType();
                    if (returnType != null) {
                        classInfoSb.append("，返回类型: ").append(returnType.getPresentableText());
                    }
                }
            }
        }
        classInfoSb.append("。\n圈选的代码所在的类信息提供给你，有助于你更了解情况。");
        // 将StringBuffer对象的内容设置为Message对象的内容
        classMessage.setContent(classInfoSb.toString());
        // 返回转换后的Message对象
        return classMessage;
    }

    /**
     * 获取类的类型名称
     * <p>
     * 该方法用于获取给定 PsiClass 对象所表示的类的类型（如类、接口、枚举等），
     * 并以字符串形式返回其类型名称。如果无法确定类型，则返回 "UNKNOWN"。
     *
     * @param psiClass 要获取类型名称的 PsiClass 对象，可以为 null
     * @return 类型名称字符串，可能的值包括 "CLASS"、"INTERFACE"、"ENUM" 等，或者 "UNKNOWN"
     */
    private static String getClassKindName(PsiClass psiClass) {
        if (psiClass != null) { // 确保传入的 PsiClass 不为 null
            JvmClassKind classKind = psiClass.getClassKind();
            if (classKind != null) {
                return classKind.toString(); // 返回枚举值的字符串表示
            }
        }
        return "UNKNOWN"; // 如果无法确定类的类型，则返回 "UNKNOWN"
    }


}