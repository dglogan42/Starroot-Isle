package com.starrootisle.app.game

data class CutscenePage(
    val speaker: String,
    val text: String,
    val mood: Mood = Mood.SOFT,
) {
    enum class Mood { SOFT, WONDER, WARM, NIGHT, FINALE }
}

data class Cutscene(
    val id: String,
    val title: String,
    val pages: List<CutscenePage>,
)

object Cutscenes {
    val INTRO = Cutscene(
        id = "intro",
        title = "Washed Ashore",
        pages = listOf(
            CutscenePage("…", "Salt air. Soft grass. A sky full of quiet stars."),
            CutscenePage(
                "Wind",
                "Welcome, settler. This is Starroot Isle — tired, but not forgotten.",
                CutscenePage.Mood.WONDER,
            ),
            CutscenePage(
                "Wind",
                "Tend the soil. Be kind to the soft-footed ones. Listen for the Heartseed.",
                CutscenePage.Mood.SOFT,
            ),
            CutscenePage(
                "You",
                "A tent and workbench wait nearby. Day one begins.",
                CutscenePage.Mood.WARM,
            ),
        ),
    )

    private val byQuest: Map<QuestId, Cutscene> = mapOf(
        QuestId.WAKE_ASHOR to Cutscene(
            id = "q_wake",
            title = "First Night",
            pages = listOf(
                CutscenePage("Tent flap", "You sleep as the starroot crown dims to a heartbeat."),
                CutscenePage(
                    "Dream",
                    "\"Tend the soil. The Heartseed waits.\"",
                    CutscenePage.Mood.NIGHT,
                ),
                CutscenePage("You", "Morning. Energy full. The meadow listens."),
            ),
        ),
        QuestId.FIRST_TILL to Cutscene(
            id = "q_till",
            title = "Broken Ground",
            pages = listOf(
                CutscenePage("Soil", "Dark earth remembers rain."),
                CutscenePage(
                    "Wind",
                    "Something under the meadow stirs — slow as roots.",
                    CutscenePage.Mood.WONDER,
                ),
            ),
        ),
        QuestId.FIRST_HARVEST to Cutscene(
            id = "q_harvest",
            title = "First Light Crop",
            pages = listOf(
                CutscenePage("Crop", "Your harvest glows faintly in your palms."),
                CutscenePage(
                    "Isle",
                    "Kindness noticed. Paths will open for the brave.",
                    CutscenePage.Mood.WARM,
                ),
            ),
        ),
        QuestId.MEET_PUFFKIN to Cutscene(
            id = "q_puff",
            title = "Soft Footfalls",
            pages = listOf(
                CutscenePage("Puffkin", "Chirp. (It invents a name for you.)"),
                CutscenePage(
                    "You",
                    "A warm weight at your heel. You are less alone.",
                    CutscenePage.Mood.WARM,
                ),
            ),
        ),
        QuestId.DEEPWOOD_VISIT to Cutscene(
            id = "q_deep",
            title = "Whispering Canopy",
            pages = listOf(
                CutscenePage("Deepwood", "Logs hum like cello strings."),
                CutscenePage(
                    "Roots",
                    "The Heartseed’s veins run through this shade.",
                    CutscenePage.Mood.NIGHT,
                ),
            ),
        ),
        QuestId.CRYSTAL_VISIT to Cutscene(
            id = "q_crystal",
            title = "Hollow Lights",
            pages = listOf(
                CutscenePage("Crystal", "Pale fireflies scatter in the hollows."),
                CutscenePage("Shard", "One facet points east of home.", CutscenePage.Mood.WONDER),
            ),
        ),
        QuestId.EMBER_VISIT to Cutscene(
            id = "q_ember",
            title = "Warm Tide",
            pages = listOf(
                CutscenePage("Shore", "Pepper, salt, and cooling ash."),
                CutscenePage("Tide", "Something old rests under the ember sand.", CutscenePage.Mood.WARM),
            ),
        ),
        QuestId.SHORE_CATCH to Cutscene(
            id = "q_fish",
            title = "Silver Letter",
            pages = listOf(
                CutscenePage("Silverfin", "In its eye — a flicker of map-light."),
                CutscenePage(
                    "Sea",
                    "Messages travel between shores. You are part of the current now.",
                    CutscenePage.Mood.WONDER,
                ),
            ),
        ),
        QuestId.STAR_ORE_HUNT to Cutscene(
            id = "q_ore",
            title = "Sky Metal",
            pages = listOf(
                CutscenePage("Star Ore", "Three notes ring: meadow, hollow, shore."),
                CutscenePage("You", "The metal hums against your pack.", CutscenePage.Mood.WONDER),
            ),
        ),
        QuestId.STARROOT_HEART to Cutscene(
            id = "q_finale",
            title = "Heartseed Awakens",
            pages = listOf(
                CutscenePage(
                    "Starroot",
                    "The crown brightens until the whole island inhales.",
                    CutscenePage.Mood.FINALE,
                ),
                CutscenePage(
                    "Heartseed",
                    "Thank you, settler. Grow, wander, welcome friends.",
                    CutscenePage.Mood.FINALE,
                ),
                CutscenePage(
                    "Isle",
                    "The story rests — for now. Your days continue under kinder stars.",
                    CutscenePage.Mood.WARM,
                ),
            ),
        ),
    )

    fun forQuest(id: QuestId): Cutscene? = byQuest[id]

    fun forClaim(id: QuestId): Cutscene? = byQuest[id]
}

/** Simple page-turner driven by UI. */
class CutscenePlayer {
    var active: Cutscene? = null
        private set
    var pageIndex: Int = 0
        private set
    private val queue = ArrayDeque<Cutscene>()
    var seenIds = mutableSetOf<String>()

    val isPlaying: Boolean get() = active != null

    val currentPage: CutscenePage?
        get() = active?.pages?.getOrNull(pageIndex)

    fun enqueue(scene: Cutscene, force: Boolean = false) {
        if (!force && scene.id in seenIds) return
        if (active?.id == scene.id) return
        if (queue.any { it.id == scene.id }) return
        if (active == null) {
            start(scene)
        } else {
            queue.addLast(scene)
        }
    }

    private fun start(scene: Cutscene) {
        active = scene
        pageIndex = 0
        seenIds.add(scene.id)
    }

    /** Returns true if still playing after advance. */
    fun advance(): Boolean {
        val scene = active ?: return false
        if (pageIndex < scene.pages.lastIndex) {
            pageIndex++
            return true
        }
        active = null
        pageIndex = 0
        val next = queue.removeFirstOrNull()
        if (next != null) {
            start(next)
            return true
        }
        return false
    }

    fun skipAll() {
        active = null
        pageIndex = 0
        queue.clear()
    }
}
