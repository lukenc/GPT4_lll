package com.wmsay.gpt4_lll;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChangeSourceResolverTest {

    private ChangeSourceResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ChangeSourceResolver();
    }

    // --- 需求 6.1: 两者均为空返回空列表 ---

    /** 两者均为空列表时返回空列表。Validates: Requirements 6.1 */
    @Test
    void bothEmptyReturnsEmptyList() {
        List<String> result = resolver.resolve(Collections.emptyList(), Collections.emptyList());
        assertTrue(result.isEmpty());
    }

    // --- 需求 1.2, 2.2: null 输入处理 ---

    /** selectedChanges 为 null 且 checkedFiles 为 null 时返回空列表。Validates: Requirements 1.2, 2.2 */
    @Test
    void bothNullReturnsEmptyList() {
        List<String> result = resolver.resolve(null, null);
        assertTrue(result.isEmpty());
    }

    /** selectedChanges 为 null、checkedFiles 非空时返回 checkedFiles。Validates: Requirements 1.2, 4.1 */
    @Test
    void nullSelectedWithCheckedReturnsChecked() {
        List<String> checked = List.of("src/A.java", "src/B.java");
        List<String> result = resolver.resolve(null, checked);
        assertEquals(checked, result);
    }

    /** selectedChanges 非空、checkedFiles 为 null 时返回 selectedChanges。Validates: Requirements 2.2, 3.1 */
    @Test
    void selectedWithNullCheckedReturnsSelected() {
        List<String> selected = List.of("src/A.java");
        List<String> result = resolver.resolve(selected, null);
        assertEquals(selected, result);
    }

    // --- 需求 3.1: 仅有选中内容时返回选中内容 ---

    /** 仅有选中内容时返回选中内容。Validates: Requirements 3.1 */
    @Test
    void onlySelectedReturnsSelected() {
        List<String> selected = List.of("src/Main.java", "src/Util.java");
        List<String> result = resolver.resolve(selected, Collections.emptyList());
        assertEquals(selected, result);
    }

    // --- 需求 4.1: 仅有勾选文件时返回勾选文件 ---

    /** 仅有勾选文件时返回勾选文件。Validates: Requirements 4.1 */
    @Test
    void onlyCheckedReturnsChecked() {
        List<String> checked = List.of("src/A.java", "src/B.java");
        List<String> result = resolver.resolve(Collections.emptyList(), checked);
        assertEquals(checked, result);
    }

    // --- 需求 5.1: 单文件选中且属于勾选集合时返回勾选文件 ---

    /** 单文件选中且属于勾选集合时返回勾选文件。Validates: Requirements 5.1 */
    @Test
    void singleSelectedInCheckedReturnsChecked() {
        List<String> selected = List.of("src/A.java");
        List<String> checked = List.of("src/A.java", "src/B.java", "src/C.java");
        List<String> result = resolver.resolve(selected, checked);
        assertEquals(checked, result);
    }

    // --- 需求 5.2: 多文件选中时返回选中内容 ---

    /** 多文件选中时返回选中内容。Validates: Requirements 5.2 */
    @Test
    void multipleSelectedReturnsSelected() {
        List<String> selected = List.of("src/A.java", "src/B.java");
        List<String> checked = List.of("src/A.java", "src/B.java", "src/C.java");
        List<String> result = resolver.resolve(selected, checked);
        assertEquals(selected, result);
    }

    // --- 需求 5.3: 选中文件不属于勾选集合时返回选中内容 ---

    /** 单文件选中但不属于勾选集合时返回选中内容。Validates: Requirements 5.3 */
    @Test
    void singleSelectedNotInCheckedReturnsSelected() {
        List<String> selected = List.of("src/X.java");
        List<String> checked = List.of("src/A.java", "src/B.java");
        List<String> result = resolver.resolve(selected, checked);
        assertEquals(selected, result);
    }

    // --- 需求 7.2: 文件路径精确匹配 ---

    /** 大小写不同的路径不视为相同文件。Validates: Requirements 7.2 */
    @Test
    void pathMatchingIsCaseSensitive() {
        List<String> selected = List.of("src/a.java");
        List<String> checked = List.of("src/A.java", "src/B.java");
        // "src/a.java" != "src/A.java"，不属于 checked → 返回 selected
        List<String> result = resolver.resolve(selected, checked);
        assertEquals(selected, result);
    }

    // --- 返回不可变列表 ---

    /** 返回的列表应为不可变。Validates: Requirements 7.1 */
    @Test
    void returnedListIsUnmodifiable() {
        List<String> selected = List.of("src/A.java");
        List<String> result = resolver.resolve(selected, Collections.emptyList());
        assertThrows(UnsupportedOperationException.class, () -> result.add("src/X.java"));
    }
}
