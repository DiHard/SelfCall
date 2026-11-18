package ru.ifx.selfcall.data

import ru.ifx.selfcall.BuildConfig

/** Ссылки на встречи: https://<домен>/m/abc-defg-hjk. Так же, как в веб-версии (frontend/src/lib.ts). */
object MeetingLinks {
    private val idRe = Regex("([a-z]{3})-?([a-z]{4})-?([a-z]{3})")
    private val pathRe = Regex("/m/([a-z-]+)")

    fun url(id: String) = "${BuildConfig.SERVER}/m/$id"

    /** Принимает полную ссылку, путь или голый код (с дефисами или без). */
    fun parse(input: String): String? {
        val text = input.trim().lowercase()
        val fromPath = pathRe.find(text)?.groupValues?.get(1)
        val m = idRe.find(fromPath ?: text) ?: return null
        val (a, b, c) = m.destructured
        return "$a-$b-$c"
    }
}
