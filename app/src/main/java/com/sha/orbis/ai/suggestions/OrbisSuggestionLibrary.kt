package com.sha.orbis.ai.suggestions

import java.util.Calendar
import java.util.Locale

/**
 * Bibliothèque souveraine et hautement spécialisée de suggestions contextuelles d'appels et messages.
 * Prend en charge 4 axes linguistiques complets :
 * - Français (fr)
 * - English (en)
 * - العربية الفصحى (ar - Modern Standard Arabic)
 * - الدارجة الجزائرية (dz - Darija Algérienne authentique en arabe & arabizi)
 *
 * Implémente une sélection par diversité sémantique stricte pour garantir
 * 3 suggestions complémentaires sans redondance.
 */
object OrbisSuggestionLibrary {

    // =========================================================================
    // 1. MATRICE COMPLÈTE DE REFUS D'APPELS (CALL DECLINE)
    // =========================================================================

    private val DECLINE_LIBRARY: Map<CallDeclineIntent, Map<String, List<QuickSuggestion>>> = mapOf(
        CallDeclineIntent.IN_MEETING to mapOf(
            "fr" to listOf(
                QuickSuggestion("dec_meet_fr_1", "Je suis en réunion, je te rappelle.", CallDeclineIntent.IN_MEETING, null, CallDeclineIntent.IN_MEETING.icon, SuggestionTone.PROFESSIONAL, "fr"),
                QuickSuggestion("dec_meet_fr_2", "En pleine réunion, je te recontacte plus tard.", CallDeclineIntent.IN_MEETING, null, CallDeclineIntent.IN_MEETING.icon, SuggestionTone.PROFESSIONAL, "fr"),
                QuickSuggestion("dec_meet_fr_3", "Occupé en réunion, c'est urgent ?", CallDeclineIntent.IN_MEETING, null, CallDeclineIntent.IN_MEETING.icon, SuggestionTone.CONCISE, "fr")
            ),
            "en" to listOf(
                QuickSuggestion("dec_meet_en_1", "In a meeting, I'll call you back.", CallDeclineIntent.IN_MEETING, null, CallDeclineIntent.IN_MEETING.icon, SuggestionTone.PROFESSIONAL, "en"),
                QuickSuggestion("dec_meet_en_2", "Currently in a meeting, talk soon.", CallDeclineIntent.IN_MEETING, null, CallDeclineIntent.IN_MEETING.icon, SuggestionTone.PROFESSIONAL, "en"),
                QuickSuggestion("dec_meet_en_3", "Busy in a meeting, is it urgent?", CallDeclineIntent.IN_MEETING, null, CallDeclineIntent.IN_MEETING.icon, SuggestionTone.CONCISE, "en")
            ),
            "ar" to listOf(
                QuickSuggestion("dec_meet_ar_1", "أنا في اجتماع حالياً، سأتصل بك لاحقاً.", CallDeclineIntent.IN_MEETING, null, CallDeclineIntent.IN_MEETING.icon, SuggestionTone.PROFESSIONAL, "ar"),
                QuickSuggestion("dec_meet_ar_2", "في اجتماع عمل، سأتواصل معك فور انتهائي.", CallDeclineIntent.IN_MEETING, null, CallDeclineIntent.IN_MEETING.icon, SuggestionTone.PROFESSIONAL, "ar"),
                QuickSuggestion("dec_meet_ar_3", "مشغول باجتماع، هل الأمر عاجل ؟", CallDeclineIntent.IN_MEETING, null, CallDeclineIntent.IN_MEETING.icon, SuggestionTone.CONCISE, "ar")
            ),
            "dz" to listOf(
                QuickSuggestion("dec_meet_dz_1", "Rani f réunion, n'3awedlek men ba3d.", CallDeclineIntent.IN_MEETING, null, CallDeclineIntent.IN_MEETING.icon, SuggestionTone.CASUAL, "dz"),
                QuickSuggestion("dec_meet_dz_2", "راني في اجتماع، نعاودلك من بعد إن شاء الله.", CallDeclineIntent.IN_MEETING, null, CallDeclineIntent.IN_MEETING.icon, SuggestionTone.CASUAL, "dz"),
                QuickSuggestion("dec_meet_dz_3", "Rani f l'khedma dork, chwiya o n'3ayetlek.", CallDeclineIntent.IN_MEETING, null, CallDeclineIntent.IN_MEETING.icon, SuggestionTone.CASUAL, "dz")
            )
        ),

        CallDeclineIntent.DRIVING to mapOf(
            "fr" to listOf(
                QuickSuggestion("dec_driv_fr_1", "Je suis au volant, écris-moi.", CallDeclineIntent.DRIVING, null, CallDeclineIntent.DRIVING.icon, SuggestionTone.CONCISE, "fr"),
                QuickSuggestion("dec_driv_fr_2", "En route, je te rappelle dès que je m'arrête.", CallDeclineIntent.DRIVING, null, CallDeclineIntent.DRIVING.icon, SuggestionTone.CASUAL, "fr"),
                QuickSuggestion("dec_driv_fr_3", "Au volant, impossible de décrocher pour le moment.", CallDeclineIntent.DRIVING, null, CallDeclineIntent.DRIVING.icon, SuggestionTone.PROFESSIONAL, "fr")
            ),
            "en" to listOf(
                QuickSuggestion("dec_driv_en_1", "Driving right now, please text me.", CallDeclineIntent.DRIVING, null, CallDeclineIntent.DRIVING.icon, SuggestionTone.CONCISE, "en"),
                QuickSuggestion("dec_driv_en_2", "On the road, will call once parked.", CallDeclineIntent.DRIVING, null, CallDeclineIntent.DRIVING.icon, SuggestionTone.CASUAL, "en"),
                QuickSuggestion("dec_driv_en_3", "Behind the wheel, can't talk right now.", CallDeclineIntent.DRIVING, null, CallDeclineIntent.DRIVING.icon, SuggestionTone.PROFESSIONAL, "en")
            ),
            "ar" to listOf(
                QuickSuggestion("dec_driv_ar_1", "أقود السيارة حالياً، يرجى مراسلتي كتابياً.", CallDeclineIntent.DRIVING, null, CallDeclineIntent.DRIVING.icon, SuggestionTone.CONCISE, "ar"),
                QuickSuggestion("dec_driv_ar_2", "في الطريق، سأتصل بك فور وصولي.", CallDeclineIntent.DRIVING, null, CallDeclineIntent.DRIVING.icon, SuggestionTone.CASUAL, "ar"),
                QuickSuggestion("dec_driv_ar_3", "أقود الآن ولا أستطيع الرد على المكالمات.", CallDeclineIntent.DRIVING, null, CallDeclineIntent.DRIVING.icon, SuggestionTone.PROFESSIONAL, "ar")
            ),
            "dz" to listOf(
                QuickSuggestion("dec_driv_dz_1", "Rani nsoug, ektébli message.", CallDeclineIntent.DRIVING, null, CallDeclineIntent.DRIVING.icon, SuggestionTone.CONCISE, "dz"),
                QuickSuggestion("dec_driv_dz_2", "راني نسوق، اكتبلي ميساج من فضلك.", CallDeclineIntent.DRIVING, null, CallDeclineIntent.DRIVING.icon, SuggestionTone.CONCISE, "dz"),
                QuickSuggestion("dec_driv_dz_3", "Rani f triq, ki nahbes n'3ayetlek.", CallDeclineIntent.DRIVING, null, CallDeclineIntent.DRIVING.icon, SuggestionTone.CASUAL, "dz")
            )
        ),

        CallDeclineIntent.BUSY_GENERAL to mapOf(
            "fr" to listOf(
                QuickSuggestion("dec_busy_fr_1", "Je ne peux pas parler pour le moment.", CallDeclineIntent.BUSY_GENERAL, null, CallDeclineIntent.BUSY_GENERAL.icon, SuggestionTone.CONCISE, "fr"),
                QuickSuggestion("dec_busy_fr_2", "Occupé actuellement, je te rappelle dès que possible.", CallDeclineIntent.BUSY_GENERAL, null, CallDeclineIntent.BUSY_GENERAL.icon, SuggestionTone.CASUAL, "fr"),
                QuickSuggestion("dec_busy_fr_3", "Pas disponible là, je reviens vers toi rapidement.", CallDeclineIntent.BUSY_GENERAL, null, CallDeclineIntent.BUSY_GENERAL.icon, SuggestionTone.PROFESSIONAL, "fr")
            ),
            "en" to listOf(
                QuickSuggestion("dec_busy_en_1", "Can't talk right now.", CallDeclineIntent.BUSY_GENERAL, null, CallDeclineIntent.BUSY_GENERAL.icon, SuggestionTone.CONCISE, "en"),
                QuickSuggestion("dec_busy_en_2", "A bit tied up, will call you later.", CallDeclineIntent.BUSY_GENERAL, null, CallDeclineIntent.BUSY_GENERAL.icon, SuggestionTone.CASUAL, "en"),
                QuickSuggestion("dec_busy_en_3", "Not available at the moment, will reach out soon.", CallDeclineIntent.BUSY_GENERAL, null, CallDeclineIntent.BUSY_GENERAL.icon, SuggestionTone.PROFESSIONAL, "en")
            ),
            "ar" to listOf(
                QuickSuggestion("dec_busy_ar_1", "لا أستطيع التحدث في الوقت الحالي.", CallDeclineIntent.BUSY_GENERAL, null, CallDeclineIntent.BUSY_GENERAL.icon, SuggestionTone.CONCISE, "ar"),
                QuickSuggestion("dec_busy_ar_2", "مشغول حالياً، سأعاود الاتصال بك بأقرب وقت.", CallDeclineIntent.BUSY_GENERAL, null, CallDeclineIntent.BUSY_GENERAL.icon, SuggestionTone.CASUAL, "ar"),
                QuickSuggestion("dec_busy_ar_3", "غير متاح الآن، سأتواصل معك لاحقاً إن شاء الله.", CallDeclineIntent.BUSY_GENERAL, null, CallDeclineIntent.BUSY_GENERAL.icon, SuggestionTone.PROFESSIONAL, "ar")
            ),
            "dz" to listOf(
                QuickSuggestion("dec_busy_dz_1", "Ma9dertch n'hder dork, n'3awedlek.", CallDeclineIntent.BUSY_GENERAL, null, CallDeclineIntent.BUSY_GENERAL.icon, SuggestionTone.CONCISE, "dz"),
                QuickSuggestion("dec_busy_dz_2", "راني مشغول درك، نعاود نعيطلك كي نسالي.", CallDeclineIntent.BUSY_GENERAL, null, CallDeclineIntent.BUSY_GENERAL.icon, SuggestionTone.CASUAL, "dz"),
                QuickSuggestion("dec_busy_dz_3", "Rani m'occupé dorka, chwiya o n'chouf m3ak.", CallDeclineIntent.BUSY_GENERAL, null, CallDeclineIntent.BUSY_GENERAL.icon, SuggestionTone.CASUAL, "dz")
            )
        ),

        CallDeclineIntent.CALL_BACK_SOON to mapOf(
            "fr" to listOf(
                QuickSuggestion("dec_cbs_fr_1", "Je te rappelle dans 5 minutes.", CallDeclineIntent.CALL_BACK_SOON, null, CallDeclineIntent.CALL_BACK_SOON.icon, SuggestionTone.CONCISE, "fr"),
                QuickSuggestion("dec_cbs_fr_2", "Laisse-moi 10 minutes et je t'appelle.", CallDeclineIntent.CALL_BACK_SOON, null, CallDeclineIntent.CALL_BACK_SOON.icon, SuggestionTone.CASUAL, "fr"),
                QuickSuggestion("dec_cbs_fr_3", "Je termine un truc et je te rappelle !", CallDeclineIntent.CALL_BACK_SOON, null, CallDeclineIntent.CALL_BACK_SOON.icon, SuggestionTone.CASUAL, "fr")
            ),
            "en" to listOf(
                QuickSuggestion("dec_cbs_en_1", "I'll call you in 5 minutes.", CallDeclineIntent.CALL_BACK_SOON, null, CallDeclineIntent.CALL_BACK_SOON.icon, SuggestionTone.CONCISE, "en"),
                QuickSuggestion("dec_cbs_en_2", "Give me 10 minutes and I'll call.", CallDeclineIntent.CALL_BACK_SOON, null, CallDeclineIntent.CALL_BACK_SOON.icon, SuggestionTone.CASUAL, "en"),
                QuickSuggestion("dec_cbs_en_3", "Wrapping up, calling you right away!", CallDeclineIntent.CALL_BACK_SOON, null, CallDeclineIntent.CALL_BACK_SOON.icon, SuggestionTone.CASUAL, "en")
            ),
            "ar" to listOf(
                QuickSuggestion("dec_cbs_ar_1", "سأتصل بك خلال 5 دقائق.", CallDeclineIntent.CALL_BACK_SOON, null, CallDeclineIntent.CALL_BACK_SOON.icon, SuggestionTone.CONCISE, "ar"),
                QuickSuggestion("dec_cbs_ar_2", "أمهلني 10 دقائق وسأعاود الاتصال.", CallDeclineIntent.CALL_BACK_SOON, null, CallDeclineIntent.CALL_BACK_SOON.icon, SuggestionTone.CASUAL, "ar"),
                QuickSuggestion("dec_cbs_ar_3", "سأتصل بك بعد لحظات إن شاء الله.", CallDeclineIntent.CALL_BACK_SOON, null, CallDeclineIntent.CALL_BACK_SOON.icon, SuggestionTone.CASUAL, "ar")
            ),
            "dz" to listOf(
                QuickSuggestion("dec_cbs_dz_1", "N'3awed n'3ayetlek f 5 dqayeq.", CallDeclineIntent.CALL_BACK_SOON, null, CallDeclineIntent.CALL_BACK_SOON.icon, SuggestionTone.CONCISE, "dz"),
                QuickSuggestion("dec_cbs_dz_2", "نعاود نعيطلك بعد 5 دقائق.", CallDeclineIntent.CALL_BACK_SOON, null, CallDeclineIntent.CALL_BACK_SOON.icon, SuggestionTone.CONCISE, "dz"),
                QuickSuggestion("dec_cbs_dz_3", "Dork n'kamel o n'3ayetlek !", CallDeclineIntent.CALL_BACK_SOON, null, CallDeclineIntent.CALL_BACK_SOON.icon, SuggestionTone.CASUAL, "dz")
            )
        ),

        CallDeclineIntent.TEXT_ME_INSTEAD to mapOf(
            "fr" to listOf(
                QuickSuggestion("dec_txt_fr_1", "Écris-moi par message, c'est plus simple.", CallDeclineIntent.TEXT_ME_INSTEAD, null, CallDeclineIntent.TEXT_ME_INSTEAD.icon, SuggestionTone.CASUAL, "fr"),
                QuickSuggestion("dec_txt_fr_2", "Envoie-moi un message ici.", CallDeclineIntent.TEXT_ME_INSTEAD, null, CallDeclineIntent.TEXT_ME_INSTEAD.icon, SuggestionTone.CONCISE, "fr"),
                QuickSuggestion("dec_txt_fr_3", "Je préfère échanger par écrit en ce moment.", CallDeclineIntent.TEXT_ME_INSTEAD, null, CallDeclineIntent.TEXT_ME_INSTEAD.icon, SuggestionTone.PROFESSIONAL, "fr")
            ),
            "en" to listOf(
                QuickSuggestion("dec_txt_en_1", "Please text me instead, it's easier.", CallDeclineIntent.TEXT_ME_INSTEAD, null, CallDeclineIntent.TEXT_ME_INSTEAD.icon, SuggestionTone.CASUAL, "en"),
                QuickSuggestion("dec_txt_en_2", "Send me a message here.", CallDeclineIntent.TEXT_ME_INSTEAD, null, CallDeclineIntent.TEXT_ME_INSTEAD.icon, SuggestionTone.CONCISE, "en"),
                QuickSuggestion("dec_txt_en_3", "Can't pick up, let's chat in text.", CallDeclineIntent.TEXT_ME_INSTEAD, null, CallDeclineIntent.TEXT_ME_INSTEAD.icon, SuggestionTone.PROFESSIONAL, "en")
            ),
            "ar" to listOf(
                QuickSuggestion("dec_txt_ar_1", "أرجو مراسلتي عبر الرسائل.", CallDeclineIntent.TEXT_ME_INSTEAD, null, CallDeclineIntent.TEXT_ME_INSTEAD.icon, SuggestionTone.CASUAL, "ar"),
                QuickSuggestion("dec_txt_ar_2", "أرسل لي رسالة وسأجيبك فوراً.", CallDeclineIntent.TEXT_ME_INSTEAD, null, CallDeclineIntent.TEXT_ME_INSTEAD.icon, SuggestionTone.CONCISE, "ar"),
                QuickSuggestion("dec_txt_ar_3", "يفضل التواصل كتابياً في الوقت الحالي.", CallDeclineIntent.TEXT_ME_INSTEAD, null, CallDeclineIntent.TEXT_ME_INSTEAD.icon, SuggestionTone.PROFESSIONAL, "ar")
            ),
            "dz" to listOf(
                QuickSuggestion("dec_txt_dz_1", "Ektébli message hna khir.", CallDeclineIntent.TEXT_ME_INSTEAD, null, CallDeclineIntent.TEXT_ME_INSTEAD.icon, SuggestionTone.CASUAL, "dz"),
                QuickSuggestion("dec_txt_dz_2", "اكتبلي ميساج هنا خير ربي يحفظك.", CallDeclineIntent.TEXT_ME_INSTEAD, null, CallDeclineIntent.TEXT_ME_INSTEAD.icon, SuggestionTone.CASUAL, "dz"),
                QuickSuggestion("dec_txt_dz_3", "Ab3atli f chat dork n'repoundi.", CallDeclineIntent.TEXT_ME_INSTEAD, null, CallDeclineIntent.TEXT_ME_INSTEAD.icon, SuggestionTone.CASUAL, "dz")
            )
        ),

        CallDeclineIntent.URGENT_CHECK to mapOf(
            "fr" to listOf(
                QuickSuggestion("dec_urg_fr_1", "C'est urgent ?", CallDeclineIntent.URGENT_CHECK, null, CallDeclineIntent.URGENT_CHECK.icon, SuggestionTone.CONCISE, "fr"),
                QuickSuggestion("dec_urg_fr_2", "Dis-moi si c'est important par message.", CallDeclineIntent.URGENT_CHECK, null, CallDeclineIntent.URGENT_CHECK.icon, SuggestionTone.CASUAL, "fr"),
                QuickSuggestion("dec_urg_fr_3", "Est-ce urgent ou je peux te rappeler après ?", CallDeclineIntent.URGENT_CHECK, null, CallDeclineIntent.URGENT_CHECK.icon, SuggestionTone.PROFESSIONAL, "fr")
            ),
            "en" to listOf(
                QuickSuggestion("dec_urg_en_1", "Is it urgent?", CallDeclineIntent.URGENT_CHECK, null, CallDeclineIntent.URGENT_CHECK.icon, SuggestionTone.CONCISE, "en"),
                QuickSuggestion("dec_urg_en_2", "Text me if it's important.", CallDeclineIntent.URGENT_CHECK, null, CallDeclineIntent.URGENT_CHECK.icon, SuggestionTone.CASUAL, "en"),
                QuickSuggestion("dec_urg_en_3", "Is it urgent or can I call you later?", CallDeclineIntent.URGENT_CHECK, null, CallDeclineIntent.URGENT_CHECK.icon, SuggestionTone.PROFESSIONAL, "en")
            ),
            "ar" to listOf(
                QuickSuggestion("dec_urg_ar_1", "هل الأمر عاجل ؟", CallDeclineIntent.URGENT_CHECK, null, CallDeclineIntent.URGENT_CHECK.icon, SuggestionTone.CONCISE, "ar"),
                QuickSuggestion("dec_urg_ar_2", "أخبرني برسالة إن كان الموضوع هاماً.", CallDeclineIntent.URGENT_CHECK, null, CallDeclineIntent.URGENT_CHECK.icon, SuggestionTone.CASUAL, "ar"),
                QuickSuggestion("dec_urg_ar_3", "هل هناك أمر طارئ أم نتحدث لاحقاً ؟", CallDeclineIntent.URGENT_CHECK, null, CallDeclineIntent.URGENT_CHECK.icon, SuggestionTone.PROFESSIONAL, "ar")
            ),
            "dz" to listOf(
                QuickSuggestion("dec_urg_dz_1", "Kayen haja urgente ?", CallDeclineIntent.URGENT_CHECK, null, CallDeclineIntent.URGENT_CHECK.icon, SuggestionTone.CONCISE, "dz"),
                QuickSuggestion("dec_urg_dz_2", "كاين حاجة urgence ؟ ابعثلي في الشات.", CallDeclineIntent.URGENT_CHECK, null, CallDeclineIntent.URGENT_CHECK.icon, SuggestionTone.CONCISE, "dz"),
                QuickSuggestion("dec_urg_dz_3", "Kayen chi mochkil ? Ektébli.", CallDeclineIntent.URGENT_CHECK, null, CallDeclineIntent.URGENT_CHECK.icon, SuggestionTone.CASUAL, "dz")
            )
        )
    )

