package com.sha.orbis.ai.reply

import com.sha.orbis.ai.core.OrbisTokenizer
import com.sha.orbis.ai.core.OrbisVectorMath

/**
 * Pre-trained Semantic Intent Centroids and Multilingual Response Knowledge Base
 * for ORBIS Reply-LLM.
 * Supports French, English, Modern Standard Arabic, and Franco-Arabe / Darija.
 */
object OrbisReplyWeights {

    // Seed exemplar training phrases for semantic intent clustering
    private val GREETING_SEEDS = listOf(
        "Bonjour comment vas-tu ?",
        "Salut ça fait longtemps !",
        "Hello there how are you doing?",
        "Hey good morning!",
        "السلام عليكم ورحمة الله وبركاته",
        "مرحبا كيف الحال والأحوال ؟",
        "Salam labas 3lik ?",
        "Ahlan khoya kidayr ?"
    )

    private val HOW_ARE_YOU_SEEDS = listOf(
        "Ça va ? Tu as passé une bonne journée ?",
        "Comment tu te portes aujourd'hui ?",
        "How are you doing today? Everything good?",
        "How's life treating you?",
        "كيف حالك وكيف هي صحتك اليوم ؟",
        "كيف هي أحوالك إن شاء الله بخير ؟",
        "Kidayr labas 3lik kolchi mzyan ?",
        "Wach labas seha kolchi bikhir ?"
    )

    private val LOCATION_QUERY_SEEDS = listOf(
        "Tu es où là ? Je ne te vois pas.",
        "Où te trouves-tu en ce moment ?",
        "Where are you right now?",
        "Are you at home or outside?",
        "أين أنت الآن ؟ هل أنت في البيت ؟",
        "وينك يا صاحبي في أي مكان موجود ؟",
        "Fink daba wach f dar wla kharej ?",
        "Fein rak wselt wla mazal ?"
    )

    private val TIME_QUERY_SEEDS = listOf(
        "À quelle heure on se retrouve ?",
        "Vers quelle heure tu seras disponible ?",
        "What time are we meeting today?",
        "When will you be arriving?",
        "في أي ساعة نلتقي اليوم ؟",
        "كم الساعة المناسبة لك للموعد ؟",
        "F weqtach ghan tla9aw ?",
        "M3a chhal ghadi tji ?"
    )

    private val YES_NO_QUERY_SEEDS = listOf(
        "Tu viens ce soir avec nous ?",
        "Est-ce que tu es libre pour un café ?",
        "Are you coming tonight?",
        "Can you make it to the meeting?",
        "هل أنت قادم معنا الليلة ؟",
        "هل يمكنك الحضور والمشاركة ؟",
        "Wach jay m3ana lyoum ?",
        "Wach 9ad tji wla mchargi ?"
    )

    private val GRATITUDE_SEEDS = listOf(
        "Merci beaucoup pour ton aide précieuse !",
        "Je te remercie infiniment pour tout.",
        "Thank you so much, really appreciate it!",
        "Thanks a lot for your help!",
        "شكرا جزيلا لك على المساعدة الرائعة بارك الله فيك",
        "جزاك الله خيرا وأشكرك كثيرا",
        "Choukrane bzaf khoya lah yhafdak",
        "Barak laho fik chokran bzaf"
    )

    private val CONFIRMATION_SEEDS = listOf(
        "C'est bon, on fait comme ça alors.",
        "D'accord, marché conclu !",
        "All good, let's do that!",
        "Perfect, confirmed and noted!",
        "حسنا متفقين على هذا الأمر",
        "تمام تم تأكيد الموعد بكل سرور",
        "Safi wakha mtaf9in haka",
        "C'est bon mezyan bzaf"
    )

