package com.wmsay.gpt4_lll.llm.provider.adapters;

import com.wmsay.gpt4_lll.MyPluginSettings;
import com.wmsay.gpt4_lll.llm.LlmStreamCallback;
import com.wmsay.gpt4_lll.llm.SseStreamProcessor;
import com.wmsay.gpt4_lll.llm.provider.ProviderAdapter;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.utils.AuthUtils;
import com.wmsay.gpt4_lll.utils.ChatUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 免费系列供应商适配器。
 * <p>
 * 当前免费系列复用百度基础设施（ernie-speed-128k），
 * 与 BaiduProviderAdapter 共享消息格式和 SSE 解析逻辑，
 * 但 URL 和认证方式不同。
 *
 * @see BaiduProviderAdapter
 */
public class FreeProviderAdapter implements ProviderAdapter {

    private static final String FREE_API_URL =
            "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/ernie-speed-128k";
    private static final String ACCESS_TOKEN_QUERY_KEY = "?access_token=";

    @Override
    public String getProviderName() {
        return "免费系列"; // ProviderNameEnum.FREE.getProviderName()
    }

    @Override
    public String getApiUrl(MyPluginSettings settings, String modelName) {
        // 免费系列使用固定 URL，忽略 modelName
        String accessToken = AuthUtils.getFreeBaiduAccessToken();
        return FREE_API_URL + ACCESS_TOKEN_QUERY_KEY + accessToken;
    }

    @Override
    public String getApiKey(MyPluginSettings settings) {
        return AuthUtils.getFreeBaiduAccessToken();
    }

    @Override
    public boolean supportsSystemRole() {
        return false; // 复用百度基础设施，不支持 system role
    }

    @Override
    public List<Message> adaptMessages(List<Message> messages) {
        // 与 BaiduProviderAdapter 相同的消息适配规则
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
}
