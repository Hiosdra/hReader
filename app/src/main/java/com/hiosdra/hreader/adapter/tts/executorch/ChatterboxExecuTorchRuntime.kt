package com.hiosdra.hreader.adapter.tts.executorch

import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File

internal class ChatterboxExecuTorchRuntime : AutoCloseable {
    private val lock = Any()
    private var loadedRoot: File? = null
    private var loadedThreads: Int? = null
    private var loadedModules: Map<ChatterboxExecuTorchModule, Module> = emptyMap()

    fun load(root: File, numThreads: Int) = synchronized(lock) {
        require(numThreads > 0)
        val normalizedRoot = root.absoluteFile
        if (
            loadedRoot == normalizedRoot &&
            loadedThreads == numThreads &&
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
                    File(normalizedRoot, module.fileName).absolutePath,
                    Module.LOAD_MODE_MMAP,
                    numThreads
                )
            }
        } catch (error: Throwable) {
            destroy(newlyLoadedModules.values)
            throw error
        }

        destroy(loadedModules.values)
        loadedRoot = normalizedRoot
        loadedThreads = numThreads
        loadedModules = newlyLoadedModules
    }

    fun <T> withModules(block: (Map<ChatterboxExecuTorchModule, Module>) -> T): T =
        synchronized(lock) {
            check(isLoaded()) { "Chatterbox ExecuTorch runtime is not loaded" }
            block(loadedModules)
        }

    fun forward(module: Module, vararg inputs: Tensor): Array<Tensor> {
        val values = inputs.map(EValue::from).toTypedArray()
        return module.forward(*values).map(EValue::toTensor).toTypedArray()
    }

    fun isLoaded(): Boolean = synchronized(lock) {
        loadedModules.size == ChatterboxExecuTorchModel.requiredModules.size
    }

    override fun close() = synchronized(lock) {
        destroy(loadedModules.values)
        loadedRoot = null
        loadedThreads = null
        loadedModules = emptyMap()
    }

    private fun destroy(modules: Collection<Module>) {
        modules.forEach { module -> runCatching(module::destroy) }
    }
}
