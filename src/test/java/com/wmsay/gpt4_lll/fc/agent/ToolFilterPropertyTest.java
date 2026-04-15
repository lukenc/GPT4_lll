package com.wmsay.gpt4_lll.fc.agent;

import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.fc.events.ObservabilityManager;
import com.wmsay.gpt4_lll.fc.runtime.IntentResult;
import com.wmsay.gpt4_lll.fc.runtime.ToolFilter;
import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.fc.tools.ToolResult;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ToolFilter 属性测试。
 * <p>
 * 验证 BaseTool 始终包含和空 filteredToolNames 回退为全量两个核心属性。
 */
class ToolFilterPropertyTest {

    private static final Set<String> BASE_TOOLS = ToolFilter.BASE_TOOLS;

    // ---------------------------------------------------------------
    // Property 13: ToolFilter 始终包含 BaseTool
    // Validates: Requirements 13.2
    // ---------------------------------------------------------------

    /**
     * Property 13: 当 filteredToolNames 非空时，过滤结果始终包含 allTools 中存在的所有 BaseTool。
     */
    @Property(tries = 100)
    @Label("Feature: agent-runtime, Property 13: ToolFilter 始终包含 BaseTool")
    void filterResultAlwaysContainsBaseTools(
            @ForAll("nonEmptyFilteredToolNames") List<String> filteredToolNames,
            @ForAll("toolListsWithBaseTools") List<Tool> allTools) {

        ToolFilter filter = new ToolFilter(new ObservabilityManager());
        IntentResult intent = IntentResult.of(
                IntentResult.Clarity.CLEAR,
                IntentResult.Complexity.SIMPLE,
                "react", "test",
                filteredToolNames);

        List<Tool> result = filter.filter(intent, allTools);
        Set<String> resultNames = result.stream().map(Tool::name).collect(Collectors.toSet());
        Set<String> allToolNames = allTools.stream().map(Tool::name).collect(Collectors.toSet());

        // Every BASE_TOOL that exists in allTools must appear in the result
        for (String baseTool : BASE_TOOLS) {
            if (allToolNames.contains(baseTool)) {
                assert resultNames.contains(baseTool) :
                        "BaseTool '" + baseTool + "' exists in allTools but missing from filter result. "
                                + "filteredToolNames=" + filteredToolNames
                                + ", resultNames=" + resultNames;
            }
        }
    }

    /**
     * Property 13 (supplement): 即使 filteredToolNames 不包含任何 BaseTool 名称，
     * 过滤结果仍应包含 allTools 中存在的 BaseTool。
     */
    @Property(tries = 50)
    @Label("Feature: agent-runtime, Property 13: ToolFilter 非 BaseTool filteredToolNames 仍包含 BaseTool")
    void filterWithNonBaseFilteredNamesStillIncludesBaseTools(
            @ForAll("nonBaseToolNames") List<String> filteredToolNames,
            @ForAll("toolListsWithBaseTools") List<Tool> allTools) {

        Assume.that(!filteredToolNames.isEmpty());

        ToolFilter filter = new ToolFilter(new ObservabilityManager());
        IntentResult intent = IntentResult.of(
                IntentResult.Clarity.CLEAR,
                IntentResult.Complexity.COMPLEX,
                "plan_and_execute", "test",
                filteredToolNames);

        List<Tool> result = filter.filter(intent, allTools);
        Set<String> resultNames = result.stream().map(Tool::name).collect(Collectors.toSet());
        Set<String> allToolNames = allTools.stream().map(Tool::name).collect(Collectors.toSet());

        for (String baseTool : BASE_TOOLS) {
            if (allToolNames.contains(baseTool)) {
                assert resultNames.contains(baseTool) :
                        "BaseTool '" + baseTool + "' should always be included, "
                                + "even when filteredToolNames contains no base tools. "
                                + "filteredToolNames=" + filteredToolNames;
            }
        }
    }

    // ---------------------------------------------------------------
    // Property 14: ToolFilter 空 filteredToolNames 回退为全量
    // Validates: Requirements 13.6
    // ---------------------------------------------------------------

