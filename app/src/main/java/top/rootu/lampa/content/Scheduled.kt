package top.rootu.lampa.content

import top.rootu.lampa.App
import top.rootu.lampa.helpers.Helpers.getJson
import top.rootu.lampa.helpers.Prefs.CUB
import top.rootu.lampa.helpers.Prefs.FAV
import top.rootu.lampa.helpers.Prefs.schdToRemove
import top.rootu.lampa.helpers.Prefs.syncEnabled
import top.rootu.lampa.models.LampaCard

class Scheduled : LampaProviderI() {

    override fun get(): ReleaseID {
        return ReleaseID(Scheduled.get())
    }

    companion object {
        fun get(): List<LampaCard> {
            val lst = mutableListOf<LampaCard>()
            // CUB
            if (App.context.syncEnabled)
                App.context.CUB
                    ?.filter { it.type == LampaProvider.SCHD }
                    ?.reversed() // Reverse the order of the filtered list
                    ?.forEach { bm ->
                    bm.data?.let {
                        it.fixCard()
                        lst.add(it)
                    }
                }
            // FAV (use ID to match KP_573840 etc)
            App.context.FAV?.card?.filter { App.context.FAV?.scheduled?.contains(it.id.toString()) == true }
                ?.forEach { lst.add(it) }
            // exclude pending
            return lst.filter { !App.context.schdToRemove.contains(it.id.toString()) }
                .reversed() // Reverse the final list if needed
        }
    }
}