package com.wmsay.gpt4_lll.languages;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.wmsay.gpt4_lll.languages.extensionPoints.GenericFileAnalysisService;
import com.wmsay.gpt4_lll.model.Message;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public final class FileAnalysisManager {
    private final List<FileAnalysisService> services;
    private static final Object lock = new Object();
    private static volatile List<FileAnalysisService> cachedServices = null;


    /**
     * FileAnalysisManager的构造方法。初始化服务列表。
     */
    public FileAnalysisManager() {
        // 获取或创建服务列表并赋值给services成员变量
        this.services = getOrCreateServices();
    }


    public void analyzeFile(Project project, VirtualFile virtualFile) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
        if (psiFile == null) {
            return;
        }

        // 找到合适的服务来处理文件
        FileAnalysisService service = findBestService(virtualFile);
        if (service != null) {
            service.analyze(project, psiFile);
        }
    }

    public List<Message> analyzeFile(Project project, Editor editor) {
        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
        FileAnalysisService service = findBestService(virtualFile);
        if (service != null) {
            return service.analyzeInfoToMessage(project, editor);
        }
        return List.of();
    }

    public List<Message> analyzeCurrentFile(Project project, Editor editor) {
        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());

        FileAnalysisService service = findBestService(virtualFile);
        if (service != null) {
            return service.analyzeCurrentEditorInfoToMessage(project, editor);
        }
        return List.of();
    }


    private FileAnalysisService findBestService(VirtualFile file) {
        return services.stream()
                .filter(service -> service.canHandle(file))
                .max(Comparator.comparingInt(FileAnalysisService::getPriority))
                .orElse(null);
    }

    private static List<FileAnalysisService> getOrCreateServices() {
        if (cachedServices == null) {
            synchronized (lock) {
                if (cachedServices == null) {
                    cachedServices = createServices();
                }
            }
        }
        return cachedServices;
    }

    private static List<FileAnalysisService> createServices() {
        List<FileAnalysisService> services = new ArrayList<>();

        // 总是添加通用服务（最低优先级，作为后备）
        services.add(new GenericFileAnalysisService());

        // 尝试从 IntelliJ 服务容器获取 Java 分析服务
        // JavaFileAnalysisService 在 java-support.xml 中注册，只有 Java 插件可用时才会存在
        tryLoadServiceFromContainer(services, "com.wmsay.gpt4_lll.languages.extensionPoints.JavaFileAnalysisService", "Java");

        // 未来可以添加其他语言支持，例如：
        // tryLoadServiceFromContainer(services, "com.wmsay.gpt4_lll.languages.extensionPoints.KotlinFileAnalysisService", "Kotlin");

        return services;
    }

    /**
     * 尝试从 IntelliJ 服务容器加载指定的服务。
     * 服务通过可选依赖的 XML 文件注册，只有对应插件可用时才会存在。
     *
     * @param services         服务列表，成功加载的服务将添加到此列表
     * @param serviceClassName 服务类的完全限定名
     * @param serviceName      服务名称，用于日志输出
     */
    private static void tryLoadServiceFromContainer(List<FileAnalysisService> services, String serviceClassName, String serviceName) {
        try {
            // 通过类名获取服务类
            Class<?> serviceClass = Class.forName(serviceClassName);
            // 从 IntelliJ 服务容器获取已注册的服务实例
            Object service = ApplicationManager.getApplication().getService(serviceClass);
            if (service instanceof FileAnalysisService fileAnalysisService) {
                services.add(fileAnalysisService);
                System.out.println("Loaded " + serviceName + " analysis service from container");
            }
        } catch (ClassNotFoundException e) {
            // 服务类不存在（对应插件未安装），这是正常情况
            System.out.println(serviceName + " analysis service not available (plugin not installed)");
        } catch (Exception e) {
            // 其他加载错误
            System.out.println("Failed to load " + serviceName + " service: " + e.getMessage());
        }
    }


}