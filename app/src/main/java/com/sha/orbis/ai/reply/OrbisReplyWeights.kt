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
        "Salam 3likoum khoya kifech rak ?",
        "Ahlan khouya wach rak mlih ?"
    )

    private val HOW_ARE_YOU_SEEDS = listOf(
        "Ça va ? Tu as passé une bonne journée ?",
        "Comment tu te portes aujourd'hui ?",
        "How are you doing today? Everything good?",
        "How's life treating you?",
        "كيف حالك وكيف هي صحتك اليوم ؟",
        "كيف هي أحوالك إن شاء الله بخير ؟",
        "Wach rak mlih ? Labas 3lik ?",
        "Kifech rak lyoum nchalah ghaya ?"
    )

    private val LOCATION_QUERY_SEEDS = listOf(
        "Tu es où là ? Je ne te vois pas.",
        "Où te trouves-tu en ce moment ?",
        "Where are you right now?",
        "Are you at home or outside?",
        "أين أنت الآن ؟ هل أنت في البيت ؟",
        "وينك يا صاحبي في أي مكان موجود ؟",
        "Win rak dorka wach f dar wla barrani ?",
        "Winek khouya wselt wla mazelt ?"
    )

    private val TIME_QUERY_SEEDS = listOf(
        "À quelle heure on se retrouve ?",
        "Vers quelle heure tu seras disponible ?",
        "What time are we meeting today?",
        "When will you be arriving?",
        "في أي ساعة نلتقي اليوم ؟",
        "كم الساعة المناسبة لك للموعد ؟",
        "Waqtah ghadi netla9aw nchalah ?",
        "M3a chhal nji ?"
    )

    private val YES_NO_QUERY_SEEDS = listOf(
        "Tu viens ce soir avec nous ?",
        "Est-ce que tu es libre pour un café ?",
        "Are you coming tonight?",
        "Can you make it to the meeting?",
        "هل أنت قادم معنا الليلة ؟",
        "هل يمكنك الحضور والمشاركة ؟",
        "Wach rak jay m3ana lyoum ?",
        "Wach t9der tji wla rak mechtghol ?"
    )

    private val GRATITUDE_SEEDS = listOf(
        "Merci beaucoup pour ton aide précieuse !",
        "Je te remercie infiniment pour tout.",
        "Thank you so much, really appreciate it!",
        "Thanks a lot for your help!",
        "شكرا جزيلا لك على المساعدة الرائعة بارك الله فيك",
        "جزاك الله خيرا وأشكرك كثيرا",
        "Choukran bezzaf khouya yatik sahha",
        "Baraka Allahou fik sahit 3lik"
    )

    private val CONFIRMATION_SEEDS = listOf(
        "C'est bon, on fait comme ça alors.",
        "D'accord, marché conclu !",
        "All good, let's do that!",
        "Perfect, confirmed and noted!",
        "حسنا متفقين على هذا الأمر",
        "تمام تم تأكيد الموعد بكل سرور",
        "Safi wakha metfehmin haka",
        "C'est bon mlih bezzaf nchalah"
    )

    private val APOLOGY_SEEDS = listOf(
        "Désolé pour le retard, j'étais coincé.",
        "Pardon pour le dérangement !",
        "Sorry for the delay, got stuck in traffic.",
        "My apologies for not replying earlier.",
        "أعتذر منك كثيرا عن التأخير غير المقصود",
        "سامحني لم أستطع الرد في الوقت المناسب",
        "Smahli bezzaf 3la t3til rani kount mechtghol",
        "Désolé khouya ma9dertch nrod f lwe9t"
    )

    private val URGENT_CALL_SEEDS = listOf(
        "Appelle-moi dès que tu peux c'est important.",
        "Rappelle-moi vite s'il te plaît !",
        "Please call me urgently when you get this.",
        "Give me a quick call ASAP.",
        "اتصل بي في أقرب وقت للأهمية القصوى",
        "عيطلي ضروري راني محتاجك",
        "3aytli dorka khouya 3afak urgent",
        "Tasel biya khouya rani n7abek dorka"
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
     * Enriched with comprehensive Algerian Darija ("dz"), French ("fr"), English ("en"), and Arabic ("ar").
     */
    val SMART_REPLIES: Map<MessageIntent, Map<String, List<String>>> = mapOf(
        MessageIntent.GREETING to mapOf(
            "fr" to listOf(
                "Bonjour ! Comment vas-tu ?",
                "Salut ! Ça fait plaisir d'avoir de tes nouvelles.",
                "Hello ! J'espère que tu vas bien.",
                "Coucou ! Tout se passe bien pour toi ?"
            ),
            "en" to listOf(
                "Hello! How are you doing?",
                "Hey! Great to hear from you.",
                "Hi there! Hope all is well.",
                "Hey! How have you been?"
            ),
            "ar" to listOf(
                "أهلاً وسهلاً ! كيف حالك ؟",
                "السلام عليكم ورحمة الله وبركاته !",
                "مرحباً بك، أتمنى أن تكون بأفضل حال !",
                "أهلاً بك، سعدت بتواصلك !"
            ),
            "dz" to listOf(
                "Salam khouya ! Wach rak mlih ?",
                "Ahlan bik ! Kifech rakoum ?",
                "Salam 3likoum, labas 3lik ?",
                "Wach khouya, ghaya nchalah ?"
            ),
            "darija" to listOf(
                "Salam khouya ! Wach rak mlih ?",
                "Ahlan bik ! Kifech rakoum ?",
                "Salam 3likoum, labas 3lik ?",
                "Wach khouya, ghaya nchalah ?"
            )
        ),
        MessageIntent.HOW_ARE_YOU to mapOf(
            "fr" to listOf(
                "Ça va très bien merci, et toi ?",
                "Tout va pour le mieux, et de ton côté ?",
                "Impeccable, merci d'avoir demandé !",
                "La forme, et toi comment se passe ta journée ?"
            ),
            "en" to listOf(
                "Doing great, thanks! And yourself?",
                "All good on my side, how about you?",
                "Doing wonderful, thanks for asking!",
                "Pretty good, how's everything with you?"
            ),
            "ar" to listOf(
                "بخير ونعمة والحمد لله، وأنت كيف حالك ؟",
                "تمام الحمد لله، كيف هي أمورك ؟",
                "بأفضل حال والحمد لله، شكراً لسؤالك !",
                "كل شيء على ما يرام ولله الحمد."
            ),
            "dz" to listOf(
                "Hamdullah kolchi bikhir, wenta kifech rak ?",
                "Labas lhamdoulillah, sahha w l3afya !",
                "Rani mlih khouya, sahit li saqsit !",
                "Hamdullah ya rabi, w nta rak mlih ?"
            ),
            "darija" to listOf(
                "Hamdullah kolchi bikhir, wenta kifech rak ?",
                "Labas lhamdoulillah, sahha w l3afya !",
                "Rani mlih khouya, sahit li saqsit ?",
                "Hamdullah ya rabi, w nta rak mlih ?"
            )
        ),
        MessageIntent.LOCATION_QUERY to mapOf(
            "fr" to listOf(
                "Je suis en route, j'arrive bientôt !",
                "Je suis coincé dans le trafic, j'arrive dans 10 min.",
                "À la maison, tu peux passer quand tu veux.",
                "Je suis au travail en ce moment."
            ),
            "en" to listOf(
                "On my way, will be there shortly!",
                "Stuck in traffic, about 10 minutes away.",
                "At home right now, feel free to drop by.",
                "Currently at work."
            ),
            "ar" to listOf(
                "أنا في الطريق الآن، سأصل في غضون دقائق !",
                "في المنزل حالياً، يمكنك المجيء متى شئت.",
                "عالق في الزحمة، سأصل قريباً بإذن الله.",
                "أنا في مكان العمل حالياً."
            ),
            "dz" to listOf(
                "Rani f triq, dork nwsel nchalah !",
                "Wahed 10 d9ayeq w nkoun 3andek.",
                "Rani f dar, marhba bik weqtma habbit.",
                "Rani f lkhedma dorka."
            ),
            "darija" to listOf(
                "Rani f triq, dork nwsel nchalah !",
                "Wahed 10 d9ayeq w nkoun 3andek.",
                "Rani f dar, marhba bik weqtma habbit.",
                "Rani f lkhedma dorka."
            )
        ),
        MessageIntent.TIME_QUERY to mapOf(
            "fr" to listOf(
                "Vers quelle heure cela t'arrange ?",
                "D'ici une trentaine de minutes si possible.",
                "Je suis disponible en début de soirée, vers 18h.",
                "Dis-moi quand tu es prêt !"
            ),
            "en" to listOf(
                "What time works best for you?",
                "In about 30 minutes if possible.",
                "I should be free this evening around 6 PM.",
                "Whenever you're ready, let me know!"
            ),
            "ar" to listOf(
                "في أي وقت يناسبك تماماً ؟",
                "خلال نصف ساعة بإذن الله إن كان مناسباً.",
                "سأكون متفرغاً هذا المساء قرابة الساعة السادسة.",
                "أنا جاهز متى ما كنت مستعداً !"
            ),
            "dz" to listOf(
                "Waqtah ysa3dek khouya ?",
                "Mn hna noss sa3a nchalah.",
                "Fel 3chiya m3a l 6 haka.",
                "Weqtma tkoun wajed goli."
            ),
            "darija" to listOf(
                "Waqtah ysa3dek khouya ?",
                "Mn hna noss sa3a nchalah.",
                "Fel 3chiya m3a l 6 haka.",
                "Weqtma tkoun wajed goli."
            )
        ),
        MessageIntent.YES_NO_QUERY to mapOf(
            "fr" to listOf(
                "Oui, avec grand plaisir !",
                "Désolé, ce ne sera pas possible cette fois.",
                "Laisse-moi vérifier mon planning et je te confirme rapidement.",
                "Oui absolument, compte sur moi !"
            ),
            "en" to listOf(
                "Yes, with pleasure!",
                "Sorry, won't be able to make it this time.",
                "Let me check and get back to you shortly.",
                "Yes absolutely, count me in!"
            ),
            "ar" to listOf(
                "نعم بكل تأكيد وبكل سرور !",
                "أعتذر منك، لن أتمكن من ذلك هذه المرة.",
                "دعني أتأكد من برنامجي وسأرد عليك سريعاً.",
                "بالتأكيد، اعتبر الأمر محسوماً !"
            ),
            "dz" to listOf(
                "Walah b kol farah, ma kayen mochkil !",
                "Smahli khouya ma n9derch had lmerra.",
                "Nchouf w nrod 3lik dorka.",
                "Iyeh bayna, 3awel 3liya !"
            ),
            "darija" to listOf(
                "Walah b kol farah, ma kayen mochkil !",
                "Smahli khouya ma n9derch had lmerra.",
                "Nchouf w nrod 3lik dorka.",
                "Iyeh bayna, 3awel 3liya !"
            )
        ),
        MessageIntent.GRATITUDE to mapOf(
            "fr" to listOf(
                "Je t'en prie, c'est tout naturel !",
                "Avec grand plaisir, n'hésite surtout pas si besoin !",
                "De rien, toujours là pour aider !",
                "Merci infiniment à toi également !"
            ),
            "en" to listOf(
                "You're most welcome, anytime!",
                "My absolute pleasure, glad I could help!",
                "Don't mention it, always here for you!",
                "Thank you so much as well!"
            ),
            "ar" to listOf(
                "على الرحب والسعة دائماً، لا شكر على واجب !",
                "بكل سرور، في أي وقت تحتاجه !",
                "عفواً أخي العزيز، هذا أقل واجب.",
                "بارك الله فيك وجزاك كل خير !"
            ),
            "dz" to listOf(
                "Bla jmil khouya, hada wajib !",
                "Yatik essaha, rani hna weqtma s7aqitni !",
                "Sahit bezzaf, rabbi yhafdek !",
                "Hanya khouya l3ziz, matkhememch !"
            ),
            "darija" to listOf(
                "Bla jmil khouya, hada wajib !",
                "Yatik essaha, rani hna weqtma s7aqitni !",
                "Sahit bezzaf, rabbi yhafdek !",
                "Hanya khouya l3ziz, matkhememch !"
            )
        ),
        MessageIntent.CONFIRMATION to mapOf(
            "fr" to listOf(
                "C'est parfait, c'est noté !",
                "Entendu, on fait comme ça !",
                "Excellent, rendez-vous confirmé !",
                "Très bien, marché conclu !"
            ),
            "en" to listOf(
                "Sounds like a plan, noted!",
                "Understood, let's do exactly that!",
                "Great, confirmed on my end!",
                "Perfect, all set!"
            ),
            "ar" to listOf(
                "ممتاز، تم التأكيد والتدوين بنجاح !",
                "مفهوم، متفقون على هذه الخطة تماماً.",
                "على بركة الله، نلتقي كما اتفقنا.",
                "رائع، كل شيء جاهز !"
            ),
            "dz" to listOf(
                "Safi metfehmin, c'est noté !",
                "Mlih bezzaf, haka nchalah !",
                "Wakha, rani m3ak f kolchi !",
                "Kolchi wadah w bayen, sahit !"
            ),
            "darija" to listOf(
                "Safi metfehmin, c'est noté !",
                "Mlih bezzaf, haka nchalah !",
                "Wakha, rani m3ak f kolchi !",
                "Kolchi wadah w bayen, sahit !"
            )
        ),
        MessageIntent.APOLOGY to mapOf(
            "fr" to listOf(
                "Ne t'inquiète pas du tout, aucun problème !",
                "Pas de souci, ça arrive à tout le monde !",
                "Rien de grave, prends tout ton temps.",
                "C'est tout à fait compréhensible, t'en fais pas !"
            ),
            "en" to listOf(
                "No worries at all, it's completely fine!",
                "Don't worry about it, happens to the best of us!",
                "Nothing to apologize for, take your time.",
                "Totally understandable, no stress!"
            ),
            "ar" to listOf(
                "لا عليك أبداً، لا تقلق فالأمر بسيط جداً !",
                "حصل خير، يحدث هذا للجميع دون استثناء.",
                "لا داعي للاعتذار، خذ كامل وقتك براحة.",
                "الأمر مفهوم تماماً، لا تشغل بالك !"
            ),
            "dz" to listOf(
                "Denya hanya khouya, ma sar walou !",
                "Matkhememch gaa, kolchi normal !",
                "Makayn hetta mochkil, khoud rahtek.",
                "Smahli anta, makayn bass nchalah !"
            ),
            "darija" to listOf(
                "Denya hanya khouya, ma sar walou !",
                "Matkhememch gaa, kolchi normal !",
                "Makayn hetta mochkil, khoud rahtek.",
                "Smahli anta, makayn bass nchalah !"
            )
        ),
        MessageIntent.URGENT_CALL to mapOf(
            "fr" to listOf(
                "Je suis occupé pour le moment, je te rappelle dès que je me libère !",
                "En réunion importante, je te passe un coup de fil dès que possible.",
                "Reçu, je t'appelle dans quelques instants.",
                "Je ne peux pas parler pour l'instant, peux-tu m'écrire par message ?"
            ),
            "en" to listOf(
                "Currently tied up, I'll call you as soon as I'm free!",
                "In an important meeting, calling you back ASAP.",
                "Got it, giving you a call in just a few minutes.",
                "Can't talk right now, please text me here."
            ),
            "ar" to listOf(
                "أنا مشغول حالياً، سأتصل بك فور انتهائي مباشرة !",
                "في اجتماع حالياً، سأعاود الاتصال بك بأسرع وقت.",
                "وصلني تنبيهك، سأتصل بك بعد لحظات قليلة.",
                "لا يمكنني التحدث حالياً، يرجى مراسلتي كتابياً هنا."
            ),
            "dz" to listOf(
                "Rani mechtghol dorka, n3awedlek dork dork !",
                "Rani f réunion, ntasel bik ghir nkemmel nchalah.",
                "Safi dork n3aytlek f 5 d9ayeq.",
                "Ma n9derch nahder dorka, ktabli hna khouya."
            ),
            "darija" to listOf(
                "Rani mechtghol dorka, n3awedlek dork dork !",
                "Rani f réunion, ntasel bik ghir nkemmel nchalah.",
                "Safi dork n3aytlek f 5 d9ayeq.",
                "Ma n9derch nahder dorka, ktabli hna khouya."
            )
        ),
        MessageIntent.GENERIC to mapOf(
            "fr" to listOf(
                "D'accord, bien reçu !",
                "Merci pour cette information !",
                "Parfait, je regarde ça de plus près.",
                "Compris, à plus tard !"
            ),
            "en" to listOf(
                "Understood, well received!",
                "Thanks for letting me know!",
                "Sounds good, looking into it.",
                "Got it, talk to you soon!"
            ),
            "ar" to listOf(
                "مفهوم ومسجل، شكراً جزيلاً !",
                "شكراً على التوضيح والمعلومة القيّمة.",
                "تمام، سألقي نظرة على الأمر بعناية.",
                "وصلت الفكرة، إلى لقاء قريب !"
            ),
            "dz" to listOf(
                "Wakha, wseltni lma3louma !",
                "Sahit bezzaf khouya 3la lkhbar.",
                "Mlih, dork nchouf kifech ndir.",
                "C'est bon, nchalah نتلاقاو bientôt !"
            ),
            "darija" to listOf(
                "Wakha, wseltni lma3louma !",
                "Sahit bezzaf khouya 3la lkhbar.",
                "Mlih, dork nchouf kifech ndir.",
                "C'est bon, nchalah نتلاقاو bientôt !"
            )
        )
    )
}
