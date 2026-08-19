plugins {
    java
    id("java-gradle-plugin")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.kyori:adventure-api:5.2.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.palantir.javapoet:javapoet:0.18.0")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}


gradlePlugin {
    plugins {
        create("fidorialRegistryGenerator") {
            id = "fr.fidorial.registry-generator"
            implementationClass = "fr.fidorial.registrygen.FidorialRegistryGeneratorPlugin"
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

val blockLightPropertiesAgent = tasks.register<Jar>("buildBlockLightPropertiesAgent") {
    group = "build"
    description = "Packages the BlockLightPropertiesAgent as a standalone javaagent jar."

    archiveClassifier.set("block-light-properties-agent")

    from(sourceSets.main.map { it.output.classesDirs }) {
        include("fr/fidorial/registrygen/agent/**")
    }

    manifest {
        attributes(
            "Premain-Class" to "fr.fidorial.registrygen.agent.BlockLightPropertiesAgent",
            "Can-Redefine-Classes" to "false",
            "Can-Retransform-Classes" to "false"
        )
    }
}

tasks.named<ProcessResources>("processResources") {
    from(blockLightPropertiesAgent) {
        into("fr/fidorial/registrygen/agent")
        rename { "block-light-properties-agent.jar" }
    }
}
