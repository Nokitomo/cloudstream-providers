package it.dogior.hadEnough

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.CommonActivity.activity
import it.dogior.hadEnough.shared.PastebinDomainResolver

@CloudstreamPlugin
class AnimeWorldPlugin : Plugin() {
    val sharedPref = activity?.getSharedPreferences("AnimeWorldIT", Context.MODE_PRIVATE)

    override fun load(context: Context) {
        val remoteDomainPreferences = context.getSharedPreferences(
            PastebinDomainResolver.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val isSplit = sharedPref?.getBoolean("isSplit", false) ?: false
        val dubEnabled = sharedPref?.getBoolean("dubEnabled", false) ?: false
        val subEnabled = sharedPref?.getBoolean("subEnabled", false) ?: false
        // All providers should be added in this manner. Please don't edit the providers list directly.
        if (isSplit) {
            if (dubEnabled) {
                registerMainAPI(AnimeWorldDub(isSplit, remoteDomainPreferences))
            }
            if (subEnabled) {
                registerMainAPI(AnimeWorldSub(isSplit, remoteDomainPreferences))
            }
        } else {
            registerMainAPI(AnimeWorldCore(isSplit, remoteDomainPreferences = remoteDomainPreferences))
        }

        openSettings = { ctx ->
            val activity = ctx as AppCompatActivity
            val frag = Settings(this)
            frag.show(activity.supportFragmentManager, "Frag")
        }
    }
}
