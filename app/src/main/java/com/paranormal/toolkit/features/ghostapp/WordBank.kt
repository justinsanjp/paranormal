package com.paranormal.toolkit.features.ghostapp

import com.paranormal.toolkit.data.Language

/**
 * The GhostApp's built-in vocabulary: plain generic words, nothing tailored
 * to a session.
 *
 * No sensor reading is ever used as an index or seed into these lists - see
 * [SensorTriggerEngine] / [GhostAppService]: sensors only decide *when* a
 * tick fires, a [java.security.SecureRandom] draw that has never seen any
 * sensor data decides *which* entry is spoken. That separation is the whole
 * point - it makes it impossible for a sensor value to steer word choice.
 */
object WordBank {

    val german = listOf(
        "ja", "nein", "hier", "dort", "geh", "komm", "hilfe", "warte", "stopp", "weiter",
        "licht", "dunkel", "kalt", "warm", "name", "zeit", "jetzt", "später", "tot", "leben",
        "geist", "seele", "haus", "zimmer", "tür", "fenster", "wand", "boden", "oben", "unten",
        "links", "rechts", "drinnen", "draußen", "allein", "zusammen", "freund", "feind",
        "familie", "mutter", "vater", "kind", "mann", "frau", "alt", "jung", "angst", "ruhe",
        "schmerz", "glücklich", "traurig", "wütend", "müde", "wach", "schlaf", "traum",
        "erinnerung", "vergessen", "wissen", "sehen", "hören", "fühlen", "sprechen", "schweigen",
        "laut", "leise", "nah", "fern", "schnell", "langsam", "richtig", "falsch", "gut", "böse",
        "hell", "schwarz", "weiß", "rot", "blau", "grün", "eins", "zwei", "drei", "vier", "fünf",
        "wasser", "feuer", "erde", "luft", "wind", "regen", "sonne", "mond", "sterne", "nacht",
        "tag", "morgen", "gestern", "heute", "immer", "nie", "vielleicht", "sicher", "gefahr",
        "frieden", "krieg", "liebe", "hass", "hoffnung", "wahrheit", "lüge", "frage", "antwort",
        "wer", "was", "wo", "wann", "warum", "wie", "bitte", "danke", "verzeihung", "willkommen",
        "abschied", "ende", "anfang", "weg", "straße", "wald", "berg", "tal", "fluss", "meer",
        "himmel", "hölle", "engel", "dämon", "schatten", "stimme", "zuhören", "bleib", "verlass",
        "berühr", "glaube", "zweifel", "echt", "energie", "anwesenheit",
    ).distinct()

    val english = listOf(
        "yes", "no", "here", "there", "go", "come", "help", "wait", "stop", "more",
        "light", "dark", "cold", "warm", "name", "time", "now", "later", "dead", "alive",
        "ghost", "soul", "house", "room", "door", "window", "wall", "floor", "up", "down",
        "left", "right", "inside", "outside", "alone", "together", "friend", "enemy",
        "family", "mother", "father", "child", "man", "woman", "old", "young", "fear", "calm",
        "pain", "happy", "sad", "angry", "tired", "awake", "sleep", "dream",
        "memory", "forget", "know", "see", "hear", "feel", "speak", "silence",
        "loud", "quiet", "near", "far", "fast", "slow", "correct", "wrong", "good", "evil",
        "bright", "black", "white", "red", "blue", "green", "one", "two", "three", "four", "five",
        "water", "fire", "earth", "air", "wind", "rain", "sun", "moon", "stars", "night",
        "day", "tomorrow", "yesterday", "today", "always", "never", "maybe", "safe", "danger",
        "peace", "war", "love", "hate", "hope", "truth", "lie", "question", "answer",
        "who", "what", "where", "when", "why", "how", "please", "thanks", "sorry", "welcome",
        "goodbye", "end", "beginning", "path", "road", "forest", "mountain", "valley", "river", "sea",
        "sky", "hell", "angel", "demon", "shadow", "voice", "listen", "stay", "leave",
        "touch", "believe", "doubt", "real", "energy", "presence",
    ).distinct()

    fun forLanguage(language: Language): List<String> =
        if (language == Language.DE) german else english
}
