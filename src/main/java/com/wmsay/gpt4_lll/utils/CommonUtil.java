package com.wmsay.gpt4_lll.utils;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;

public class CommonUtil {
    private static final String TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static HashMap<String ,String > languageMap=new HashMap<>();
    static {
        languageMap.put("ab", "Abkhazian");
        languageMap.put("aa", "Afar");
        languageMap.put("af", "Afrikaans");
        languageMap.put("ak", "Akan");
        languageMap.put("sq", "Albanian");
        languageMap.put("am", "Amharic");
        languageMap.put("ar", "Arabic");
        languageMap.put("an", "Aragonese");
        languageMap.put("hy", "Armenian");
        languageMap.put("as", "Assamese");
        languageMap.put("av", "Avaric");
        languageMap.put("ae", "Avestan");
        languageMap.put("ay", "Aymara");
        languageMap.put("az", "Azerbaijani");
        languageMap.put("bm", "Bambara");
        languageMap.put("ba", "Bashkir");
        languageMap.put("eu", "Basque");
        languageMap.put("be", "Belarusian");
        languageMap.put("bn", "Bengali");
        languageMap.put("bi", "Bislama");
        languageMap.put("bs", "Bosnian");
        languageMap.put("br", "Breton");
        languageMap.put("bg", "Bulgarian");
        languageMap.put("my", "Burmese");
        languageMap.put("ca", "Catalan, Valencian");
        languageMap.put("ch", "Chamorro");
        languageMap.put("ce", "Chechen");
        languageMap.put("ny", "Chichewa, Chewa, Nyanja");
        languageMap.put("zh", "Chinese");
        languageMap.put("cu", "Church Slavonic, Old Slavonic,Old Church Slavonic");
        languageMap.put("cv", "Chuvash");
        languageMap.put("kw", "Cornish");
        languageMap.put("co", "Corsican");
        languageMap.put("cr", "Cree");
        languageMap.put("hr", "Croatian");
        languageMap.put("cs", "Czech");
        languageMap.put("da", "Danish");
        languageMap.put("dv", "Divehi, Dhivehi, Maldivian");
        languageMap.put("nl", "Dutch,Flemish");
        languageMap.put("dz", "Dzongkha");
        languageMap.put("en", "English");
        languageMap.put("eo", "Esperanto");
        languageMap.put("et", "Estonian");
        languageMap.put("ee", "Ewe");
        languageMap.put("fo", "Faroese");
        languageMap.put("fj", "Fijian");
        languageMap.put("fi", "Finnish");
        languageMap.put("fr", "French");
        languageMap.put("fy", "Western Frisian");
        languageMap.put("ff", "Fulah");
        languageMap.put("gd", "Gaelic, Scottish Gaelic");
        languageMap.put("gl", "Galician");
        languageMap.put("lg", "Ganda");
        languageMap.put("ka", "Georgian");
        languageMap.put("de", "German");
        languageMap.put("el", "Greek, Modern (1453–)");
        languageMap.put("kl", "Kalaallisut, Greenlandic");
        languageMap.put("gn", "Guarani");
        languageMap.put("gu", "Gujarati");
        languageMap.put("ht", "Haitian, Haitian Creole");
        languageMap.put("ha", "Hausa");
        languageMap.put("he", "Hebrew");
        languageMap.put("hz", "Herero");
        languageMap.put("hi", "Hindi");
        languageMap.put("ho", "Hiri Motu");
        languageMap.put("hu", "Hungarian");
        languageMap.put("is", "Icelandic");
        languageMap.put("io", "Ido");
        languageMap.put("ig", "Igbo");
        languageMap.put("id", "Indonesian");
        languageMap.put("ia", "Interlingua(International Auxiliary Language Association)");
        languageMap.put("ie", "Interlingue, Occidental");
        languageMap.put("iu", "Inuktitut");
        languageMap.put("ik", "Inupiaq");
        languageMap.put("ga", "Irish");
        languageMap.put("it", "Italian");
        languageMap.put("ja", "Japanese");
        languageMap.put("jv", "Javanese");
        languageMap.put("kn", "Kannada");
        languageMap.put("kr", "Kanuri");
        languageMap.put("ks", "Kashmiri");
        languageMap.put("kk", "Kazakh");
        languageMap.put("km", "Central Khmer");
        languageMap.put("ki", "Kikuyu, Gikuyu");
        languageMap.put("rw", "Kinyarwanda");
        languageMap.put("ky", "Kirghiz, Kyrgyz");
        languageMap.put("kv", "Komi");
        languageMap.put("kg", "Kongo");
        languageMap.put("ko", "Korean");
        languageMap.put("kj", "Kuanyama, Kwanyama");
        languageMap.put("ku", "Kurdish");
        languageMap.put("lo", "Lao");
        languageMap.put("la", "Latin");
        languageMap.put("lv", "Latvian");
        languageMap.put("li", "Limburgan, Limburger, Limburgish");
        languageMap.put("ln", "Lingala");
        languageMap.put("lt", "Lithuanian");
        languageMap.put("lu", "Luba-Katanga");
        languageMap.put("lb", "Luxembourgish, Letzeburgesch");
        languageMap.put("mk", "Macedonian");
        languageMap.put("mg", "Malagasy");
        languageMap.put("ms", "Malay");
        languageMap.put("ml", "Malayalam");
        languageMap.put("mt", "Maltese");
        languageMap.put("gv", "Manx");
        languageMap.put("mi", "Maori");
        languageMap.put("mr", "Marathi");
        languageMap.put("mh", "Marshallese");
        languageMap.put("mn", "Mongolian");
        languageMap.put("na", "Nauru");
        languageMap.put("nv", "Navajo, Navaho");
        languageMap.put("nd", "North Ndebele");
        languageMap.put("nr", "South Ndebele");
        languageMap.put("ng", "Ndonga");
        languageMap.put("ne", "Nepali");
        languageMap.put("no", "Norwegian");
        languageMap.put("nb", "Norwegian Bokmål");
        languageMap.put("nn", "Norwegian Nynorsk");
        languageMap.put("ii", "Sichuan Yi, Nuosu");
        languageMap.put("oc", "Occitan");
        languageMap.put("oj", "Ojibwa");
        languageMap.put("or", "Oriya");
        languageMap.put("om", "Oromo");
        languageMap.put("os", "Ossetian, Ossetic");
        languageMap.put("pi", "Pali");
        languageMap.put("ps", "Pashto, Pushto");
        languageMap.put("fa", "Persian");
        languageMap.put("pl", "Polish");
        languageMap.put("pt", "Portuguese");
        languageMap.put("pa", "Punjabi, Panjabi");
        languageMap.put("qu", "Quechua");
        languageMap.put("ro", "Romanian,Moldavian, Moldovan");
        languageMap.put("rm", "Romansh");
        languageMap.put("rn", "Rundi");
        languageMap.put("ru", "Russian");
        languageMap.put("se", "Northern Sami");
        languageMap.put("sm", "Samoan");
        languageMap.put("sg", "Sango");
        languageMap.put("sa", "Sanskrit");
        languageMap.put("sc", "Sardinian");
        languageMap.put("sr", "Serbian");
        languageMap.put("sn", "Shona");
        languageMap.put("sd", "Sindhi");
        languageMap.put("si", "Sinhala, Sinhalese");
        languageMap.put("sk", "Slovak");
        languageMap.put("sl", "Slovenian");
        languageMap.put("so", "Somali");
        languageMap.put("st", "Southern Sotho");
        languageMap.put("es", "Spanish, Castilian");
        languageMap.put("su", "Sundanese");
        languageMap.put("sw", "Swahili");
        languageMap.put("ss", "Swati");
        languageMap.put("sv", "Swedish");
        languageMap.put("tl", "Tagalog");
        languageMap.put("ty", "Tahitian");
        languageMap.put("tg", "Tajik");
        languageMap.put("ta", "Tamil");
        languageMap.put("tt", "Tatar");
        languageMap.put("te", "Telugu");
        languageMap.put("th", "Thai");
        languageMap.put("bo", "Tibetan");
        languageMap.put("ti", "Tigrinya");
        languageMap.put("to", "Tonga(Tonga Islands)");
        languageMap.put("ts", "Tsonga");
        languageMap.put("tn", "Tswana");
        languageMap.put("tr", "Turkish");
        languageMap.put("tk", "Turkmen");
        languageMap.put("tw", "Twi");
        languageMap.put("ug", "Uighur, Uyghur");
        languageMap.put("uk", "Ukrainian");
        languageMap.put("ur", "Urdu");
        languageMap.put("uz", "Uzbek");
        languageMap.put("ve", "Venda");
        languageMap.put("vi", "Vietnamese");
        languageMap.put("vo", "Volapük");
        languageMap.put("wa", "Walloon");
        languageMap.put("cy", "Welsh");
        languageMap.put("wo", "Wolof");
        languageMap.put("xh", "Xhosa");
        languageMap.put("yi", "Yiddish");
        languageMap.put("yo", "Yoruba");
        languageMap.put("za", "Zhuang, Chuang");
        languageMap.put("zu", "Zulu");
    }


    public static String  getSystemLanguage() {
        Locale locale = Locale.getDefault();
        String languageCode = locale.getLanguage();
        String language=languageMap.get(languageCode);
        if (language==null){
            return "中文";
        }
        return language;
    }


    public static String getOpenFileType(Project project) {
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        VirtualFile file = fileEditorManager.getSelectedFiles()[0];  // 获取当前正在编辑的文件

        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document != null) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile != null) {
                return psiFile.getFileType().getName();
            }
        }
        return "java";
    }

    /**
     * 根据方法类型和时间生成对话主题
     *
     * @param topicContent 对话主题内容
     * @param methodType   方法类型
     * @return             生成的对话主题
     */
    public static String generateTopicByMethodAndTime(String topicContent, String methodType) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TIME_FORMAT);
        String result = now.format(formatter) + "--" + methodType + ":" + topicContent;
        return result;
    }


}
