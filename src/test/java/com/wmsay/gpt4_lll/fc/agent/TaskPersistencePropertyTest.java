package com.wmsay.gpt4_lll.fc.agent;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TaskPersistence 属性测试。
 * <p>
 * 验证序列化往返一致性和崩溃恢复（RUNNING→PENDING）。
 * 使用临时目录进行文件 IO 测试。
 */
class TaskPersistencePropertyTest {

    // ---------------------------------------------------------------
    // Property 21: TaskPersistence 序列化往返
    // Validates: Requirements 18.1, 18.3
    // ---------------------------------------------------------------

    /**
     * Property 21: 对任意步骤列表和状态映射，save 后 load 应返回等价数据。
     * 步骤列表内容和顺序一致，状态映射的 key-value 一致。
     */
    @Property(tries = 80)
    @Label("Feature: agent-runtime, Property 21: TaskPersistence 序列化往返")
    void saveAndLoadShouldRoundTrip(
            @ForAll("stepLists") List<String> steps,
            @ForAll("taskStateArbitrary") TaskState assignedState) throws IOException {

        Path tempDir = Files.createTempDirectory("task-persist-test");
        try {
            TaskPersistence persistence = new TaskPersistence(tempDir.toString());
            String sessionId = "test-session-" + UUID.randomUUID();

            // Build state map: assign the same state to all steps
            ConcurrentHashMap<Integer, TaskState> stepStates = new ConcurrentHashMap<>();
            for (int i = 0; i < steps.size(); i++) {
                stepStates.put(i, assignedState);
            }

            TaskPersistenceData original = new TaskPersistenceData(steps, stepStates);

            // Save then load
            persistence.save(sessionId, original);
            TaskPersistenceData loaded = persistence.load(sessionId);

            // Steps round-trip
            assert loaded.getSteps().equals(original.getSteps()) :
                    "Steps should round-trip: expected " + original.getSteps()
                            + " but got " + loaded.getSteps();

            // State map round-trip
            ConcurrentHashMap<Integer, TaskState> originalStates = original.getStepStates();
            ConcurrentHashMap<Integer, TaskState> loadedStates = loaded.getStepStates();
            assert loadedStates.size() == originalStates.size() :
                    "State map size mismatch: expected " + originalStates.size()
                            + " but got " + loadedStates.size();

            for (Map.Entry<Integer, TaskState> entry : originalStates.entrySet()) {
                assert loadedStates.get(entry.getKey()) == entry.getValue() :
                        "State mismatch at index " + entry.getKey()
                                + ": expected " + entry.getValue()
                                + " but got " + loadedStates.get(entry.getKey());
            }
        } finally {
            deleteRecursively(tempDir);
        }
    }

    /**
     * Property 21 (supplement): 对混合状态的步骤列表，save 后 load 应保留每个步骤的独立状态。
     */
    @Property(tries = 50)
    @Label("Feature: agent-runtime, Property 21: TaskPersistence 混合状态序列化往返")
    void mixedStatesShouldRoundTrip(
            @ForAll @IntRange(min = 1, max = 10) int stepCount) throws IOException {

        Path tempDir = Files.createTempDirectory("task-persist-mixed");
        try {
            TaskPersistence persistence = new TaskPersistence(tempDir.toString());
            String sessionId = "mixed-" + UUID.randomUUID();

            List<String> steps = new ArrayList<>();
            ConcurrentHashMap<Integer, TaskState> stepStates = new ConcurrentHashMap<>();
            TaskState[] allStates = TaskState.values();

            for (int i = 0; i < stepCount; i++) {
                steps.add("Step " + i + ": do something");
                stepStates.put(i, allStates[i % allStates.length]);
            }

            TaskPersistenceData original = new TaskPersistenceData(steps, stepStates);
            persistence.save(sessionId, original);
            TaskPersistenceData loaded = persistence.load(sessionId);

            assert loaded.getSteps().size() == stepCount :
                    "Step count mismatch after round-trip";

            for (int i = 0; i < stepCount; i++) {
                assert loaded.getSteps().get(i).equals(steps.get(i)) :
                        "Step text mismatch at index " + i;
                assert loaded.getStepStates().get(i) == stepStates.get(i) :
                        "State mismatch at index " + i
                                + ": expected " + stepStates.get(i)
                                + " but got " + loaded.getStepStates().get(i);
            }
        } finally {
            deleteRecursively(tempDir);
        }
    }

    /**
     * Property 21 (empty): 空数据 save 后 load 应返回空数据。
     */
    @Property(tries = 10)
    @Label("Feature: agent-runtime, Property 21: TaskPersistence 空数据序列化往返")
    void emptyDataShouldRoundTrip() throws IOException {
        Path tempDir = Files.createTempDirectory("task-persist-empty");
        try {
            TaskPersistence persistence = new TaskPersistence(tempDir.toString());
            String sessionId = "empty-" + UUID.randomUUID();

            TaskPersistenceData original = TaskPersistenceData.empty();
            persistence.save(sessionId, original);
            TaskPersistenceData loaded = persistence.load(sessionId);

            assert loaded.getSteps().isEmpty() :
                    "Empty data should round-trip to empty steps";
            assert loaded.getStepStates().isEmpty() :
                    "Empty data should round-trip to empty states";
        } finally {
            deleteRecursively(tempDir);
        }
    }

