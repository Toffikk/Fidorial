import fr.euphyllia.fidorial.gradle.FidorialBuildExtension
import fr.euphyllia.fidorial.gradle.signing.SignJarTask

plugins {
    `java-library`
    signing
}

val fidorialBuild = extensions.create<FidorialBuildExtension>("fidorialBuild")

repositories {
    mavenCentral()
    maven("https://libraries.minecraft.net")
    maven("https://repo.papermc.io/repository/maven-public/")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = Charsets.UTF_8.name()
    options.isFork = true
    options.release = 25

    fidorialBuild.readUnnamedModules.get().forEach {
        options.compilerArgs.addAll(
            listOf(
                "--add-reads",
                "$it=ALL-UNNAMED"
            )
        )
    }
}

tasks.withType<ProcessResources>().configureEach {
    filteringCharset = Charsets.UTF_8.name()
}

tasks.withType<Test>().configureEach {
    fidorialBuild.readUnnamedModules.get().forEach {
        jvmArgs("--add-reads", "$it=ALL-UNNAMED")
    }
}

tasks.withType<JavaExec>().configureEach {
    fidorialBuild.readUnnamedModules.get().forEach {
        jvmArgs("--add-reads", "$it=ALL-UNNAMED")
    }
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = Charsets.UTF_8.name()

    val options = options as StandardJavadocDocletOptions
    fidorialBuild.readUnnamedModules.get().forEach {
        options.addStringOption(
            "-add-reads",
            "$it=ALL-UNNAMED"
        )
    }
}

signing {
    val signingKey = providers.environmentVariable("SIGNING_KEY")
    val signingPassword = providers.environmentVariable("SIGNING_PASSWORD")

    useInMemoryPgpKeys(
        signingKey.orNull,
        signingPassword.orNull,
    )
}

fidorialBuild.jarSigning {
    keyStore.convention(providers.environmentVariable("SIGNING_KEYSTORE"))
    keyStorePassword.convention(providers.environmentVariable("SIGNING_KEYSTORE_PASSWORD"))
    alias.convention(providers.environmentVariable("SIGNING_KEYSTORE_ALIAS"))
}

val signJar = tasks.register<SignJarTask>("signJar") {
    group = "signing"
    description = "Signs a jar using JarSigner"
    enabled = false

    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))

    keyStore.set(fidorialBuild.jarSigning.keyStore)
    keyStorePassword.set(fidorialBuild.jarSigning.keyStorePassword)
    alias.set(fidorialBuild.jarSigning.alias)
}

tasks.assemble {
    dependsOn(signJar)
}
