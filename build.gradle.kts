plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.8.21"
    id("org.jetbrains.intellij.platform") version "2.5.0"
//   id("org.jetbrains.intellij.platform.migration") version "2.5.0"
}

group = "com.wmsay"
version = "3.9.1"

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
    implementation("org.mozilla:rhino:1.7.15")
    implementation("org.jsoup:jsoup:1.17.2")
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
//    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
//        kotlinOptions.jvmTarget = "17"
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