    // =========================================================================
    // 2. MATRICE COMPLÈTE DE SUIVI APPELS MANQUÉS (MISSED CALL FOLLOW-UP)
    // =========================================================================

    private val MISSED_FOLLOWUP_LIBRARY: Map<MissedCallFollowUpIntent, Map<String, List<QuickSuggestion>>> = mapOf(
        MissedCallFollowUpIntent.WHAT_HAPPENED to mapOf(
            "fr" to listOf(
                QuickSuggestion("mis_wh_fr_1", "Désolé j'ai manqué ton appel, ça va ?", null, MissedCallFollowUpIntent.WHAT_HAPPENED, MissedCallFollowUpIntent.WHAT_HAPPENED.icon, SuggestionTone.CASUAL, "fr"),
                QuickSuggestion("mis_wh_fr_2", "Tu voulais me dire quelque chose ?", null, MissedCallFollowUpIntent.WHAT_HAPPENED, MissedCallFollowUpIntent.WHAT_HAPPENED.icon, SuggestionTone.CASUAL, "fr")
            ),
            "en" to listOf(
                QuickSuggestion("mis_wh_en_1", "Sorry I missed your call, what's up?", null, MissedCallFollowUpIntent.WHAT_HAPPENED, MissedCallFollowUpIntent.WHAT_HAPPENED.icon, SuggestionTone.CASUAL, "en"),
                QuickSuggestion("mis_wh_en_2", "Did you need something?", null, MissedCallFollowUpIntent.WHAT_HAPPENED, MissedCallFollowUpIntent.WHAT_HAPPENED.icon, SuggestionTone.CASUAL, "en")
            ),
            "ar" to listOf(
                QuickSuggestion("mis_wh_ar_1", "أعتذر عن تفويت مكالمتك، هل كل شيء بخير ؟", null, MissedCallFollowUpIntent.WHAT_HAPPENED, MissedCallFollowUpIntent.WHAT_HAPPENED.icon, SuggestionTone.CASUAL, "ar"),
                QuickSuggestion("mis_wh_ar_2", "رأيت مكالمتك، فيمَ يمكنني مساعدتك ؟", null, MissedCallFollowUpIntent.WHAT_HAPPENED, MissedCallFollowUpIntent.WHAT_HAPPENED.icon, SuggestionTone.CASUAL, "ar")
            ),
            "dz" to listOf(
                QuickSuggestion("mis_wh_dz_1", "Smahli ma9dertch n'jawbek, wach kayen ?", null, MissedCallFollowUpIntent.WHAT_HAPPENED, MissedCallFollowUpIntent.WHAT_HAPPENED.icon, SuggestionTone.CASUAL, "dz"),
                QuickSuggestion("mis_wh_dz_2", "سمحلي ما شفتش لابال، كاش ما كاين ؟", null, MissedCallFollowUpIntent.WHAT_HAPPENED, MissedCallFollowUpIntent.WHAT_HAPPENED.icon, SuggestionTone.CASUAL, "dz")
            )
        ),

        MissedCallFollowUpIntent.CALL_BACK_NOW to mapOf(
            "fr" to listOf(
                QuickSuggestion("mis_cb_fr_1", "Je suis libre maintenant, je te rappelle ?", null, MissedCallFollowUpIntent.CALL_BACK_NOW, MissedCallFollowUpIntent.CALL_BACK_NOW.icon, SuggestionTone.CASUAL, "fr"),
                QuickSuggestion("mis_cb_fr_2", "Désolé, je peux te rappeler de suite !", null, MissedCallFollowUpIntent.CALL_BACK_NOW, MissedCallFollowUpIntent.CALL_BACK_NOW.icon, SuggestionTone.CASUAL, "fr")
            ),
            "en" to listOf(
                QuickSuggestion("mis_cb_en_1", "Free now, should I call you back?", null, MissedCallFollowUpIntent.CALL_BACK_NOW, MissedCallFollowUpIntent.CALL_BACK_NOW.icon, SuggestionTone.CASUAL, "en"),
                QuickSuggestion("mis_cb_en_2", "Sorry about that, can call you right away!", null, MissedCallFollowUpIntent.CALL_BACK_NOW, MissedCallFollowUpIntent.CALL_BACK_NOW.icon, SuggestionTone.CASUAL, "en")
            ),
            "ar" to listOf(
                QuickSuggestion("mis_cb_ar_1", "أنا متاح الآن، هل أعاود الاتصال بك ؟", null, MissedCallFollowUpIntent.CALL_BACK_NOW, MissedCallFollowUpIntent.CALL_BACK_NOW.icon, SuggestionTone.CASUAL, "ar"),
                QuickSuggestion("mis_cb_ar_2", "أعتذر منك، يمكنني الاتصال بك حالاً !", null, MissedCallFollowUpIntent.CALL_BACK_NOW, MissedCallFollowUpIntent.CALL_BACK_NOW.icon, SuggestionTone.CASUAL, "ar")
            ),
            "dz" to listOf(
                QuickSuggestion("mis_cb_dz_1", "Rani dispo dork, n'3awed n'3ayetlek ?", null, MissedCallFollowUpIntent.CALL_BACK_NOW, MissedCallFollowUpIntent.CALL_BACK_NOW.icon, SuggestionTone.CASUAL, "dz"),
                QuickSuggestion("mis_cb_dz_2", "راني مسالي درك، نعيطلك ؟", null, MissedCallFollowUpIntent.CALL_BACK_NOW, MissedCallFollowUpIntent.CALL_BACK_NOW.icon, SuggestionTone.CASUAL, "dz")
            )
        ),

        MissedCallFollowUpIntent.RESCHEDULE_LATER to mapOf(
            "fr" to listOf(
                QuickSuggestion("mis_res_fr_1", "Pas dispo tout de suite, on s'appelle ce soir ?", null, MissedCallFollowUpIntent.RESCHEDULE_LATER, MissedCallFollowUpIntent.RESCHEDULE_LATER.icon, SuggestionTone.CASUAL, "fr"),
                QuickSuggestion("mis_res_fr_2", "Je te recontacte un peu plus tard.", null, MissedCallFollowUpIntent.RESCHEDULE_LATER, MissedCallFollowUpIntent.RESCHEDULE_LATER.icon, SuggestionTone.PROFESSIONAL, "fr")
            ),
            "en" to listOf(
                QuickSuggestion("mis_res_en_1", "Tied up right now, let's connect tonight?", null, MissedCallFollowUpIntent.RESCHEDULE_LATER, MissedCallFollowUpIntent.RESCHEDULE_LATER.icon, SuggestionTone.CASUAL, "en"),
                QuickSuggestion("mis_res_en_2", "Will get back to you a bit later.", null, MissedCallFollowUpIntent.RESCHEDULE_LATER, MissedCallFollowUpIntent.RESCHEDULE_LATER.icon, SuggestionTone.PROFESSIONAL, "en")
            ),
            "ar" to listOf(
                QuickSuggestion("mis_res_ar_1", "لست متاحاً الآن، هل نتحدث هذا المساء ؟", null, MissedCallFollowUpIntent.RESCHEDULE_LATER, MissedCallFollowUpIntent.RESCHEDULE_LATER.icon, SuggestionTone.CASUAL, "ar"),
                QuickSuggestion("mis_res_ar_2", "سأتواصل معك في وقت لاحق اليوم.", null, MissedCallFollowUpIntent.RESCHEDULE_LATER, MissedCallFollowUpIntent.RESCHEDULE_LATER.icon, SuggestionTone.PROFESSIONAL, "ar")
            ),
            "dz" to listOf(
                QuickSuggestion("mis_res_dz_1", "Chwiya mchougi dork, net3aytou felil ?", null, MissedCallFollowUpIntent.RESCHEDULE_LATER, MissedCallFollowUpIntent.RESCHEDULE_LATER.icon, SuggestionTone.CASUAL, "dz"),
                QuickSuggestion("mis_res_dz_2", "نتواصل معاك من بعد إن شاء الله.", null, MissedCallFollowUpIntent.RESCHEDULE_LATER, MissedCallFollowUpIntent.RESCHEDULE_LATER.icon, SuggestionTone.CASUAL, "dz")
            )
        )
    )

