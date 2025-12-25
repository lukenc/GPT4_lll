package com.wmsay.gpt4_lll;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.wmsay.gpt4_lll.model.CommentTranslation;
import com.wmsay.gpt4_lll.service.CommentTranslationService;
import com.wmsay.gpt4_lll.service.TranslationProgressCallback;
import com.wmsay.gpt4_lll.utils.CommentTranslationStorage;
import com.wmsay.gpt4_lll.utils.CommonUtil;
import com.wmsay.gpt4_lll.utils.FileTypeDetector;
import com.wmsay.gpt4_lll.view.CommentTranslationRenderer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 重新生成翻译动作类
 * Regenerate translation action class
 */
public class RegenerateTranslationAction extends AnAction {
    private static final Logger log = LoggerFactory.getLogger(RegenerateTranslationAction.class);
    
    // 共享渲染器映射（与TranslateCommentsAction共享）
    private static final Map<Editor, CommentTranslationRenderer> rendererMap = new ConcurrentHashMap<>();
    
    static {
        // 添加编辑器监听，支持阅读模式和编辑器生命周期管理
        EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
            @Override
            public void editorReleased(@NotNull EditorFactoryEvent event) {
                Editor editor = event.getEditor();
                CommentTranslationRenderer renderer = rendererMap.remove(editor);
                if (renderer != null) {
                    renderer.dispose();
                }
            }
        }, ApplicationManager.getApplication());
    }
    
    private final CommentTranslationService translationService;
    private final CommentTranslationStorage storage;
    
    public RegenerateTranslationAction() {
        super("Regenerate Translation", "Regenerate comment translations and update cache", null);
        this.translationService = new CommentTranslationService();
        this.storage = CommentTranslationStorage.getInstance();
    }
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        
        if (project == null || editor == null || virtualFile == null) {
            return;
        }
        
        // 检查是否为外部库文件（在读访问中执行）
        boolean shouldShowTranslation = ReadAction.compute(() -> 
            FileTypeDetector.shouldShowTranslation(project, virtualFile));
            
        if (!shouldShowTranslation) {
            ApplicationManager.getApplication().invokeLater(() ->
                Messages.showInfoMessage(project, 
                    "Translation feature is only available for external library files.\n" +
                    "翻译功能仅适用于外部库文件。", 
                    "Comment Translation")
            );
            return;
        }
        
        // 确认是否要重新生成翻译
        int[] result = new int[1];
        ApplicationManager.getApplication().invokeAndWait(() -> {
            result[0] = Messages.showYesNoDialog(project,
                "This will regenerate all comment translations and overwrite the existing cache.\n" +
                "这将重新生成所有注释翻译并覆盖现有缓存。\n\n" +
                "Do you want to continue?\n" +
                "是否继续？",
                "Regenerate Translation",
                Messages.getQuestionIcon());
        });
        
        if (result[0] != Messages.YES) {
            return;
        }
        
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (psiFile == null) {
            return;
        }
        
        String targetLanguage = CommonUtil.getSystemLanguage();
        
        // 清除现有缓存
        storage.clearTranslationCache(virtualFile, targetLanguage);
        
        CommentTranslationRenderer renderer = getOrCreateRenderer(project, editor);
        
        // 执行重新翻译
        performRegenerateTranslation(project, psiFile, virtualFile, targetLanguage, renderer);
    }
    
    /**
     * 执行重新生成翻译操作（流式版本）
     * Perform regenerate translation operation (streaming version)
     */
    private void performRegenerateTranslation(Project project, PsiFile psiFile, VirtualFile virtualFile, 
                                            String targetLanguage, CommentTranslationRenderer renderer) {
        
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Regenerating Comment Translations", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                List<CommentTranslation> allTranslatedComments = new java.util.ArrayList<>();
                
                try {
                    indicator.setText("Extracting comments...");
                    indicator.setFraction(0.1);
                    
                    // 在读访问中提取注释
                    List<CommentTranslation> comments = ReadAction.compute(() -> 
                        translationService.extractComments(psiFile, targetLanguage)
                    );
                    
                    if (comments.isEmpty()) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            Messages.showInfoMessage(project, 
                                "No comments found in this file.\n" +
                                "在此文件中未找到注释。", 
                                "Comment Translation");
                        });
                        return;
                    }
                    
                    indicator.setText("Preparing for streaming regeneration...");
                    indicator.setFraction(0.2);
                    
                    // 清除旧的翻译显示并初始化翻译模式
                    ApplicationManager.getApplication().invokeLater(() -> {
                        renderer.initializeTranslationMode();
                        log.info("Initialized streaming regeneration for {} comments", comments.size());
                    });
                    
                    // 创建进度回调
                    TranslationProgressCallback callback = new TranslationProgressCallback() {
                        @Override
                        public void onTranslationCompleted(CommentTranslation translation, int currentIndex, int totalCount) {
                            // 更新进度
                            double progress = 0.2 + (0.7 * (currentIndex + 1) / totalCount);
                            indicator.setFraction(progress);
                            indicator.setText(String.format("Regenerating translations... (%d/%d)", currentIndex + 1, totalCount));
                            
                            // 在UI线程中立即显示翻译结果
                            ApplicationManager.getApplication().invokeLater(() -> {
                                renderer.addTranslation(translation);
                                log.debug("Displayed streaming regenerated translation {}/{} for comment: '{}'", 
                                         currentIndex + 1, totalCount, 
                                         translation.getOriginalComment().substring(0, Math.min(50, translation.getOriginalComment().length())));
                            });
                            
                            // 收集已翻译的注释用于缓存
                            synchronized (allTranslatedComments) {
                                allTranslatedComments.add(translation);
                            }
                        }
                        
                        @Override
                        public void onTranslationError(String error, int currentIndex, int totalCount) {
                            log.warn("Regeneration error at {}/{}: {}", currentIndex + 1, totalCount, error);
                            // 继续处理下一个注释
                        }
                        
                        @Override
                        public void onAllTranslationsCompleted() {
                            indicator.setText("Updating cache...");
                            indicator.setFraction(0.95);
                            
                            // 保存新的翻译到缓存
                            try {
                                storage.saveTranslationCache(virtualFile, targetLanguage, allTranslatedComments);
                                log.info("Updated cache with {} regenerated translations for file: {}", 
                                        allTranslatedComments.size(), virtualFile.getName());
                            } catch (Exception e) {
                                log.error("Failed to update translation cache", e);
                            }
                            
                            indicator.setFraction(1.0);
                            indicator.setText("Regeneration completed");
                            
                            // 显示完成消息
                            ApplicationManager.getApplication().invokeLater(() -> {
                                Messages.showInfoMessage(project,
                                    "Successfully regenerated " + allTranslatedComments.size() + " comment translations.\n" +
                                    "成功重新生成了 " + allTranslatedComments.size() + " 条注释翻译。",
                                    "Translation Complete");
                                
                                log.info("Completed streaming regeneration of {} comments for file: {}", 
                                        allTranslatedComments.size(), virtualFile.getName());
                            });
                        }
                    };
                    
                    // 开始流式重新翻译
                    translationService.translateCommentsWithCallback(project, comments, targetLanguage, callback);
                    
                } catch (Exception ex) {
                    log.error("Failed to regenerate comment translations", ex);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        Messages.showErrorDialog(project, 
                            "Failed to regenerate translations: " + ex.getMessage() + "\n" +
                            "重新生成翻译失败：" + ex.getMessage(), 
                            "Translation Error");
                    });
                }
            }
        });
    }
    
    /**
     * 获取或创建编辑器的翻译渲染器
     * Get or create translation renderer for editor
     */
    private CommentTranslationRenderer getOrCreateRenderer(Project project, Editor editor) {
        return rendererMap.computeIfAbsent(editor, e -> {
            CommentTranslationRenderer renderer = new CommentTranslationRenderer(project, e);
            
            // 注册编辑器关闭监听器
            registerEditorCloseListener(editor, renderer);
            
            return renderer;
        });
    }
    
    /**
     * 注册编辑器关闭监听器
     * Register editor close listener
     */
    private void registerEditorCloseListener(Editor editor, CommentTranslationRenderer renderer) {
        EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
            @Override
            public void editorReleased(EditorFactoryEvent event) {
                if (event.getEditor() == editor) {
                    renderer.dispose();
                    rendererMap.remove(editor);
                }
            }
        }, ApplicationManager.getApplication());
    }
    
    /**
     * 获取渲染器映射（供TranslateCommentsAction使用）
     * Get renderer map (for use by TranslateCommentsAction)
     */
    public static Map<Editor, CommentTranslationRenderer> getRendererMap() {
        return rendererMap;
    }
    
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        
        boolean isAvailable = project != null && 
                            editor != null && 
                            virtualFile != null && 
                            ReadAction.compute(() -> FileTypeDetector.shouldShowTranslation(project, virtualFile));
        
        e.getPresentation().setEnabledAndVisible(isAvailable);
        
        // 只有在有缓存翻译的情况下才显示重新生成选项
        if (isAvailable) {
            CommentTranslationStorage storage = CommentTranslationStorage.getInstance();
            String targetLanguage = CommonUtil.getSystemLanguage();
            boolean hasCache = ReadAction.compute(() -> 
                storage.getTranslationCache(virtualFile, targetLanguage) != null);
            
            e.getPresentation().setEnabled(hasCache);
            
            if (!hasCache) {
                e.getPresentation().setDescription("No translation cache found. Please translate comments first.");
            }
        }
    }
}
