package com.wmsay.gpt4_lll.fc.runtime;

import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.fc.tools.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentBuilder 和 Agent 高层 API 单元测试。
 */
class AgentBuilderTest {

    private Agent agent;

    @AfterEach
    void tearDown() {
        if (agent != null && !agent.isClosed()) {
            agent.close();
        }
    }

    // ---- AgentBuilder validation tests ----

    @Test
    void build_missingProvider_throwsIllegalArgument() {
        AgentBuilder builder = Agent.builder()
                .apiKey("sk-test")
                .model("gpt-4");
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void build_missingApiKey_throwsIllegalArgument() {
        AgentBuilder builder = Agent.builder()
                .provider("OpenAI")
                .model("gpt-4");
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void build_missingModel_throwsIllegalArgument() {
        AgentBuilder builder = Agent.builder()
                .provider("OpenAI")
                .apiKey("sk-test");
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void build_blankProvider_throwsIllegalArgument() {
        AgentBuilder builder = Agent.builder()
                .provider("  ")
                .apiKey("sk-test")
                .model("gpt-4");
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void build_negativeMaxRounds_throwsIllegalArgument() {
        AgentBuilder builder = Agent.builder()
                .provider("OpenAI")
                .apiKey("sk-test")
                .model("gpt-4")
                .maxRounds(-1);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void build_zeroMaxRounds_throwsIllegalArgument() {
        AgentBuilder builder = Agent.builder()
                .provider("OpenAI")
                .apiKey("sk-test")
                .model("gpt-4")
                .maxRounds(0);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void build_zeroTimeout_throwsIllegalArgument() {
        AgentBuilder builder = Agent.builder()
                .provider("OpenAI")
                .apiKey("sk-test")
                .model("gpt-4")
                .timeout(Duration.ZERO);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    // ---- Successful build tests ----

    @Test
    void build_withRequiredFieldsOnly_succeeds() {
        agent = Agent.builder()
                .provider("OpenAI")
                .apiKey("sk-test")
                .model("gpt-4")
                .build();

        assertNotNull(agent);
        assertFalse(agent.isClosed());
        assertNotNull(agent.getRuntime());
        assertNull(agent.getSession()); // no session until chat()
        assertEquals(60, agent.getMaxRounds()); // default
    }

    @Test
    void build_withAllOptions_succeeds() {
        Tool dummyTool = new Tool() {
            @Override public String name() { return "dummy"; }
            @Override public String description() { return "A dummy tool"; }
            @Override public Map<String, Object> inputSchema() { return Collections.emptyMap(); }
            @Override public ToolResult execute(ToolContext context, Map<String, Object> params) {
                return ToolResult.text("ok");
            }
        };

        agent = Agent.builder()
                .provider("OpenAI")
                .apiKey("sk-test")
                .model("gpt-4")
                .systemPrompt("You are a test assistant")
                .tools(dummyTool)
                .executionStrategy("react")
                .memoryStrategy("sliding_window")
                .maxRounds(30)
                .timeout(Duration.ofSeconds(60))
                .proxy("127.0.0.1:7890")
                .build();

        assertNotNull(agent);
        assertEquals(30, agent.getMaxRounds());
    }

    @Test
    void build_withTimeoutSeconds_succeeds() {
        agent = Agent.builder()
                .provider("OpenAI")
                .apiKey("sk-test")
                .model("gpt-4")
                .timeout(45)
                .build();

        assertNotNull(agent);
    }

    // ---- Agent lifecycle tests ----

    @Test
    void close_idempotent() {
        agent = Agent.builder()
                .provider("OpenAI")
                .apiKey("sk-test")
                .model("gpt-4")
                .build();

        agent.close();
        assertTrue(agent.isClosed());

        // second close should not throw
        assertDoesNotThrow(() -> agent.close());
    }

    @Test
    void chat_afterClose_throwsIllegalState() {
        agent = Agent.builder()
                .provider("OpenAI")
                .apiKey("sk-test")
                .model("gpt-4")
                .build();

        agent.close();
        assertThrows(IllegalStateException.class, () -> agent.chat("hello"));
    }

    @Test
    void chat_nullMessage_throwsIllegalArgument() {
        agent = Agent.builder()
                .provider("OpenAI")
                .apiKey("sk-test")
                .model("gpt-4")
                .build();

        assertThrows(IllegalArgumentException.class, () -> agent.chat(null));
    }

    @Test
    void chat_blankMessage_throwsIllegalArgument() {
        agent = Agent.builder()
                .provider("OpenAI")
                .apiKey("sk-test")
                .model("gpt-4")
                .build();

        assertThrows(IllegalArgumentException.class, () -> agent.chat("  "));
    }

    @Test
    void chatStream_nullCallback_throwsIllegalArgument() {
        agent = Agent.builder()
                .provider("OpenAI")
                .apiKey("sk-test")
                .model("gpt-4")
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> agent.chatStream("hello", null));
    }

    @Test
    void chatStream_afterClose_throwsIllegalState() {
        agent = Agent.builder()
                .provider("OpenAI")
                .apiKey("sk-test")
                .model("gpt-4")
                .build();

        agent.close();
        Agent.StreamCallback cb = new Agent.StreamCallback() {
            @Override public void onDelta(String delta) {}
            @Override public void onComplete(String fullResponse) {}
            @Override public void onError(Exception error) {}
        };
        assertThrows(IllegalStateException.class, () -> agent.chatStream("hello", cb));
    }

    // ---- Builder static entry point ----

    @Test
    void builder_returnsNewInstance() {
        AgentBuilder b1 = Agent.builder();
        AgentBuilder b2 = Agent.builder();
        assertNotSame(b1, b2);
    }
}
