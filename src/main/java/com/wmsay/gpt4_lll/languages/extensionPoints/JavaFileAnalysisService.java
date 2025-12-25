package com.wmsay.gpt4_lll.languages.extensionPoints;

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
            // 获取Document对象，用于计算行号
            PsiFile psiFile = psiClass.getContainingFile();
            com.intellij.openapi.editor.Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
            // 创建一个新的Message对象
            Message classMessage = new Message();
            classMessage.setRole("user");
            classMessage.setName("owner");
            // 创建一个StringBuilder对象，用于存储类的信息
            StringBuilder classInfoSb = new StringBuilder();

            // ========== 类基本信息 ==========
            classInfoSb.append("已知类: ").append(psiClass.getName());

            // 添加类的起止行号
            if (document != null) {
                int classStartLine = document.getLineNumber(psiClass.getTextRange().getStartOffset()) + 1;
                int classEndLine = document.getLineNumber(psiClass.getTextRange().getEndOffset()) + 1;
                classInfoSb.append(" (行号: ").append(classStartLine).append("-").append(classEndLine).append(")");
            }
            classInfoSb.append("\n");

            // 添加类的完整限定名
            String qualifiedName = psiClass.getQualifiedName();
            if (qualifiedName != null) {
                classInfoSb.append("完整类名: ").append(qualifiedName).append("\n");
            }

            // 添加类的类型（CLASS/INTERFACE/ENUM/ANNOTATION/RECORD）
            classInfoSb.append("类型: ").append(getClassKindName(psiClass)).append("\n");

            // 添加类的修饰符
            PsiModifierList modifierList = psiClass.getModifierList();
            if (modifierList != null && !modifierList.getText().isEmpty()) {
                classInfoSb.append("修饰符: ").append(modifierList.getText()).append("\n");
            }

            // 添加泛型类型参数
            PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
            if (typeParameters.length > 0) {
                classInfoSb.append("泛型参数: <");
                for (int i = 0; i < typeParameters.length; i++) {
                    if (i > 0) classInfoSb.append(", ");
                    classInfoSb.append(typeParameters[i].getName());
                    // 添加泛型边界
                    PsiClassType[] extendsList = typeParameters[i].getExtendsList().getReferencedTypes();
                    if (extendsList.length > 0) {
                        classInfoSb.append(" extends ");
                        for (int j = 0; j < extendsList.length; j++) {
                            if (j > 0) classInfoSb.append(" & ");
                            classInfoSb.append(extendsList[j].getPresentableText());
                        }
                    }
                }
                classInfoSb.append(">\n");
            }

            // 添加类的文档注释
            PsiDocComment classDocComment = psiClass.getDocComment();
            if (classDocComment != null) {
                String classDocContent = extractContentFromDocComment(classDocComment);
                if (!classDocContent.isEmpty()) {
                    classInfoSb.append("类描述: ").append(classDocContent).append("\n");
                }
            }

            // 添加类注解信息
            PsiAnnotation[] classAnnotations = psiClass.getAnnotations();
            if (classAnnotations.length > 0) {
                classInfoSb.append("类注解: ");
                for (int i = 0; i < classAnnotations.length; i++) {
                    if (i > 0) classInfoSb.append(", ");
                    classInfoSb.append(classAnnotations[i].getQualifiedName());
                }
                classInfoSb.append("\n");
            }

            // ========== 继承关系 ==========
            // 添加父类信息
            PsiClass superClass = psiClass.getSuperClass();
            if (superClass != null && !"Object".equals(superClass.getName())) {
                classInfoSb.append("父类: ").append(superClass.getName());
                String superQualifiedName = superClass.getQualifiedName();
                if (superQualifiedName != null && !isJdkClass(superQualifiedName)) {
                    // 项目内自定义类，追加更多信息
                    classInfoSb.append(" (").append(superQualifiedName).append(")");
                    if (superClass.getDocComment() != null) {
                        String superDocContent = extractContentFromDocComment(superClass.getDocComment());
                        if (!superDocContent.isEmpty()) {
                            classInfoSb.append("\n  父类说明: ").append(superDocContent);
                        }
                    }
                }
                classInfoSb.append("\n");
            }

            // 添加实现的接口信息
            PsiClassType[] implementsTypes = psiClass.getImplementsListTypes();
            if (implementsTypes.length > 0) {
                classInfoSb.append("实现的接口:\n");
                for (PsiClassType interfaceType : implementsTypes) {
                    classInfoSb.append("  - ").append(interfaceType.getPresentableText());
                    PsiClass interfaceClass = interfaceType.resolve();
                    if (interfaceClass != null) {
                        String interfaceQualifiedName = interfaceClass.getQualifiedName();
                        if (interfaceQualifiedName != null && !isJdkClass(interfaceQualifiedName)) {
                            // 项目内接口，添加接口说明
                            if (interfaceClass.getDocComment() != null) {
                                String interfaceDocContent = extractContentFromDocComment(interfaceClass.getDocComment());
                                if (!interfaceDocContent.isEmpty()) {
                                    classInfoSb.append(" (说明: ").append(interfaceDocContent).append(")");
                                }
                            }
                        }
                    }
                    classInfoSb.append("\n");
                }
            }

            // 添加继承的接口（通过父类继承的）
            PsiClass[] allInterfaces = psiClass.getInterfaces();
            if (allInterfaces.length > implementsTypes.length) {
                classInfoSb.append("继承的接口(通过父类): ");
                boolean first = true;
                Set<String> directInterfaceNames = new HashSet<>();
                for (PsiClassType type : implementsTypes) {
                    directInterfaceNames.add(type.getCanonicalText());
                }
                for (PsiClass iface : allInterfaces) {
                    if (iface.getQualifiedName() != null && !directInterfaceNames.contains(iface.getQualifiedName())) {
                        if (!first) classInfoSb.append(", ");
                        classInfoSb.append(iface.getName());
                        first = false;
                    }
                }
                classInfoSb.append("\n");
            }

            // ========== 内部类信息 ==========
            PsiClass[] innerClasses = psiClass.getInnerClasses();
            if (innerClasses.length > 0) {
                classInfoSb.append("内部类:\n");
                for (PsiClass innerClass : innerClasses) {
                    classInfoSb.append("  - ").append(getClassKindName(innerClass)).append(" ")
                            .append(innerClass.getName());
                    if (document != null) {
                        int innerStartLine = document.getLineNumber(innerClass.getTextRange().getStartOffset()) + 1;
                        int innerEndLine = document.getLineNumber(innerClass.getTextRange().getEndOffset()) + 1;
                        classInfoSb.append(" (行号: ").append(innerStartLine).append("-").append(innerEndLine).append(")");
                    }
                    classInfoSb.append("\n");
                }
            }

            // ========== 字段信息 ==========
            if (fields.length > 0) {
                classInfoSb.append("\n包含以下属性：\n");
                for (PsiField field : fields) {
                    // 添加字段修饰符
                    PsiModifierList fieldModifiers = field.getModifierList();
                    if (fieldModifiers != null && !fieldModifiers.getText().isEmpty()) {
                        classInfoSb.append(fieldModifiers.getText()).append(" ");
                    }
                    // 添加字段类型和字段名
                    classInfoSb.append(field.getType().getPresentableText()).append(" ").append(field.getName());
                    // 添加字段的起止行号信息
                    if (document != null) {
                        int startLine = document.getLineNumber(field.getTextRange().getStartOffset()) + 1;
                        int endLine = document.getLineNumber(field.getTextRange().getEndOffset()) + 1;
                        classInfoSb.append(" (行号: ").append(startLine).append("-").append(endLine).append(")");
                    }
                    // 添加字段初始值（如果是常量）
                    PsiExpression initializer = field.getInitializer();
                    if (initializer != null && field.hasModifierProperty(PsiModifier.FINAL)) {
                        String initText = initializer.getText();
                        if (initText.length() <= 50) { // 限制长度，避免过长
                            classInfoSb.append(" = ").append(initText);
                        }
                    }
                    // 如果字段有备注，则把备注也append进去
                    PsiDocComment docComment = field.getDocComment();
                    if (docComment != null) {
                        String commentText = extractContentFromDocComment(docComment);
                        classInfoSb.append("，描述为: ").append(commentText);
                    } else {
                        PsiAnnotation[] annotations = field.getAnnotations();
                        // 遍历注解数组
                        for (PsiAnnotation annotation : annotations) {
                            PsiAnnotationMemberValue value = annotation.findAttributeValue("description");
                            if (value != null) {
                                classInfoSb.append("，描述为: ").append(value.getText());
                                break;
                            }
                        }
                    }
                    classInfoSb.append("\n");
                }
            }

            // ========== 方法信息 ==========
            if (methods.length > 0) {
                Set<String> getterAndSetterNames = new HashSet<>();
                for (PsiField field : psiClass.getFields()) {
                    String fieldName = field.getName();
                    String capitalizedFieldName = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                    getterAndSetterNames.add("get" + capitalizedFieldName);
                    getterAndSetterNames.add("set" + capitalizedFieldName);
                    getterAndSetterNames.add("is" + capitalizedFieldName); // boolean getter
                }

                List<PsiMethod> usefulMethod = Arrays.stream(psiClass.getMethods())
                        .filter(psiMethod -> !getterAndSetterNames.contains(psiMethod.getName()))
                        .toList();
                if (CollectionUtils.isNotEmpty(usefulMethod)) {
                    classInfoSb.append("\n包含如下方法：\n");
                    for (PsiMethod method : usefulMethod) {
                        // 方法修饰符
                        PsiModifierList methodModifiers = method.getModifierList();
                        if (!methodModifiers.getText().isEmpty()) {
                            classInfoSb.append(methodModifiers.getText()).append(" ");
                        }
                        // 方法返回类型
                        PsiType returnType = method.getReturnType();
                        if (returnType != null) {
                            classInfoSb.append(returnType.getPresentableText()).append(" ");
                        }
                        // 方法名
                        classInfoSb.append(method.getName());
                        // 方法参数
                        classInfoSb.append("(");
                        PsiParameter[] parameters = method.getParameterList().getParameters();
                        for (int i = 0; i < parameters.length; i++) {
                            if (i > 0) classInfoSb.append(", ");
                            classInfoSb.append(parameters[i].getType().getPresentableText())
                                    .append(" ").append(parameters[i].getName());
                        }
                        classInfoSb.append(")");
                        // 添加方法的起止行号信息
                        if (document != null) {
                            int startLine = document.getLineNumber(method.getTextRange().getStartOffset()) + 1;
                            int endLine = document.getLineNumber(method.getTextRange().getEndOffset()) + 1;
                            classInfoSb.append(" (行号: ").append(startLine).append("-").append(endLine).append(")");
                        }
                        // 抛出的异常
                        PsiClassType[] throwsList = method.getThrowsList().getReferencedTypes();
                        if (throwsList.length > 0) {
                            classInfoSb.append(" throws ");
                            for (int i = 0; i < throwsList.length; i++) {
                                if (i > 0) classInfoSb.append(", ");
                                classInfoSb.append(throwsList[i].getPresentableText());
                            }
                        }
                        // 输出方法的注释
                        PsiDocComment docComment = method.getDocComment();
                        if (docComment != null) {
                            String extractedContent = extractContentFromDocComment(docComment);
                            if (!extractedContent.isEmpty()) {
                                classInfoSb.append("\n    用途描述: ").append(extractedContent);
                            }
                        }
                        classInfoSb.append("\n");
                    }
                }
            }

            classInfoSb.append("\n上面这个类提供给你，有助于你更了解情况。");
            // 将StringBuilder对象的内容设置为Message对象的内容
            classMessage.setContent(classInfoSb.toString());
            // 返回转换后的Message对象
            return classMessage;
        }
        // 如果PsiClass对象不在源代码中，则返回null
        return null;
    }

    /**
     * 判断类是否为JDK标准库类
     *
     * @param qualifiedName 类的完整限定名
     * @return 如果是JDK类返回true，否则返回false
     */
    private static boolean isJdkClass(String qualifiedName) {
        if (qualifiedName == null) return false;
        return qualifiedName.startsWith("java.") ||
                qualifiedName.startsWith("javax.") ||
                qualifiedName.startsWith("com.sun.") ||
                qualifiedName.startsWith("sun.") ||
                qualifiedName.startsWith("jdk.") ||
                qualifiedName.startsWith("org.w3c.") ||
                qualifiedName.startsWith("org.xml.");
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
        if (psiClass == null) {
            return "UNKNOWN";
        }
        if (psiClass.isAnnotationType()) {
            return "ANNOTATION";
        }
        if (psiClass.isEnum()) {
            return "ENUM";
        }
        if (psiClass.isInterface()) {
            return "INTERFACE";
        }
        if (psiClass.isRecord()) {
            return "RECORD";
        }
        return "CLASS";
    }


}