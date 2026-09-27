// build-logic/src/main/kotlin/fr/euphyllia/fidorial/gradle/signing/SignJarTask.kt
package fr.euphyllia.fidorial.gradle.signing

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject

@CacheableTask
abstract class SignJarTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {

    @get:Internal
    abstract val keyStore: Property<String>

    @get:Input
    @get:Optional
    abstract val keyStoreFingerprint: Property<String>

    @get:Internal
    abstract val keyStorePassword: Property<String>

    @get:Input
    abstract val alias: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputJar: RegularFileProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @get:Internal
    abstract val javaLauncher: Property<JavaLauncher>

    init {
        keyStoreFingerprint.convention(keyStore.map { sha256(Base64.getDecoder().decode(it)) })
    }

    @TaskAction
    fun sign() {
        val input = inputJar.get().asFile
        val output = outputJar.get().asFile
        output.parentFile.mkdirs()
        input.copyTo(output, overwrite = true)

        val jarsigner = jarsignerExecutable()
        val keyStoreFile = writeKeyStoreTempFile()

        try {
            val command = buildList {
                add(jarsigner.absolutePath)
                add("-keystore"); add(keyStoreFile.absolutePath)
                add("-storetype"); add("PKCS12")
                add("-storepass:env"); add("SIGNING_PASSWORD")
                add("-keypass:env"); add("SIGNING_PASSWORD")
                add("-strict")
                add(output.absolutePath)
                add(alias.get())
            }

            val log = ByteArrayOutputStream()
            val result = execOperations.exec {
                commandLine(command)
                environment("SIGNING_PASSWORD", keyStorePassword.get())
                standardOutput = log
                errorOutput = log
                isIgnoreExitValue = true
            }

            if (result.exitValue != 0) {
                throw GradleException(
                    buildString {
                        appendLine("Failed to sign $input (alias '${alias.get()}')")
                        appendLine("jarsigner exited with code ${result.exitValue}")
                        val text = log.toString(Charsets.UTF_8)
                        if (text.isNotBlank()) {
                            appendLine()
                            append(text)
                        }
                    },
                )
            }

            logger.lifecycle("Signed ${output.name} (alias '${alias.get()}')")
        } finally {
            Files.deleteIfExists(keyStoreFile.toPath())
        }
    }

    private fun writeKeyStoreTempFile(): File {
        val bytes = Base64.getDecoder().decode(keyStore.get())
        val temp = Files.createTempFile("fidorial-signing-", ".p12")
        try {
            Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------"))
        } catch (_: UnsupportedOperationException) {
        }
        Files.write(temp, bytes)
        return temp.toFile()
    }

    private fun jarsignerExecutable(): File {
        val binary = if (System.getProperty("os.name").startsWith("Windows")) "jarsigner.exe" else "jarsigner"
        val jarsigner = javaLauncher.get().executablePath.asFile.parentFile.resolve(binary)

        if (!jarsigner.isFile) {
            throw GradleException("Could not find jarsigner next to the Java toolchain's java executable: $jarsigner")
        }
        return jarsigner
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