    /**
     * Property 21 (load non-existent): load 不存在的 sessionId 应返回空数据。
     */
    @Property(tries = 10)
    @Label("Feature: agent-runtime, Property 21: TaskPersistence 加载不存在文件返回空")
    void loadNonExistentShouldReturnEmpty() throws IOException {
        Path tempDir = Files.createTempDirectory("task-persist-nofile");
        try {
            TaskPersistence persistence = new TaskPersistence(tempDir.toString());
            TaskPersistenceData loaded = persistence.load("non-existent-session");

            assert loaded.isEmpty() : "Loading non-existent session should return empty data";
        } finally {
            deleteRecursively(tempDir);
        }
    }

    // ---------------------------------------------------------------
    // Property 22: TaskPersistence 崩溃恢复 RUNNING→PENDING
    // Validates: Requirements 18.4, 18.5
    // ---------------------------------------------------------------

    /**
     * Property 22: recoverFromCrash 应将所有 RUNNING 状态的步骤重置为 PENDING，
     * 其他状态保持不变。
     */
    @Property(tries = 80)
    @Label("Feature: agent-runtime, Property 22: TaskPersistence 崩溃恢复 RUNNING→PENDING")
    void recoverFromCrashShouldResetRunningToPending(
            @ForAll @IntRange(min = 1, max = 10) int stepCount,
            @ForAll("taskStateListArbitrary") List<TaskState> stateTemplate) throws IOException {

        Path tempDir = Files.createTempDirectory("task-persist-crash");
        try {
            TaskPersistence persistence = new TaskPersistence(tempDir.toString());
            String sessionId = "crash-" + UUID.randomUUID();

            List<String> steps = new ArrayList<>();
            ConcurrentHashMap<Integer, TaskState> stepStates = new ConcurrentHashMap<>();

            for (int i = 0; i < stepCount; i++) {
                steps.add("Step " + i);
                // Cycle through the template states
                stepStates.put(i, stateTemplate.get(i % stateTemplate.size()));
            }

            // Save the "crashed" state
            TaskPersistenceData crashedData = new TaskPersistenceData(steps, stepStates);
            persistence.save(sessionId, crashedData);

            // Recover
            TaskPersistenceData recovered = persistence.recoverFromCrash(sessionId);

            // Verify: RUNNING → PENDING, others unchanged
            for (int i = 0; i < stepCount; i++) {
                TaskState originalState = stepStates.get(i);
                TaskState recoveredState = recovered.getStepStates().get(i);

                if (originalState == TaskState.RUNNING) {
                    assert recoveredState == TaskState.PENDING :
                            "RUNNING step at index " + i + " should be reset to PENDING, got " + recoveredState;
                } else {
                    assert recoveredState == originalState :
                            "Non-RUNNING step at index " + i + " should remain " + originalState
                                    + ", got " + recoveredState;
                }
            }

            // Verify the recovered data was also persisted
            TaskPersistenceData reloaded = persistence.load(sessionId);
            for (int i = 0; i < stepCount; i++) {
                assert reloaded.getStepStates().get(i) == recovered.getStepStates().get(i) :
                        "Recovered data should be persisted: mismatch at index " + i;
            }
        } finally {
            deleteRecursively(tempDir);
        }
    }

    /**
     * Property 22 (supplement): recoverFromCrash 对不存在的 session 应返回空数据。
     */
    @Property(tries = 10)
    @Label("Feature: agent-runtime, Property 22: TaskPersistence 崩溃恢复不存在文件返回空")
    void recoverNonExistentShouldReturnEmpty() throws IOException {
        Path tempDir = Files.createTempDirectory("task-persist-crash-nofile");
        try {
            TaskPersistence persistence = new TaskPersistence(tempDir.toString());
            TaskPersistenceData recovered = persistence.recoverFromCrash("no-such-session");

            assert recovered.isEmpty() :
                    "Recovering non-existent session should return empty data";
        } finally {
            deleteRecursively(tempDir);
        }
    }