    /**
     * Résout le code de langue le plus adapté ("fr", "en", "ar", "dz")
     * en inspectant la locale de l'appareil ou le choix forcé.
     */
    fun resolveLanguageCode(preferredLang: String? = null): String {
        if (!preferredLang.isNullOrBlank()) {
            val lower = preferredLang.lowercase(Locale.ROOT)
            if (lower in listOf("fr", "en", "ar", "dz", "darija")) {
                return if (lower == "darija") "dz" else lower
            }
        }
        val defaultLang = Locale.getDefault().language.lowercase(Locale.ROOT)
        return when {
            defaultLang == "ar" -> "ar"
            defaultLang == "en" -> "en"
            else -> "fr"
        }
    }

    /**
     * Résout la langue optimale pour un correspondant :
     * 1. Langue explicitement demandée
     * 2. Affinité linguistique mémorisée sur l'appareil pour ce peerId
     * 3. Langue locale par défaut du système
     */
    fun resolveLanguageForPeer(
        context: android.content.Context? = null,
        peerId: String? = null,
        explicitLang: String? = null
    ): String {
        if (!explicitLang.isNullOrBlank()) {
            return resolveLanguageCode(explicitLang)
        }
        return com.sha.orbis.ai.affinity.OrbisPeerLanguageEngine.getPreferredLanguage(context, peerId)
    }

