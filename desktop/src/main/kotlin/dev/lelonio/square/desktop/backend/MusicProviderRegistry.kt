package dev.lelonio.square.desktop.backend

import dev.lelonio.square.desktop.model.MusicProviderId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the providers available in this desktop process and the selected source.
 *
 * Registration and selection are deliberately explicit: no provider is marked
 * usable until a real desktop adapter has been created and registered.
 */
class MusicProviderRegistry {
    private val lock = Any()
    private val providers = linkedMapOf<MusicProviderId, MusicProvider>()

    private val mutableSelectedProviderId = MutableStateFlow<MusicProviderId?>(null)
    val selectedProviderId: StateFlow<MusicProviderId?> =
        mutableSelectedProviderId.asStateFlow()

    val registeredProviders: List<MusicProvider>
        get() = synchronized(lock) { providers.values.toList() }

    val selectedProvider: MusicProvider?
        get() = synchronized(lock) {
            mutableSelectedProviderId.value?.let(providers::get)
        }

    fun register(provider: MusicProvider) {
        synchronized(lock) {
            require(provider.id !in providers) {
                "A provider for ${provider.id} is already registered"
            }
            providers[provider.id] = provider
        }
    }

    fun provider(id: MusicProviderId): MusicProvider? =
        synchronized(lock) { providers[id] }

    fun select(id: MusicProviderId) {
        synchronized(lock) {
            require(id in providers) { "Provider $id has not been registered" }
            mutableSelectedProviderId.value = id
        }
    }

    fun clearSelection() {
        mutableSelectedProviderId.value = null
    }

    fun unregister(id: MusicProviderId): MusicProvider? =
        synchronized(lock) {
            val removed = providers.remove(id)
            if (mutableSelectedProviderId.value == id) {
                mutableSelectedProviderId.value = null
            }
            removed
        }
}
