package com.sha.orbis.ai.guard

import com.sha.orbis.ai.core.OrbisTokenizer
import com.sha.orbis.ai.core.OrbisVectorMath

/**
 * Pre-trained Knowledge Base & Semantic Weights for ORBIS Guard-LLM.
 * Embedded native weights covering French, English, Arabic, and Franco-Arabe / Darija.
 */
object OrbisGuardWeights {

    // Seed exemplar training texts for centroid derivation
    private val BANKING_PHISHING_SEEDS = listOf(
        "Votre carte bancaire a été temporairement suspendue suite à une activité suspecte. Cliquez ici pour valider vos accès",
        "Alerte Sécurité: Un paiement de 450€ est en attente de confirmation. Si vous n'êtes pas à l'origine, annulez immédiatement",
        "Info Société Générale: Veuillez mettre à jour votre numéro de sécurité afin d'éviter la résiliation de vos services",
        "Bank alert: Your account has been locked due to unauthorized login attempts. Verify your identity now",
        "Security update: New payee added. If this was not you, visit our secure portal to revoke access",
        "تحذير أمني: تم حظر بطاقتك البنكية مؤقتا. يرجى تأكيد هويتك عبر الرابط التالي لتجنب إيقاف الحساب",
        "حسابك في البنك معلق. يرجى الدخول إلى الرابط وتأكيد كلمة المرور الخاصة بك",
        "Carte te3ek tbloquat f la banque dkhoul f le lien bach tactiviha urgent"
    )

    private val DELIVERY_PHISHING_SEEDS = listOf(
        "Chronopost: Votre colis numéro 489201 ne peut être livré en raison de frais de douane impayés de 2,99€. Réglez ici",
        "Mondial Relay: Votre paquet est retenu au centre de tri. Merci de confirmer votre adresse postale sous 24h",
        "Info Colissimo: Impossible d'acheminer votre livraison car le numéro de rue est manquant. Cliquez pour corriger",
        "DHL Express: Your shipment is on hold due to unpaid import duty fees. Pay online to schedule delivery",
        "UPS notice: We attempted delivery today. Schedule a redelivery date through this link",
        "طردك البريدي محتجز في مركز الفرز بسبب عدم دفع رسوم التوصيل. يرجى الدفع عبر الرابط",
        "Colis dyalek rah f la poste khassk tkhalles les frais bach ywslek"
    )

    private val EMERGENCY_SCAM_SEEDS = listOf(
        "Coucou maman, j'ai cassé mon téléphone. C'est mon nouveau numéro temporaire, peux-tu m'écrire sur WhatsApp en urgence ?",
        "Salut papa, mon tél est tombé dans l'eau. Envoie-moi un message sur ce numéro s'il te plaît c'est très important",
        "Hi mum, I dropped my phone and screen broke. This is my temporary new number, please text me on WhatsApp ASAP",
        "Hey dad, new number as my phone was stolen. Can you please message me urgently on WhatsApp?",
        "Salam mama tksar le telephone te3i hada numero jdid ab3tili f whatsapp urgent",
        "سلام ماما ضاعلي التيليفون هادا رقمي الجديد عيطيلي فايبر ولا واتساب ضروري"
    )

    private val CRYPTO_LOTTERY_SEEDS = listOf(
        "Félicitations ! Votre numéro a été tiré au sort pour remporter 50 000€ ou une carte cadeau Amazon. Réclamez votre lot",
        "Gagnez jusqu'à 800€ par jour en investissant dans notre plateforme crypto automatisée sans risque",
        "Congratulations! You have been selected as the winner of our grand prize draw. Claim your prize now",
        "Make $500 daily trading Bitcoin with our automated AI system. Guaranteed profits with zero experience",
        "مبروك لقد ربحت جائزة مالية كبرى قدرها 10000 دولار. اضغط هنا لاستلام مكافأتك فورا",
        "Rbeht m3ana 50 melyon ab3at message bach tdihom"
    )

    private val COMMERCIAL_SPAM_SEEDS = listOf(
        "Offre exclusive: -50% sur toute la boutique jusqu'à ce soir seulement ! Profitez-en avec le code PROMO50",
        "Bénéficiez d'une pompe à chaleur à 1€ financée par l'État. Testez votre éligibilité en 3 clics",
        "Flash sale: 70% off everything today only! Click to view trending items and use code SAVE70",
        "تخفيضات كبرى تصل إلى 70% على جميع المنتجات. اطلب الآن واستفد من التوصيل المجاني",
        "Promotion spéciale solde d'été livraison gratuite partout"
    )