    /**
     * Property 14: 当 filteredToolNames 为空列表时，过滤结果应等于全量工具列表。
     */
    @Property(tries = 100)
    @Label("Feature: agent-runtime, Property 14: ToolFilter 空 filteredToolNames 回退为全量")
    void emptyFilteredToolNamesFallsBackToAllTools(
            @ForAll("toolLists") List<Tool> allTools) {

        ToolFilter filter = new ToolFilter(new ObservabilityManager());
        IntentResult intent = IntentResult.of(
                IntentResult.Clarity.CLEAR,
                IntentResult.Complexity.SIMPLE,
                "react", "test",
                Collections.emptyList());

        List<Tool> result = filter.filter(intent, allTools);

        // Result should contain exactly the same tools as allTools
        assert result.size() == allTools.size() :
                "Empty filteredToolNames should return all tools. "
                        + "Expected size=" + allTools.size() + " but got=" + result.size();

        Set<String> resultNames = result.stream().map(Tool::name).collect(Collectors.toSet());
        Set<String> allToolNames = allTools.stream().map(Tool::name).collect(Collectors.toSet());
        assert resultNames.equals(allToolNames) :
                "Empty filteredToolNames should return all tools. "
                        + "Expected=" + allToolNames + " but got=" + resultNames;
    }

    /**
     * Property 14 (null intent): 当 intent 为 null 时，也应回退为全量。
     */
    @Property(tries = 50)
    @Label("Feature: agent-runtime, Property 14: ToolFilter null intent 回退为全量")
    void nullIntentFallsBackToAllTools(
            @ForAll("toolLists") List<Tool> allTools) {

        ToolFilter filter = new ToolFilter(new ObservabilityManager());

        List<Tool> result = filter.filter(null, allTools);

        assert result.size() == allTools.size() :
                "Null intent should return all tools. "
                        + "Expected size=" + allTools.size() + " but got=" + result.size();
    }

    /**
     * Property 14 (null filteredToolNames in intent): 当 intent 的 filteredToolNames 为 null 时回退全量。
     */
    @Property(tries = 50)
    @Label("Feature: agent-runtime, Property 14: ToolFilter null filteredToolNames 回退为全量")
    void nullFilteredToolNamesFallsBackToAllTools(
            @ForAll("toolLists") List<Tool> allTools) {

        ToolFilter filter = new ToolFilter(new ObservabilityManager());
        // IntentResult.of with null filteredToolNames → internally becomes emptyList
        IntentResult intent = IntentResult.of(
                IntentResult.Clarity.CLEAR,
                IntentResult.Complexity.SIMPLE,
                "react", "test",
                null);

        List<Tool> result = filter.filter(intent, allTools);

        assert result.size() == allTools.size() :
                "Null filteredToolNames should return all tools. "
                        + "Expected size=" + allTools.size() + " but got=" + result.size();
    }

    // ---------------------------------------------------------------
    // Providers
    // ---------------------------------------------------------------

    @Provide
    Arbitrary<List<Tool>> toolLists() {
        Arbitrary<String> toolName = Arbitraries.strings()
                .alpha().ofMinLength(2).ofMaxLength(20)
                .map(s -> "tool_" + s);
        return toolName.list().ofMinSize(0).ofMaxSize(10)
                .map(names -> names.stream()
                        .distinct()
                        .map(ToolFilterPropertyTest::stubTool)
                        .collect(Collectors.toList()));
    }

    @Provide
    Arbitrary<List<Tool>> toolListsWithBaseTools() {
        // Always include all BASE_TOOLS plus some random extra tools
        Arbitrary<String> extraName = Arbitraries.strings()
                .alpha().ofMinLength(2).ofMaxLength(20)
                .map(s -> "extra_" + s);
        return extraName.list().ofMinSize(0).ofMaxSize(8)
                .map(extras -> {
                    List<Tool> tools = new ArrayList<>();
                    for (String bt : BASE_TOOLS) {
                        tools.add(stubTool(bt));
                    }
                    for (String extra : extras) {
                        tools.add(stubTool(extra));
                    }
                    return tools;
                });
    }

    @Provide
    Arbitrary<List<String>> nonEmptyFilteredToolNames() {
        Arbitrary<String> name = Arbitraries.strings()
                .alpha().ofMinLength(2).ofMaxLength(20)
                .map(s -> "filtered_" + s);
        return name.list().ofMinSize(1).ofMaxSize(6);
    }

    @Provide
    Arbitrary<List<String>> nonBaseToolNames() {
        // Generate tool names that are guaranteed NOT to be in BASE_TOOLS
        Arbitrary<String> name = Arbitraries.strings()
                .alpha().ofMinLength(2).ofMaxLength(15)
                .map(s -> "custom_" + s)
                .filter(n -> !BASE_TOOLS.contains(n));
        return name.list().ofMinSize(1).ofMaxSize(5);
    }

    // ---------------------------------------------------------------
    // Utility
    // ---------------------------------------------------------------

    private static Tool stubTool(String name) {
        return new Tool() {
            @Override
            public String name() { return name; }

            @Override
            public String description() { return "Stub tool: " + name; }

            @Override
            public Map<String, Object> inputSchema() { return Collections.emptyMap(); }

            @Override
            public ToolResult execute(ToolContext context, Map<String, Object> params) {
                return null;
            }
        };
    }
}