    private val APOLOGY_SEEDS = listOf(
        "Désolé pour le retard, j'étais coincé.",
        "Pardon pour le dérangement !",
        "Sorry for the delay, got stuck in traffic.",
        "My apologies for not replying earlier.",
        "أعتذر منك كثيرا عن التأخير غير المقصود",
        "سامحني لم أستطع الرد في الوقت المناسب",
        "Smehli bzaf 3la t3tal rani kount blocked",
        "Désolé khoya makountch cheft message"
    )

    private val URGENT_CALL_SEEDS = listOf(
        "Appelle-moi dès que tu peux c'est important.",
        "Rappelle-moi vite s'il te plaît !",
        "Please call me urgently when you get this.",
        "Give me a quick call ASAP.",
        "اتصل بي في أقرب وقت للأهمية القصوى",
        "عيطلي ضروري راني محتاجك دابا",
        "3ayet lia daba dghya lah ykhalik",
        "Tasel biya urgent f tilifoun"
    )

    private fun computeNormalizedCentroid(seeds: List<String>): FloatArray {
        val sum = FloatArray(64)
        for (seed in seeds) {
            val vec = OrbisTokenizer.encodeToEmbedding(seed)
            for (i in vec.indices) {
                sum[i] += vec[i]
            }
        }
        return OrbisVectorMath.normalizeL2(sum)
    }

    val GREETING_CENTROID: FloatArray by lazy { computeNormalizedCentroid(GREETING_SEEDS) }
    val HOW_ARE_YOU_CENTROID: FloatArray by lazy { computeNormalizedCentroid(HOW_ARE_YOU_SEEDS) }
    val LOCATION_CENTROID: FloatArray by lazy { computeNormalizedCentroid(LOCATION_QUERY_SEEDS) }
    val TIME_CENTROID: FloatArray by lazy { computeNormalizedCentroid(TIME_QUERY_SEEDS) }
    val YES_NO_CENTROID: FloatArray by lazy { computeNormalizedCentroid(YES_NO_QUERY_SEEDS) }
    val GRATITUDE_CENTROID: FloatArray by lazy { computeNormalizedCentroid(GRATITUDE_SEEDS) }
    val CONFIRMATION_CENTROID: FloatArray by lazy { computeNormalizedCentroid(CONFIRMATION_SEEDS) }
    val APOLOGY_CENTROID: FloatArray by lazy { computeNormalizedCentroid(APOLOGY_SEEDS) }
    val URGENT_CALL_CENTROID: FloatArray by lazy { computeNormalizedCentroid(URGENT_CALL_SEEDS) }

