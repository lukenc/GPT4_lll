package com.wmsay.gpt4_lll.llm.provider.adapters;

import com.wmsay.gpt4_lll.MyPluginSettings;
import com.wmsay.gpt4_lll.llm.provider.ProviderAdapter;
import com.wmsay.gpt4_lll.fc.core.Message;
import com.wmsay.gpt4_lll.fc.llm.LlmStreamCallback;
import com.wmsay.gpt4_lll.fc.llm.SseStreamProcessor;
import com.wmsay.gpt4_lll.utils.AuthUtils;
import com.wmsay.gpt4_lll.utils.ChatUtils;
import com.wmsay.gpt4_lll.utils.ModelUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 百度文心一言供应商适配器。
 * <p>
 * 将以下散落逻辑集中到一处：
 * - ChatUtils.getUrl() 中的百度 URL 拼接（access_token）
 * - ChatUtils.getApiKey() 中的百度 Key 获取
 * - 百度消息交替规则适配
 * - SseStreamProcessor 中的 BaiduSseResponse 解析
 * - 各 Action 中的 if(BAIDU) systemMessage.setRole("user") 判断
 */
public class BaiduProviderAdapter implements ProviderAdapter {

    private static final String ACCESS_TOKEN_QUERY_KEY = "?access_token=";

    @Override
    public String getProviderName() {
        return "Baidu"; // ProviderNameEnum.BAIDU.getProviderName()
    }

    @Override
    public String getApiUrl(MyPluginSettings settings, String modelName) {
        String accessToken = AuthUtils.getBaiduAccessToken();
        return ModelUtils.getUrlByProvider("Baidu") + modelName + ACCESS_TOKEN_QUERY_KEY + accessToken;
    }

    @Override
    public String getApiKey(MyPluginSettings settings) {
        return AuthUtils.getBaiduAccessToken();
    }

    @Override
    public boolean supportsSystemRole() {
        return false; // 百度不支持 system role，降级为 user
    }

    /**
     * 百度消息格式适配。
     * 消息适配规则：
     * - 第一条消息 role 强制 "user"
     * - 奇数索引位置如果是 user，插入 assistant 占位消息
     * <p>
     * ⚠️ 返回新列表，不修改原始列表。
     */
    @Override
    public List<Message> adaptMessages(List<Message> messages) {
        List<Message> adapted = new ArrayList<>(messages);
        for (int i = 0; i < adapted.size(); i++) {
            Message message = adapted.get(i);
            if (i == 0) {
                message.setRole("user");
            } else if (i % 2 == 1 && "user".equals(message.getRole())) {
                adapted.add(i, ChatUtils.getOddMessage4Baidu());
            }
        }
        return adapted;
    }

    @Override
    public void parseSseLine(String lineData, LlmStreamCallback callback) {
        SseStreamProcessor.processBaiduLine(lineData, callback);
    }

    @Override
    public boolean supportsContinuationRetry() {
        return true; // 百度 API 有时截断响应，需要续传重试
    }
}
