package com.wmsay.gpt4_lll.component;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Key;
import com.wmsay.gpt4_lll.model.SelectModelOption;

public class Gpt4lllComboxKey {

    public static final Key<ComboBox<String>> GPT_4_LLL_PROVIDER_COMBO_BOX = Key.create("GPT4lllProviderComboBox");
    public static final Key<ComboBox<SelectModelOption>> GPT_4_LLL_MODEL_COMBO_BOX = Key.create("GPT4lllModelComboBox");

}
