package dev.lelonio.square.desktop.backend

import dev.lelonio.square.desktop.model.MusicProviderId
import dev.lelonio.square.desktop.model.MusicSearchPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class MusicProviderRegistryTest {
    @Test
    fun registersAndSelectsProvider() {
        val registry = MusicProviderRegistry()
        val provider = FakeMusicProvider(MusicProviderId.SPOTIFY)

        registry.register(provider)
        registry.select(MusicProviderId.SPOTIFY)

        assertEquals(MusicProviderId.SPOTIFY, registry.selectedProviderId.value)
        assertSame(provider, registry.selectedProvider)
        assertSame(provider, registry.provider(MusicProviderId.SPOTIFY))
    }

    @Test
    fun rejectsDuplicateProviderIds() {
        val registry = MusicProviderRegistry()
        registry.register(FakeMusicProvider(MusicProviderId.SPOTIFY))

        assertFailsWith<IllegalArgumentException> {
            registry.register(FakeMusicProvider(MusicProviderId.SPOTIFY))
        }
    }

    @Test
    fun rejectsSelectingAnUnregisteredProvider() {
        val registry = MusicProviderRegistry()

        assertFailsWith<IllegalArgumentException> {
            registry.select(MusicProviderId.YOUTUBE_MUSIC)
        }
        assertNull(registry.selectedProviderId.value)
    }

    @Test
    fun unregisteringSelectedProviderClearsSelection() {
        val registry = MusicProviderRegistry()
        val provider = FakeMusicProvider(MusicProviderId.YOUTUBE_MUSIC)
        registry.register(provider)
        registry.select(provider.id)

        assertSame(provider, registry.unregister(provider.id))
        assertNull(registry.selectedProviderId.value)
        assertNull(registry.selectedProvider)
    }

    private class FakeMusicProvider(
        override val id: MusicProviderId,
    ) : MusicProvider {
        override val authState = MutableStateFlow<ProviderAuthState>(ProviderAuthState.SignedOut)

        override suspend fun signIn() {
            authState.value = ProviderAuthState.SignedIn("Test account")
        }

        override suspend fun signOut() {
            authState.value = ProviderAuthState.SignedOut
        }

        override suspend fun search(query: String, pageToken: String?) = MusicSearchPage()
    }
}