    private val OTP_SEEDS = listOf(
        "Google: Votre code de vérification est 492018. Ne le partagez avec personne pour votre sécurité",
        "WhatsApp: votre code de sécurité est 823-109. Ne communiquez jamais ce code à un tiers",
        "Votre mot de passe à usage unique (OTP) pour valider votre transaction est 602931. Valable 5 minutes",
        "Your verification code is 381902. Do not share this code with anyone for your security",
        "رمز التحقق الخاص بك هو 940281. لا تشارك هذا الرمز مع أي شخص",
        "Code de confirmation d'accès: 719203"
    )

    private val LEGITIMATE_SEEDS = listOf(
        "Salut, tu es bien rentré hier soir ? On se voit comme prévu demain ?",
        "Bonjour, est-ce que le dossier est prêt pour la réunion de 14h ?",
        "Je t'ai préparé les documents, je te les dépose en sortant du travail",
        "Hi, are we still meeting today for coffee? Let me know when you're free",
        "Thanks for your help earlier, I really appreciate it!",
        "Salam, wesh rak labas? nchoufou ba3dhana ghedwa nchallah",
        "سلام خويا، واش راك لاباس؟ العائلة كامل بخير؟"
    )

    // Pre-computed normalized centroids for each threat category
    val BANKING_CENTROID: FloatArray by lazy { computeCentroid(BANKING_PHISHING_SEEDS) }
    val DELIVERY_CENTROID: FloatArray by lazy { computeCentroid(DELIVERY_PHISHING_SEEDS) }
    val EMERGENCY_CENTROID: FloatArray by lazy { computeCentroid(EMERGENCY_SCAM_SEEDS) }
    val CRYPTO_CENTROID: FloatArray by lazy { computeCentroid(CRYPTO_LOTTERY_SEEDS) }
    val SPAM_CENTROID: FloatArray by lazy { computeCentroid(COMMERCIAL_SPAM_SEEDS) }
    val OTP_CENTROID: FloatArray by lazy { computeCentroid(OTP_SEEDS) }
    val LEGITIMATE_CENTROID: FloatArray by lazy { computeCentroid(LEGITIMATE_SEEDS) }

    private fun computeCentroid(seeds: List<String>): FloatArray {
        val sumVec = FloatArray(OrbisTokenizer.EMBEDDING_DIM)
        for (seed in seeds) {
            val vec = OrbisTokenizer.encodeToEmbedding(seed)
            for (i in sumVec.indices) {
                sumVec[i] += vec[i]
            }
        }
        return OrbisVectorMath.normalizeL2(sumVec)
    }

    // High-risk heuristic indicators
    val SUSPICIOUS_DOMAINS = listOf(
        "bit.ly", "tinyurl.com", "is.gd", "t.co", "rb.gy", "cutt.ly", "shorturl.at",
        "vip", "top", "xyz", "online", "tk", "ml", "ga", "cf", "gq"
    )

    val URGENCY_TRIGGERS_FR = listOf(
        "bloqu", "suspend", "immediat", "urgent", "resili", "24h", "sous 48h", "amende", "penalite", "frais", "interdit"
    )

    val URGENCY_TRIGGERS_EN = listOf(
        "blocked", "suspended", "immediately", "urgent", "action required", "within 24h", "penalty", "locked", "unauthorized"
    )

    val URGENCY_TRIGGERS_AR = listOf(
        "حظر", "معلق", "فورا", "عاجل", "إيقاف", "غرامة", "تجميد", "غير مصرح"
    )

    val IMPERSONATION_TRIGGERS = listOf(
        "coucou maman", "salut papa", "bonjour maman", "bonjour papa",
        "mon nouveau numero", "mon nouveau numéro", "tel casse", "tél cassé", "tel dans l'eau", "tél dans l'eau",
        "hi mum", "hi dad", "hello mom", "my new number", "dropped my phone", "broke my phone",
        "salam mama", "salam papa", "numero jdid", "numéro jdid", "tksar le telephone",
        "ضاعلي التيليفون", "تكسر تيليفوني", "رقمي الجديد", "هادا رقمي الجديد", "عيطيلي فايبر ولا واتساب"
    )
}
