package com.hiosdra.hreader.adapter.tts.executorch

import org.pytorch.executorch.Module
import java.io.File

internal class ChatterboxExecuTorchRuntime : AutoCloseable {
    private val lock = Any()
    private var loadedRoot: File? = null
    private var loadedModules: Map<ChatterboxExecuTorchModule, Module> = emptyMap()

    fun load(root: File) = synchronized(lock) {
        val normalizedRoot = root.absoluteFile
        if (
            loadedRoot == normalizedRoot &&
            loadedModules.size == ChatterboxExecuTorchModel.requiredModules.size
        ) {
            return@synchronized
        }

        val missingFiles = ChatterboxExecuTorchModel.missingFiles(normalizedRoot)
        check(missingFiles.isEmpty()) {
            "Chatterbox ExecuTorch model is missing: ${missingFiles.joinToString()}"
        }

        val newlyLoadedModules = linkedMapOf<ChatterboxExecuTorchModule, Module>()
        try {
            ChatterboxExecuTorchModel.requiredModules.forEach { module ->
                newlyLoadedModules[module] = Module.load(
                    File(normalizedRoot, module.fileName).absolutePath
                )
            }
        } catch (error: Throwable) {
            destroy(newlyLoadedModules.values)
            throw error
        }

        destroy(loadedModules.values)
        loadedRoot = normalizedRoot
        loadedModules = newlyLoadedModules
    }

    fun <T> withModules(block: (Map<ChatterboxExecuTorchModule, Module>) -> T): T = synchronized(lock) {
        check(isLoaded()) { "Chatterbox ExecuTorch runtime is not loaded" }
        block(loadedModules)
    }

    fun isLoaded(): Boolean = synchronized(lock) {
        loadedModules.size == ChatterboxExecuTorchModel.requiredModules.size
    }

    override fun close() = synchronized(lock) {
        destroy(loadedModules.values)
        loadedRoot = null
        loadedModules = emptyMap()
    }

    private fun destroy(modules: Collection<Module>) {
        modules.forEach { module -> runCatching(module::destroy) }
    }
}