    /**
     * Highly-curated smart replies bank per intent and language code.
     */
    val SMART_REPLIES: Map<MessageIntent, Map<String, List<String>>> = mapOf(
        MessageIntent.GREETING to mapOf(
            "fr" to listOf("Bonjour !", "Salut ! Ça va ?", "Hello !"),
            "en" to listOf("Hello there!", "Hi! How are you?", "Hey!"),
            "ar" to listOf("أهلاً وسهلاً !", "السلام عليكم ورحمة الله !", "مرحباً بك !"),
            "darija" to listOf("Salam khoya !", "Labas ?", "Ahlan !")
        ),
        MessageIntent.HOW_ARE_YOU to mapOf(
            "fr" to listOf("Ça va bien merci, et toi ?", "Tout va bien, merci !", "Impeccable !"),
            "en" to listOf("Doing great, thanks! You?", "All good here!", "Very well, and you?"),
            "ar" to listOf("بخير والحمد لله، وأنت ؟", "تمام الحمد لله !", "كل شيء على ما يرام !"),
            "darija" to listOf("Hamdullah kolchi bikhir, wenta ?", "Labas lhamdoulillah !", "Kolchi mezyan !")
        ),
        MessageIntent.LOCATION_QUERY to mapOf(
            "fr" to listOf("Je suis en route !", "J'arrive dans 5 minutes.", "À la maison."),
            "en" to listOf("On my way!", "I'll be there in 5 mins.", "At home."),
            "ar" to listOf("أنا في الطريق الآن !", "سأصل خلال 5 دقائق.", "في المنزل."),
            "darija" to listOf("Rani f triq !", "Wahed 5 min o nwsal.", "F dar.")
        ),
        MessageIntent.TIME_QUERY to mapOf(
            "fr" to listOf("Vers 18h ?", "D'ici une heure.", "Quand tu veux !"),
            "en" to listOf("Around 6 PM?", "In about an hour.", "Whenever you're ready!"),
            "ar" to listOf("حوالي الساعة 6 ؟", "خلال ساعة إن شاء الله.", "في الوقت الذي يناسبك !"),
            "darija" to listOf("M3a l 6 incha Allah ?", "Mn hna sa3a.", "Weqtma bghiti !")
        ),
        MessageIntent.YES_NO_QUERY to mapOf(
            "fr" to listOf("Oui avec plaisir !", "Non désolé, pas possible.", "Je te confirme ça vite."),
            "en" to listOf("Yes with pleasure!", "Sorry, can't make it.", "Let me confirm shortly."),
            "ar" to listOf("نعم بكل سرور !", "للأسف لا أستطيع.", "سأؤكد لك قريباً."),
            "darija" to listOf("Wakha b kol farah !", "Smehli ma9adertch.", "Nchouf o nrud 3lik daba.")
        ),
        MessageIntent.GRATITUDE to mapOf(
            "fr" to listOf("De rien !", "Avec grand plaisir !", "Je t'en prie !"),
            "en" to listOf("You're welcome!", "My pleasure!", "Anytime!"),
            "ar" to listOf("على الرحب والسعة !", "لا شكر على واجب !", "عفواً أخي الكريم !"),
            "darija" to listOf("Bla jmil !", "Hanya khoya !", "La choukra 3ala wajib !")
        ),
        MessageIntent.CONFIRMATION to mapOf(
            "fr" to listOf("D'accord, parfait !", "Ça marche !", "C'est noté !"),
            "en" to listOf("Sounds good!", "Perfect, got it!", "All set!"),
            "ar" to listOf("حسناً، تمام !", "تم، على بركة الله !", "مفهوم ومسجل !"),
            "darija" to listOf("Wakha safi !", "Mezyan bzaf !", "Safie mtaf9in !")
        ),
        MessageIntent.APOLOGY to mapOf(
            "fr" to listOf("Pas de souci du tout !", "Ne t'en fais pas !", "C'est rien, t'inquiète !"),
            "en" to listOf("No worries at all!", "Don't worry about it!", "All good!"),
            "ar" to listOf("لا عليك، لا مشكلة أبداً !", "لا تقلق، الأمر بسيط !", "حصل خير إن شاء الله !"),
            "darija" to listOf("Hanya makayn mochkil !", "Mathezzch lhem !", "Denya hanya khoya !")
        ),
        MessageIntent.URGENT_CALL to mapOf(
            "fr" to listOf("J'arrive, je t'appelle dans un instant.", "En réunion, je te rappelle après.", "Je t'appelle !"),
            "en" to listOf("I'll call you right back.", "In a meeting, calling you after.", "Calling you now!"),
            "ar" to listOf("سأتصل بك حالاً.", "في اجتماع، أتصل بك لاحقاً.", "حسناً، أتصل بك الآن."),
            "darija" to listOf("Rani f reunion, n3awed lik daba.", "Wakha n3ayet lik db.", "Daba ntasel bik.")
        ),
        MessageIntent.GENERIC to mapOf(
            "fr" to listOf("D'accord", "Très bien !", "Merci pour l'info."),
            "en" to listOf("Got it", "Sounds great!", "Thanks for letting me know."),
            "ar" to listOf("حسناً", "تمام ومفهوم !", "شكراً على المعلومة."),
            "darija" to listOf("Wakha", "Mezyan bzaf !", "Choukran 3la lkhbar.")
        )
    )
}
