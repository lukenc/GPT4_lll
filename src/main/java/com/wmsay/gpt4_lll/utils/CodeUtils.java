package com.wmsay.gpt4_lll.utils;

import java.util.HashMap;
import java.util.Map;

public class CodeUtils {
    static Map<String, String> extensionToLanguageMap;
    static {
        extensionToLanguageMap = new HashMap<>();
        extensionToLanguageMap.put("java", "Java");
        extensionToLanguageMap.put("c", "C");
        extensionToLanguageMap.put("cpp", "C++");
        extensionToLanguageMap.put("cxx", "C++");
        extensionToLanguageMap.put("cc", "C++");
        extensionToLanguageMap.put("h", "C/C++ Header");
        extensionToLanguageMap.put("hpp", "C++ Header");
        extensionToLanguageMap.put("cs", "C#");
        extensionToLanguageMap.put("py", "Python");
        extensionToLanguageMap.put("py3", "Python 3");
        extensionToLanguageMap.put("rb", "Ruby");
        extensionToLanguageMap.put("js", "JavaScript");
        extensionToLanguageMap.put("jsx", "JavaScript JSX");
        extensionToLanguageMap.put("ts", "TypeScript");
        extensionToLanguageMap.put("tsx", "TypeScript JSX");
        extensionToLanguageMap.put("html", "HTML");
        extensionToLanguageMap.put("htm", "HTML");
        extensionToLanguageMap.put("xhtml", "XHTML");
        extensionToLanguageMap.put("css", "CSS");
        extensionToLanguageMap.put("scss", "Sass");
        extensionToLanguageMap.put("sass", "Sass");
        extensionToLanguageMap.put("php", "PHP");
        extensionToLanguageMap.put("php4", "PHP");
        extensionToLanguageMap.put("php5", "PHP");
        extensionToLanguageMap.put("phtml", "PHP");
        extensionToLanguageMap.put("go", "Go");
        extensionToLanguageMap.put("rs", "Rust");
        extensionToLanguageMap.put("swift", "Swift");
        extensionToLanguageMap.put("kt", "Kotlin");
        extensionToLanguageMap.put("groovy", "Groovy");
        extensionToLanguageMap.put("pl", "Perl");
        extensionToLanguageMap.put("pm", "Perl Module");
        extensionToLanguageMap.put("t", "Perl Test");
        extensionToLanguageMap.put("lua", "Lua");
        extensionToLanguageMap.put("r", "R");
        extensionToLanguageMap.put("sh", "Shell Script");
        extensionToLanguageMap.put("bash", "Bash");
        extensionToLanguageMap.put("zsh", "Zsh");
        extensionToLanguageMap.put("sql", "SQL");
        extensionToLanguageMap.put("psql", "PostgreSQL");
        extensionToLanguageMap.put("plsql", "PL/SQL");
        extensionToLanguageMap.put("xml", "XML");
        extensionToLanguageMap.put("json", "JSON");
        extensionToLanguageMap.put("yaml", "YAML");
        extensionToLanguageMap.put("yml", "YAML");
        extensionToLanguageMap.put("md", "Markdown");
        extensionToLanguageMap.put("markdown", "Markdown");
        extensionToLanguageMap.put("m", "Objective-C");
        extensionToLanguageMap.put("mm", "Objective-C++");
        extensionToLanguageMap.put("clj", "Clojure");
        extensionToLanguageMap.put("cljs", "ClojureScript");
        extensionToLanguageMap.put("cljc", "Clojure/ClojureScript");
        extensionToLanguageMap.put("edn", "EDN");
        extensionToLanguageMap.put("scala", "Scala");
        extensionToLanguageMap.put("ex", "Elixir");
        extensionToLanguageMap.put("exs", "Elixir");
        extensionToLanguageMap.put("erl", "Erlang");
        extensionToLanguageMap.put("hrl", "Erlang Header");
        extensionToLanguageMap.put("v", "Verilog");
        extensionToLanguageMap.put("sv", "SystemVerilog");
        extensionToLanguageMap.put("vhdl", "VHDL");
        extensionToLanguageMap.put("vue", "Vue");
    }
    public static String WEB_DEV_STD= """
            1、命名规范：变量、函数、类的命名是否清晰、描述性强。
            2、代码结构：是否有合理的段落划分，逻辑清晰。单个函数或类是否过大，是否遵循单一职责原则。
            3、注释质量：注释是否充分且准确，对复杂代码段提供了解释。JSDoc 或其他文档风格的使用
            4、异步处理：是否合理使用异步编程（如 Promises、async/await）来提高性能。
            5、算法效率：代码中使用的算法和数据结构是否高效。减少不必要的计算和内存使用。
            6、内存泄漏：是否存在潜在的内存泄漏。
            7、安全问题：是有存在常见的前端安全问题。
            8、资源懒加载：对于大型资源（如图片、视频），是否采用懒加载技术。
            9、错误处理：是否有有效的错误捕捉和处理机制。
            10、兼容性：使用的语法是否兼容主流浏览器和设备。
            """;

    public static String BACK_END_DEV_STD= """
            1、类、方法、变量的命名：1、是否遵循编程语言的命名惯例；2、命名是否清晰、描述性强，易于理解其作用；3、是否避免了过于简短或过于泛化的命名，以及歧义和误导性的命名；
            2、空指针风险：这一项要谨慎评估。不要遗漏，也不要误报。
            3、数组越界风险：评估数组和集合操作中是否有适当的长度或边界检查。
            4、并发控制：1、能否使用多线程技术完善当前代码，提升效率；2、是否正确使用同步和锁机制；3、对多线程和并发操作的处理是否正确；4、检查死锁风险和线程安全问题；
            5、注释完整性：1、检查关键代码段是否有清晰的注释；2、注释是否与代码逻辑一致；3、文档注释（如 Javadoc）的完整性；
            6、异常捕捉及处理：1、是否需要捕捉异常而没有做；2、检查异常处理是否合理，避免过分泛化的捕捉；
            7、日志的完善度和合规性：1、日志是否包含足够的上下文信息，便于问题排查；2、检查日志级别的适当性（如 debug, info, warn, error）；3、确保不记录敏感信息，如密码和个人数据
            8、是否有安全方面的问题：是否存在安全漏洞
            9、是否有性能方面的问题：1、评估算法和数据结构选择对性能的影响；2、检查是否有不必要的数据库访问或网络通信，避免不必要的循环和重复计算能否改用批处理；3、评估资源使用和管理，如内存泄漏、文件句柄泄漏等；4、能否适当使用缓存机制和异步处理，以提高应用性能。
            10、其余方面（例如：1、可维护性；2、是否使用了设计模式和最佳实践以提高代码重用；3、如果是方法，是否易于使用；以及其他更多）
            """;
    public static String getLanguageByFileType(String fileType){
        return extensionToLanguageMap.get(fileType);
    }



}
