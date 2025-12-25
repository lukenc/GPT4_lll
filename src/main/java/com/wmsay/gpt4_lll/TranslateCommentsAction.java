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
import com.wmsay.gpt4_lll.model.FileTranslationCache;
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

/**
 * 注释翻译动作类
 * Comment translation action class
 */
public class TranslateCommentsAction extends AnAction {
    private static final Logger log = LoggerFactory.getLogger(TranslateCommentsAction.class);
    
    // 存储每个编辑器的翻译渲染器（与RegenerateTranslationAction共享）
    private static Map<Editor, CommentTranslationRenderer> getRendererMap() {
        return RegenerateTranslationAction.getRendererMap();
    }
    
    private final CommentTranslationService translationService;
    private final CommentTranslationStorage storage;
    
    public TranslateCommentsAction() {
        super("Translate Comments", "Translate comments in external library files", null);
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
            String fileType = ReadAction.compute(() -> 
                FileTypeDetector.getFileTypeDescription(project, virtualFile));
            Messages.showInfoMessage(project, 
                "Translation feature is only available for external library files.\n" +
                "翻译功能仅适用于外部库文件。\n\n" +
                "Current file type: " + fileType + "\n" +
                "File path: " + virtualFile.getPath(), 
                "Comment Translation");
            return;
        }
        
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (psiFile == null) {
            return;
        }
        
        CommentTranslationRenderer renderer = getOrCreateRenderer(project, editor);
        
        // 如果当前已经是翻译模式，切换回原文模式
        if (renderer.isTranslationMode()) {
            renderer.clearTranslations();
            log.info("Switched to original comment mode for file: {}", virtualFile.getName());
            return;
        }
        
        // 切换到翻译模式
        String targetLanguage = CommonUtil.getSystemLanguage();
        
        // 检查缓存（在读访问中验证）
        FileTranslationCache cache = ReadAction.compute(() -> 
            storage.getTranslationCache(virtualFile, targetLanguage));
        if (cache != null && !cache.getTranslations().isEmpty()) {
            // 使用缓存的翻译
            ApplicationManager.getApplication().invokeLater(() -> {
                renderer.showTranslations(cache.getTranslations());
                log.info("Loaded {} translations from cache for file: {}", cache.getTranslations().size(), virtualFile.getName());
            });
            return;
        }
        
        log.info("No cache found, starting translation process for file: {}", virtualFile.getName());
        
        // 需要进行翻译
        performTranslation(project, psiFile, virtualFile, targetLanguage, renderer);
    }
    
    /**
     * 执行翻译操作（流式版本）
     * Perform translation operation (streaming version)
     */
    private void performTranslation(Project project, PsiFile psiFile, VirtualFile virtualFile, 
                                  String targetLanguage, CommentTranslationRenderer renderer) {
        
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Translating Comments", true) {
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
                    
                    indicator.setText("Preparing for streaming translation...");
                    indicator.setFraction(0.2);
                    
                    // 初始化翻译模式
                    ApplicationManager.getApplication().invokeLater(() -> {
                        renderer.initializeTranslationMode();
                        log.info("Initialized streaming translation mode for {} comments", comments.size());
                    });
                    
                    // 创建进度回调
                    TranslationProgressCallback callback = new TranslationProgressCallback() {
                        @Override
                        public void onTranslationCompleted(CommentTranslation translation, int currentIndex, int totalCount) {
                            // 更新进度
                            double progress = 0.2 + (0.7 * (currentIndex + 1) / totalCount);
                            indicator.setFraction(progress);
                            indicator.setText(String.format("Translating comments... (%d/%d)", currentIndex + 1, totalCount));
                            
                            // 在UI线程中立即显示翻译结果
                            ApplicationManager.getApplication().invokeLater(() -> {
                                renderer.addTranslation(translation);
                                log.debug("Displayed streaming translation {}/{} for comment: '{}'", 
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
                            log.warn("Translation error at {}/{}: {}", currentIndex + 1, totalCount, error);
                            // 继续处理下一个注释
                        }
                        
                        @Override
                        public void onAllTranslationsCompleted() {
                            indicator.setText("Saving translations...");
                            indicator.setFraction(0.95);
                            
                            // 保存到缓存
                            try {
                                storage.saveTranslationCache(virtualFile, targetLanguage, allTranslatedComments);
                                log.info("Saved {} translations to cache for file: {}", allTranslatedComments.size(), virtualFile.getName());
                            } catch (Exception e) {
                                log.error("Failed to save translations to cache", e);
                            }
                            
                            indicator.setFraction(1.0);
                            indicator.setText("Translation completed");
                            
                            ApplicationManager.getApplication().invokeLater(() -> {
                                log.info("Completed streaming translation of {} comments for file: {}", 
                                        allTranslatedComments.size(), virtualFile.getName());
                            });
                        }
                    };
                    
                    // 开始流式翻译
                    translationService.translateCommentsWithCallback(project, comments, targetLanguage, callback);
                    
                } catch (Exception ex) {
                    log.error("Failed to translate comments", ex);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        Messages.showErrorDialog(project, 
                            "Failed to translate comments: " + ex.getMessage() + "\n" +
                            "翻译注释失败：" + ex.getMessage(), 
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
        return getRendererMap().computeIfAbsent(editor, e -> {
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
                    getRendererMap().remove(editor);
                }
            }
        }, ApplicationManager.getApplication());
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
        
        // 动态更新菜单文本
        if (isAvailable) {
            CommentTranslationRenderer renderer = getRendererMap().get(editor);
            boolean inTranslationMode = false;
            if (renderer != null) {
                inTranslationMode = renderer.isTranslationMode();
            } else {
                // 无渲染器时（如重启后），从折叠占位符探测翻译模式
                try {
                    for (com.intellij.openapi.editor.FoldRegion region : editor.getFoldingModel().getAllFoldRegions()) {
                        String ph = region.getPlaceholderText();
                        if (ph != null && ph.startsWith("[GPT4LLL] ")) {
                            inTranslationMode = true;
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (inTranslationMode) {
                e.getPresentation().setText("Show Original Comments");
                e.getPresentation().setDescription("Switch back to original comments");
            } else {
                e.getPresentation().setText("Translate Comments");
                e.getPresentation().setDescription("Translate comments to " + CommonUtil.getSystemLanguage());
            }
        }
    }
}
