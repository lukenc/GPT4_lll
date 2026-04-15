package com.wmsay.gpt4_lll;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 变更来源解析器 — 根据优先级规则决定使用哪组变更生成 commit message。
 * 纯逻辑类，不依赖 IntelliJ API，可独立进行属性测试。
 */
public class ChangeSourceResolver {

    /**
     * 解析最终使用的变更来源。
     *
     * @param selectedChanges 用户高亮选中的变更文件路径列表
     * @param checkedFiles    用户勾选的文件路径列表
     * @return 最终用于生成 commit message 的文件路径列表（不可变）
     */
    public List<String> resolve(List<String> selectedChanges, List<String> checkedFiles) {
        boolean hasSelected = selectedChanges != null && !selectedChanges.isEmpty();
        boolean hasChecked = checkedFiles != null && !checkedFiles.isEmpty();

        // 规则 1: 两者均为空 → 返回空列表（由调用方回退到 Default Change List）
        if (!hasSelected && !hasChecked) {
            return Collections.emptyList();
        }

        // 规则 2: 仅有勾选文件 → 返回勾选文件
        if (!hasSelected) {
            return Collections.unmodifiableList(checkedFiles);
        }

        // 规则 3: 仅有选中内容 → 返回选中内容
        if (!hasChecked) {
            return Collections.unmodifiableList(selectedChanges);
        }

        // 规则 4: 两者均非空
        Set<String> checkedSet = Set.copyOf(checkedFiles);
        Set<String> selectedFileSet = Set.copyOf(selectedChanges);

        // 4a: 选中仅 1 个文件且属于勾选集合 → 返回勾选文件
        if (selectedFileSet.size() == 1 && checkedSet.containsAll(selectedFileSet)) {
            return Collections.unmodifiableList(checkedFiles);
        }

        // 4b: 选中多个文件，或选中文件不属于勾选集合 → 返回选中内容
        return Collections.unmodifiableList(selectedChanges);
    }
}
