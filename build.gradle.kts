plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.8.21"
    id("org.jetbrains.intellij.platform") version "2.5.0"
//   id("org.jetbrains.intellij.platform.migration") version "2.5.0"
}

group = "com.wmsay"
version = "4.1.6"

repositories {
    mavenCentral()
    maven("https://maven.aliyun.com/nexus/content/repositories/central/")
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.alibaba:fastjson:1.2.83")
    implementation("com.vladsch.flexmark:flexmark:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-util-html:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-tables:0.64.8")
    implementation("org.mozilla:rhino:1.7.15")
    implementation("org.jsoup:jsoup:1.17.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("net.jqwik:jqwik:1.8.5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    intellijPlatform {
        create("IC", "2025.1")
        intellijIdeaCommunity("2025.1")
        bundledPlugins("com.intellij.java","Git4Idea")
    }
}
tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    test {
        useJUnitPlatform()
    }

//    // 跳过 instrumentCode — 本地 JDK 路径不含 Packages 目录会导致此任务失败
//    named("instrumentCode") { enabled = false }

//    // 冻结防御已通过 getInputMethodRequests() 覆写解决，无需修改渲染属性
//    runIde {
//        jvmArgs(
//            "-Djb.consoleLog=true"
//        )
//    }

    patchPluginXml {
        sinceBuild.set("222")
        untilBuild.set("253.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