    /**
     * Vérifie si l'heure courante correspond à une tranche nocturne tardive ou très matinale.
     */
    fun isOffHours(calendarHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): Boolean {
        return calendarHour >= 22 || calendarHour < 8
    }

    /**
     * Génère 3 suggestions de refus d'appel diversifiées selon le contexte horaire et relationnel.
     * Règle de diversité sémantique : 3 intentions distinctes garanties.
     */
    fun getDiverseDeclineSuggestions(
        preferredLang: String? = null,
        isContactSaved: Boolean = true,
        calendarHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        context: android.content.Context? = null,
        peerId: String? = null
    ): List<QuickSuggestion> {
        val lang = resolveLanguageForPeer(context, peerId, preferredLang)

        // Sélection des 3 intentions prioritaires selon le contexte temporel
        val targetIntents = when {
            // Nuit tardive / Très tôt (22h - 8h) : Repos / Message écrit / C'est urgent ?
            calendarHour >= 22 || calendarHour < 8 -> listOf(
                CallDeclineIntent.TEXT_ME_INSTEAD,
                CallDeclineIntent.URGENT_CHECK,
                CallDeclineIntent.BUSY_GENERAL
            )
            // Heures ouvrées (8h - 18h) : Réunion / Rappel rapide / Écrit
            calendarHour in 8..18 -> listOf(
                CallDeclineIntent.IN_MEETING,
                CallDeclineIntent.CALL_BACK_SOON,
                CallDeclineIntent.TEXT_ME_INSTEAD
            )
            // Soirée (18h - 22h) : Au volant / Rappel rapide / C'est urgent ?
            else -> listOf(
                CallDeclineIntent.CALL_BACK_SOON,
                CallDeclineIntent.DRIVING,
                CallDeclineIntent.URGENT_CHECK
            )
        }

        val result = mutableListOf<QuickSuggestion>()
        for (intent in targetIntents) {
            val list = DECLINE_LIBRARY[intent]?.get(lang)
                ?: DECLINE_LIBRARY[intent]?.get("fr")
                ?: emptyList()
            if (list.isNotEmpty()) {
                // Préfère casual si contact enregistré, ou professional si inconnu
                val picked = if (isContactSaved) {
                    list.firstOrNull { it.tone == SuggestionTone.CASUAL } ?: list.first()
                } else {
                    list.firstOrNull { it.tone == SuggestionTone.PROFESSIONAL } ?: list.first()
                }
                result.add(picked)
            }
        }
        return result.take(3)
    }

    /**
     * Génère 3 suggestions riches pour le suivi d'un appel manqué.
     */
    fun getDiverseMissedCallSuggestions(
        preferredLang: String? = null,
        context: android.content.Context? = null,
        peerId: String? = null
    ): List<QuickSuggestion> {
        val lang = resolveLanguageForPeer(context, peerId, preferredLang)
        val targetIntents = listOf(
            MissedCallFollowUpIntent.WHAT_HAPPENED,
            MissedCallFollowUpIntent.CALL_BACK_NOW,
            MissedCallFollowUpIntent.RESCHEDULE_LATER
        )

        val result = mutableListOf<QuickSuggestion>()
        for (intent in targetIntents) {
            val list = MISSED_FOLLOWUP_LIBRARY[intent]?.get(lang)
                ?: MISSED_FOLLOWUP_LIBRARY[intent]?.get("fr")
                ?: emptyList()
            if (list.isNotEmpty()) {
                result.add(list.first())
            }
        }
        return result.take(3)
    }
}
