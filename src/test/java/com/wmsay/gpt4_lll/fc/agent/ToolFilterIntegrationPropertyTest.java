package com.wmsay.gpt4_lll.fc.agent;

import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.fc.events.ObservabilityManager;
import com.wmsay.gpt4_lll.fc.runtime.IntentResult;
import com.wmsay.gpt4_lll.fc.runtime.ToolFilter;
import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.fc.tools.ToolResult;
import net.jqwik.api.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ToolFilter 集成属性测试 — agent-runtime-ui-integration spec。
 * <p>
 * 验证 Property 8（BaseTool 始终包含）和 Property 9（空过滤名称回退全量）。
 */
class ToolFilterIntegrationPropertyTest {

    private static final Set<String> BASE_TOOLS = ToolFilter.BASE_TOOLS;

    // ---------------------------------------------------------------
    // Property 8: 工具过滤始终包含 BaseTool
    // Validates: Requirements 9.1
    // ---------------------------------------------------------------

    /**
     * Property 8: 对于任意 IntentResult（非空 filteredToolNames）和 allTools 列表，
     * filter() 结果始终包含 BaseTool 集合中存在于 allTools 中的所有工具。
     */
    @Property(tries = 200)
    @Label("Feature: agent-runtime-ui-integration, Property 8: 工具过滤始终包含 BaseTool")
    void filterAlwaysContainsBaseToolsPresentInAllTools(
            @ForAll("randomIntentWithNonEmptyFilter") IntentResult intent,
            @ForAll("randomToolListWithSomeBaseTools") List<Tool> allTools) {

        ToolFilter filter = new ToolFilter(new ObservabilityManager());

        List<Tool> result = filter.filter(intent, allTools);
        Set<String> resultNames = result.stream().map(Tool::name).collect(Collectors.toSet());
        Set<String> allToolNames = allTools.stream().map(Tool::name).collect(Collectors.toSet());

        for (String baseTool : BASE_TOOLS) {
            if (allToolNames.contains(baseTool)) {
                assert resultNames.contains(baseTool) :
                        "BaseTool '" + baseTool + "' exists in allTools but missing from filter result. "
                                + "filteredToolNames=" + intent.getFilteredToolNames()
                                + ", resultNames=" + resultNames;
            }
        }
    }

    // ---------------------------------------------------------------
    // Property 9: 空过滤名称回退到全量工具
    // Validates: Requirements 9.2
    // ---------------------------------------------------------------

    /**
     * Property 9: 当 filteredToolNames 为空列表时，filter() 返回全量工具列表。
     */
    @Property(tries = 200)
    @Label("Feature: agent-runtime-ui-integration, Property 9: 空过滤名称回退到全量工具")
    void emptyFilteredToolNamesReturnsAllTools(
            @ForAll("randomToolList") List<Tool> allTools) {

        ToolFilter filter = new ToolFilter(new ObservabilityManager());
        IntentResult intent = IntentResult.of(
                IntentResult.Clarity.CLEAR,
                IntentResult.Complexity.SIMPLE,
                "react", "test",
                Collections.emptyList());

        List<Tool> result = filter.filter(intent, allTools);

        Set<String> resultNames = result.stream().map(Tool::name).collect(Collectors.toSet());
        Set<String> allToolNames = allTools.stream().map(Tool::name).collect(Collectors.toSet());

        assert result.size() == allTools.size() :
                "Empty filteredToolNames should return all tools. "
                        + "Expected size=" + allTools.size() + " but got=" + result.size();
        assert resultNames.equals(allToolNames) :
                "Empty filteredToolNames should return all tools. "
                        + "Expected=" + allToolNames + " but got=" + resultNames;
    }

    // ---------------------------------------------------------------
    // Providers
    // ---------------------------------------------------------------

    @Provide
    Arbitrary<IntentResult> randomIntentWithNonEmptyFilter() {
        Arbitrary<IntentResult.Clarity> clarity = Arbitraries.of(IntentResult.Clarity.values());
        Arbitrary<IntentResult.Complexity> complexity = Arbitraries.of(IntentResult.Complexity.values());
        Arbitrary<String> strategy = Arbitraries.of("react", "plan_and_execute");
        Arbitrary<List<String>> filteredNames = Arbitraries.strings()
                .alpha().ofMinLength(2).ofMaxLength(15)
                .map(s -> "tool_" + s)
                .list().ofMinSize(1).ofMaxSize(8);

        return Combinators.combine(clarity, complexity, strategy, filteredNames)
                .as((c, cx, s, names) -> IntentResult.of(c, cx, s, "test-reasoning", names));
    }

    @Provide
    Arbitrary<List<Tool>> randomToolListWithSomeBaseTools() {
        // Include a random subset of BASE_TOOLS plus random extra tools
        Arbitrary<Set<String>> baseSubset = Arbitraries.subsetOf(BASE_TOOLS).ofMinSize(0);
        Arbitrary<List<String>> extraNames = Arbitraries.strings()
                .alpha().ofMinLength(2).ofMaxLength(15)
                .map(s -> "extra_" + s)
                .filter(n -> !BASE_TOOLS.contains(n))
                .list().ofMinSize(0).ofMaxSize(8);

        return Combinators.combine(baseSubset, extraNames)
                .as((bases, extras) -> {
                    Set<String> allNames = new LinkedHashSet<>(bases);
                    allNames.addAll(extras);
                    return allNames.stream()
                            .map(ToolFilterIntegrationPropertyTest::stubTool)
                            .collect(Collectors.toList());
                });
    }

    @Provide
    Arbitrary<List<Tool>> randomToolList() {
        Arbitrary<String> toolName = Arbitraries.strings()
                .alpha().ofMinLength(2).ofMaxLength(20)
                .map(s -> "tool_" + s);
        return toolName.list().ofMinSize(0).ofMaxSize(12)
                .map(names -> names.stream()
                        .distinct()
                        .map(ToolFilterIntegrationPropertyTest::stubTool)
                        .collect(Collectors.toList()));
    }

    // ---------------------------------------------------------------
    // Utility
    // ---------------------------------------------------------------

    private static Tool stubTool(String name) {
        return new Tool() {
            @Override public String name() { return name; }
            @Override public String description() { return "Stub: " + name; }
            @Override public Map<String, Object> inputSchema() { return Collections.emptyMap(); }
            @Override public ToolResult execute(ToolContext ctx, Map<String, Object> params) { return null; }
        };
    }
}
