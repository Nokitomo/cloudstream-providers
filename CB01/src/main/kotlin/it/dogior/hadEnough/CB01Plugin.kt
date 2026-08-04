package it.dogior.hadEnough

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import it.dogior.hadEnough.shared.PastebinDomainResolver

@CloudstreamPlugin
class CB01Plugin: Plugin() {
    override fun load(context: Context) {
        val remoteDomainPreferences = context.getSharedPreferences(
            PastebinDomainResolver.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        registerMainAPI(CB01(remoteDomainPreferences))
    }
}
