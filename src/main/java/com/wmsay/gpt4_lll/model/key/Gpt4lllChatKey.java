package com.wmsay.gpt4_lll.model.key;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Key;
import com.wmsay.gpt4_lll.model.Message;

import java.util.List;

/**
 * 放置存放于project中，获取对话数据的key
* @author liuchuan
* @date 2024/10/24 12:05 AM
*/
public class Gpt4lllChatKey {
    public static final Key<Boolean> GPT_4_LLL_RUNNING_STATUS = Key.create("GPT4lllRunningStatus");

    public static final Key<String> GPT_4_LLL_NOW_TOPIC = Key.create("GPT4lllNowTopic");

    public static final Key<List<Message>> GPT_4_LLL_CONVERSATION_HISTORY = Key.create("GPT4lllConversationHistory");

}
