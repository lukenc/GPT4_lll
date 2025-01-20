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
            5、注释完整性：1、检查关键代码段是否有清晰的注释；2、注释是否与代码逻辑一致、是否恰当；
            6、异常捕捉及处理：1、是否需要捕捉异常而没有做；2、检查异常处理是否合理，避免过分泛化的捕捉；
            7、日志的完善度和合规性：1、日志是否包含足够的上下文信息，便于问题排查；2、检查日志级别的适当性（如 debug, info, warn, error）；3、确保不记录敏感信息，如密码和个人数据
            8、是否有安全方面的问题：是否存在安全漏洞
            9、是否有性能方面的问题：1、评估算法和数据结构选择对性能的影响；2、检查是否有不必要的数据库访问或网络通信，避免不必要的循环和重复计算能否改用批处理；3、评估资源使用和管理，如内存泄漏、文件句柄泄漏等；4、能否适当使用缓存机制和异步处理，以提高应用性能。
            10、其余方面（例如：1、可维护性；2、是否使用了设计模式和最佳实践以提高代码重用；3、如果是方法，是否易于使用；4、是否可以复用已有的公开类库代替；5、代码是否易读；6、代码结构是否满足“高内聚、松耦合”）
            """;


    public static String BACK_END_DEV_STD_PROMPT = """
            评分标准(总分100分):
                1. 命名规范 (10分)
                2. 空指针风险 (10分)
                3. 数组与集合的边界风险 (10分)
                4. 并发控制 (10分)
                5. 注释完整性 (10分)
                6. 异常处理 (10分)
                7. 日志的完善度与合规性 (10分)
                8. 安全性 (10分)
                9. 性能优化 (10分)
                10. 代码质量的其他方面 (10分)
            1. 命名规范
                1.1 是否遵循当前编程语言的命名惯例（如驼峰命名、类名大写开头等）。
                1.2 命名是否清晰、描述性强，能够直观反映功能或用途。
                1.3 是否避免过于简短、泛化的命名。
                1.4 检查是否存在歧义或误导性的命名。
            2. 空指针风险
                2.1 是否对可能为空的对象进行了必要的检查。
                2.2 是否正确有效的使用语言特性来处理空值。
                2.3 确保代码既不遗漏空值检查，也不过度检查而影响代码简洁性。
            3. 数组与集合的边界风险
                3.1 是否在数组或集合操作中进行了适当的边界检查。
                3.2 是否存在可能导致数组溢出的逻辑漏洞。
                3.3 是否正确处理空集合或空数组的特殊情况。
            4. 并发控制
                4.1 代码是否可以以多线程的方式运行，是否有进一步优化的空间（如通过并行处理提高效率）。
                4.2 根据使用场景，判断是否正确使用同步和锁机制，避免资源竞争问题。
                4.3 是否考虑并发操作的线程安全性，避免竞态条件。
                4.4 是否检查了可能的死锁风险。
            5. 注释完整性
                5.1 是否在关键代码段添加了清晰、准确的注释。
                5.2 注释内容是否与实际代码逻辑一致，避免误导。
                5.3 是否使用适量注释，避免过多或过少的注释影响阅读体验。
            6. 异常处理
                6.1 是否捕获了必要的异常，避免遗漏可能导致程序崩溃的情况。
                6.2 异常处理逻辑是否合理，避免使用过于泛化的捕获（如直接捕获 Exception）。
                6.3 是否对可能的异常情况进行了适当的日志记录或错误提示。
            7. 日志的完善度与合规性
                7.1 是否记录了足够的上下文信息（如操作数据、调用栈信息）以便于问题排查。
                7.2 日志级别是否合理，避免无意义的 DEBUG 或过度使用 ERROR。
                7.3 是否确保不记录敏感信息（如密码、个人数据）以避免安全问题。
            8. 安全性
                8.1 是否存在潜在的安全漏洞（如 SQL 注入、XSS、敏感数据泄露）。
                8.2 是否正确使用加密机制（如加密存储密码）。
                8.3 是否避免了可能导致权限绕过或数据篡改的逻辑漏洞。
            9. 性能优化
                9.1 是否选择了合适的算法和数据结构，避免低效操作。
                9.2 是否避免了冗余的数据库访问、网络通信或循环计算。
                9.3 是否考虑资源管理问题（如内存泄漏、文件句柄泄漏）。
                9.4 是否使用了合适的性能优化机制（如缓存、异步处理、批处理）。
            10. 代码质量的其他方面
                10.1 可维护性：代码是否易于理解和修改，是否有良好的模块划分。
                10.2 设计模式：是否应用了适当的设计模式以提高代码可重用性和扩展性。
                10.3 方法易用性：方法是否易于调用，是否避免了复杂的调用流程。
                10.4 重用公开类库：是否复用已有的成熟库而非重复造轮子。
                10.5 代码可读性：是否具有清晰的逻辑，避免过长的类或方法。
                10.6 结构设计：代码结构是否遵循 “高内聚、低耦合” 的原则。
                        """;


    public static final String SCORE_AI_PROMPT = """
            你是一位经验丰富的计算机科学家和数据专家，同时也是具备深厚软件工程功底的资深架构师。在代码重构与系统优化领域，你有着超过 15 年的实战经验。你以严谨和务实的作风著称，擅长从架构设计、性能优化、安全性、可扩展性、可维护性等多个维度对代码进行全面审查和精准评估。
            你对代码质量有着极高的标准，但始终保持客观公正的评判态度。你能够准确识别代码中的各类问题，包括但不限于：系统性能瓶颈、潜在的安全漏洞、架构设计缺陷、代码可读性问题、测试覆盖不足、模块解耦不够、以及可能影响系统稳定性和可扩展性的技术债务等。在评估过程中，你坚持实事求是的原则，既不会过分吹毛求疵，指出不存在的问题，也不会因疏忽而遗漏任何一处潜在缺陷。
            你的代码评审不仅仅停留在问题指出层面，还会结合实际场景和业务需求，提供切实可行的改进建议和最佳实践方案。
            """;
    public static String getLanguageByFileType(String fileType){
        return extensionToLanguageMap.get(fileType);
    }



}
