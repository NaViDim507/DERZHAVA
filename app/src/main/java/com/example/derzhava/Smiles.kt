package com.example.derzhava

/**
 * Простая адаптация смайлов der1: коды вида .koroleva. → эмодзи.
 * Набор можно дополнять по вкусу.
 */
object Smiles {

    data class Smile(val code: String, val emoji: String)

    // Базовый набор «державных» смайликов
    val all: List<Smile> = listOf(
        Smile(".bobr.", "🦫"),
        Smile(".smotri.", "👀"),
        Smile(".spok.", "😌"),
        Smile(".koroleva.", "👑"),
        Smile(".cezar.", "🤴"),
        Smile(".russ.", "🪆"),
        Smile(".ulib.", "😃"),
        Smile(".innah.", "😊"),

        // из dobavka/smiles.php
        Smile(".angel.", "😇"),
        Smile(".angry.", "😠"),
        Smile(".bad.", "😡"),
        Smile(".beee.", "😝"),
        Smile(".lol.", "😂"),
        Smile(".cry.", "😢"),
        Smile(".wink.", "😉"),
        Smile(".kiss.", "😘")
    )

    private val map: Map<String, String> = all.associate { it.code to it.emoji }

    /** Подменяем текстовые коды на эмодзи при показе сообщения */
    fun applyTo(text: String): CharSequence {
        var res = text
        map.forEach { (code, emoji) ->
            if (res.contains(code)) {
                res = res.replace(code, emoji)
            }
        }
        return res
    }
}
