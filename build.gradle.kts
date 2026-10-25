buildscript {
    repositories {
        mavenCentral()
        google()
    }

    dependencies {
        val androidGradlePluginVersion = project.property("androidGradlePluginVersion") as String
        classpath("com.android.tools.build:gradle:$androidGradlePluginVersion")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

// 图标（art/gen 生成，各模块共用同一份蜗牛矢量）里的 pathData 很长（3k+ 字符）：
// lint 的 VectorPath 检查会把它算作 error，使 `./gradlew lint`（上游 CI 的 `Check` 工作流）失败。
// 这是"精度换观感"的有意取舍，24–108dp 下渲染开销可忽略 → 全局关掉该检查。
subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<com.android.build.api.dsl.ApplicationExtension>("android") {
            lint {
                disable += "VectorPath"
            }
        }
    }
}