    /**
     * Property 22 (all-running): 当所有步骤都是 RUNNING 时，恢复后应全部变为 PENDING。
     */
    @Property(tries = 30)
    @Label("Feature: agent-runtime, Property 22: TaskPersistence 全 RUNNING 崩溃恢复")
    void allRunningStepsShouldBecomeAllPending(
            @ForAll @IntRange(min = 1, max = 8) int stepCount) throws IOException {

        Path tempDir = Files.createTempDirectory("task-persist-allrunning");
        try {
            TaskPersistence persistence = new TaskPersistence(tempDir.toString());
            String sessionId = "allrunning-" + UUID.randomUUID();

            List<String> steps = new ArrayList<>();
            ConcurrentHashMap<Integer, TaskState> stepStates = new ConcurrentHashMap<>();
            for (int i = 0; i < stepCount; i++) {
                steps.add("Running step " + i);
                stepStates.put(i, TaskState.RUNNING);
            }

            persistence.save(sessionId, new TaskPersistenceData(steps, stepStates));
            TaskPersistenceData recovered = persistence.recoverFromCrash(sessionId);

            for (int i = 0; i < stepCount; i++) {
                assert recovered.getStepStates().get(i) == TaskState.PENDING :
                        "All RUNNING steps should become PENDING after crash recovery, "
                                + "but step " + i + " is " + recovered.getStepStates().get(i);
            }
        } finally {
            deleteRecursively(tempDir);
        }
    }

    // ---------------------------------------------------------------
    // Cleanup helper (isAllTerminal + cleanup)
    // ---------------------------------------------------------------

    /**
     * Supplementary: cleanup 应删除持久化文件，后续 load 返回空。
     */
    @Property(tries = 20)
    @Label("Feature: agent-runtime, Property 21: TaskPersistence cleanup 后 load 返回空")
    void cleanupShouldRemoveFile(
            @ForAll @IntRange(min = 1, max = 5) int stepCount) throws IOException {

        Path tempDir = Files.createTempDirectory("task-persist-cleanup");
        try {
            TaskPersistence persistence = new TaskPersistence(tempDir.toString());
            String sessionId = "cleanup-" + UUID.randomUUID();

            List<String> steps = new ArrayList<>();
            ConcurrentHashMap<Integer, TaskState> states = new ConcurrentHashMap<>();
            for (int i = 0; i < stepCount; i++) {
                steps.add("Step " + i);
                states.put(i, TaskState.COMPLETED);
            }
            persistence.save(sessionId, new TaskPersistenceData(steps, states));

            // Verify file exists
            TaskPersistenceData loaded = persistence.load(sessionId);
            assert !loaded.isEmpty() : "Data should exist before cleanup";

            // Cleanup
            persistence.cleanup(sessionId);

            // Verify file removed
            TaskPersistenceData afterCleanup = persistence.load(sessionId);
            assert afterCleanup.isEmpty() : "Data should be empty after cleanup";
        } finally {
            deleteRecursively(tempDir);
        }
    }

    // ---------------------------------------------------------------
    // isAllTerminal
    // ---------------------------------------------------------------

    /**
     * Supplementary: isAllTerminal 应正确判断所有步骤是否处于终态。
     */
    @Property(tries = 50)
    @Label("Feature: agent-runtime, Property 22: TaskPersistence isAllTerminal 正确性")
    void isAllTerminalShouldBeCorrect(
            @ForAll @IntRange(min = 1, max = 8) int stepCount,
            @ForAll("taskStateListArbitrary") List<TaskState> stateTemplate) throws IOException {

        Path tempDir = Files.createTempDirectory("task-persist-terminal");
        try {
            TaskPersistence persistence = new TaskPersistence(tempDir.toString());

            List<String> steps = new ArrayList<>();
            ConcurrentHashMap<Integer, TaskState> stepStates = new ConcurrentHashMap<>();
            boolean expectTerminal = true;

            for (int i = 0; i < stepCount; i++) {
                steps.add("Step " + i);
                TaskState state = stateTemplate.get(i % stateTemplate.size());
                stepStates.put(i, state);
                if (state == TaskState.PENDING || state == TaskState.RUNNING) {
                    expectTerminal = false;
                }
            }

            TaskPersistenceData data = new TaskPersistenceData(steps, stepStates);
            boolean actual = persistence.isAllTerminal(data);

            assert actual == expectTerminal :
                    "isAllTerminal expected " + expectTerminal + " but got " + actual
                            + " for states: " + stepStates;
        } finally {
            deleteRecursively(tempDir);
        }
    }

    // ---------------------------------------------------------------
    // Providers
    // ---------------------------------------------------------------

    @Provide
    Arbitrary<List<String>> stepLists() {
        Arbitrary<String> stepName = Arbitraries.strings()
                .alpha().ofMinLength(1).ofMaxLength(30)
                .map(s -> "Step: " + s);
        return stepName.list().ofMinSize(0).ofMaxSize(8);
    }

    @Provide
    Arbitrary<TaskState> taskStateArbitrary() {
        return Arbitraries.of(TaskState.values());
    }

    @Provide
    Arbitrary<List<TaskState>> taskStateListArbitrary() {
        return Arbitraries.of(TaskState.values()).list().ofMinSize(1).ofMaxSize(6);
    }

    // ---------------------------------------------------------------
    // Utility
    // ---------------------------------------------------------------

    private static void deleteRecursively(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (var entries = Files.list(path)) {
                    entries.forEach(TaskPersistencePropertyTest::deleteRecursively);
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup
        }
    }
}
