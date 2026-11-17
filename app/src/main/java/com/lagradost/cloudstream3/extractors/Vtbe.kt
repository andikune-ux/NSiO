package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import java.net.URI

open class Vtbe : ExtractorApi() {
    override var name = "Vtbe"
    override var mainUrl = "https://vtbe.to"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url, referer = mainUrl).document
        val extractedPack = response
            .selectFirst("script:containsData(function(p,a,c,k,e,d))")
            ?.data()
            .orEmpty()

        JsUnpacker(extractedPack).unpack()?.let { unpacked ->
            Regex("sources:\\[\\{file:\\\"(.*?)\\\"")
                .find(unpacked)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { link ->
                    return listOf(
                        ExtractorLink(
                            this.name,
                            this.name,
                            link,
                            referer.orEmpty(),
                            Qualities.Unknown.value,
                            URI(link).path.endsWith(".m3u8")
                        )
                    )
                }
        }
        return null
    }
}
