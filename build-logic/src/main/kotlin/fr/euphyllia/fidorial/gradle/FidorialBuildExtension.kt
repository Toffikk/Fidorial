package fr.euphyllia.fidorial.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Nested
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.setProperty
import javax.inject.Inject
import kotlin.collections.emptySet

abstract class FidorialBuildExtension @Inject constructor(objects: ObjectFactory) {
    /**
     * The modules that are allowed to read unnamed modules.
     */
    val readUnnamedModules: SetProperty<String> = objects.setProperty<String>().convention(emptySet())

    /**
     * Configuration for signing jars using JarSigner.
     */
    @get:Nested
    val jarSigning: JarSigningConfig = objects.newInstance(JarSigningConfig::class)

    fun jarSigning(action: JarSigningConfig.() -> Unit) = jarSigning.action()

    abstract class JarSigningConfig {
        abstract val keyStore: Property<String>
        abstract val keyStorePassword: Property<String>
        abstract val alias: Property<String>
    }
}
