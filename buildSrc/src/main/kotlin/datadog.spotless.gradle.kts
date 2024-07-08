plugins {
  id("com.diffplug.spotless")
}

// This definition is needed since the spotless file is used from stand alone projects
val configPath: String = rootProject.properties.getOrDefault("sharedConfigDirectory", project.rootProject.rootDir.path + "/gradle") as String
// This is necessary for some projects that set a special groovy target which can't coexist with excludeJava
val groovySkipJavaExclude: Boolean = project.properties.getOrDefault("groovySkipJavaExclude", false) as Boolean

spotless {
  if (project.plugins.hasPlugin("java")) {
    java {
      toggleOffOn()
      // set explicit target to workaround https://github.com/diffplug/spotless/issues/1163
      target("src/**/*.java")
      // ignore embedded test projects
      targetExclude("src/test/resources/**")
      // This is the last Google Java Format version that supports Java 8
      googleJavaFormat("1.7")
    }
  }

  groovyGradle {
    toggleOffOn()
    // same as groovy, but for .gradle (defaults to '*.gradle')
    if (project == project.rootProject) {
      // only do this for the root project since the instrumentation project has a subproject and directory named gradle
      // that will confuse task dependencies
      target("*.gradle", "gradle/**/*.gradle")
    } else {
      target("*.gradle")
    }
    greclipse().configFile(configPath + "/enforcement/spotless-groovy.properties")
  }

  kotlin {
    toggleOffOn()
    // ktfmt('0.40').kotlinlangStyle() // needs Java 11+
    // Newer versions do not work well with the older version of kotlin in this build
    ktlint("0.41.0").userData(mapOf("indent_size" to "2", "continuation_indent_size" to "2"))
  }

  kotlinGradle {
    toggleOffOn()
    // same as kotlin, but for .gradle.kts files (defaults to '*.gradle.kts')
    target("*.gradle.kts")
    // ktfmt('0.40').kotlinlangStyle() // needs Java 11+
    // Newer versions do not work well with the older version of kotlin in this build
    ktlint("0.41.0").userData(mapOf("indent_size" to "2", "continuation_indent_size" to "2"))
  }

  if (project.plugins.hasPlugin("groovy")) {
    groovy {
      toggleOffOn()
      if (!groovySkipJavaExclude) {
        excludeJava() // excludes all Java sources within the Groovy source dirs from formatting
        // the Groovy Eclipse formatter extends the Java Eclipse formatter,
        // so it formats Java files by default (unless `excludeJava` is used).
      }
      greclipse().configFile(configPath + "/enforcement/spotless-groovy.properties")
    }
  }

  if (project.plugins.hasPlugin("scala")) {
    scala {
      toggleOffOn()
      scalafmt("2.7.5").configFile(configPath + "/enforcement/spotless-scalafmt.conf")
    }
  }

  format("markdown") {
    toggleOffOn()
    target("*.md", ".github/**/*.md", "src/**/*.md", "application/**/*.md")
    indentWithSpaces()
    endWithNewline()
  }

  format("misc") {
    toggleOffOn()
    target(".gitignore", "*.sh", "tooling/*.sh", ".circleci/*.sh")
    indentWithSpaces()
    trimTrailingWhitespace()
    endWithNewline()
  }
}

tasks.register("formatCode") {
  dependsOn("spotlessApply")
}
tasks.named("check").configure {
  dependsOn("spotlessCheck")
}
