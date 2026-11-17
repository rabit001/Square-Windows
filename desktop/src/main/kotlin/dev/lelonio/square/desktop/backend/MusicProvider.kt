package dev.lelonio.square.desktop.backend

import dev.lelonio.square.desktop.model.MusicDetail
import dev.lelonio.square.desktop.model.MusicProviderId
import dev.lelonio.square.desktop.model.MusicSearchItem
import dev.lelonio.square.desktop.model.MusicSearchPage
import kotlinx.coroutines.flow.StateFlow

/** Connection state exposed to the desktop UI without Android account classes. */
sealed interface ProviderAuthState {
    data object SignedOut : ProviderAuthState
    data object SigningIn : ProviderAuthState
    data class SignedIn(val displayName: String? = null) : ProviderAuthState
    data class Failed(val message: String) : ProviderAuthState
}

/**
 * Desktop-facing catalogue and account contract.
 *
 * Implementations must use desktop-compatible networking and secure storage.
 * Android Context, Media3 Player, Activity and Android storage types must not
 * cross this boundary.
 */
interface MusicProvider {
    val id: MusicProviderId
    val authState: StateFlow<ProviderAuthState>

    suspend fun signIn()
    suspend fun signOut()

    /** [pageToken] is opaque and must only be interpreted by its provider. */
    suspend fun search(query: String, pageToken: String? = null): MusicSearchPage

    /** Open a catalogue result using the provider's native browse endpoint. */
    suspend fun browse(item: MusicSearchItem): MusicDetail

    /** Fetch the next page of a browsable result, if its provider exposes one. */
    suspend fun browseMore(detail: MusicDetail): MusicDetail
}
