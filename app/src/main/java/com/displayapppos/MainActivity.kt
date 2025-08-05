package com.displayapppos

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity(), DisplayManager.DisplayListener {

    private lateinit var layoutSaisie: LinearLayout
    private lateinit var vueWeb: WebView
    private lateinit var layoutChargement: LinearLayout
    private lateinit var barreProgres: ProgressBar
    private lateinit var texteChargement: TextView
    private lateinit var boutonRetour: Button
    private lateinit var texteStatut: TextView
    private lateinit var champUrl: EditText

    // Gestion des écrans
    private lateinit var gestionnaireDaffichage: DisplayManager
    private var ecranSecondaire: Display? = null
    private var presentationWeb: PresentationWeb? = null
    private var estUnAppareilSunmi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialiser le gestionnaire d'affichage
        gestionnaireDaffichage = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

        // Détecter le type d'appareil SUNMI
        detecterTypeAppareil()

        configurerParametresGlobauxWebView()
        setContentView(creerMiseEnPage())

        // Initialiser les écrans
        initialiserEcrans()
    }

    override fun onResume() {
        super.onResume()
        gestionnaireDaffichage.registerDisplayListener(this, null)

        if (ecranSecondaire != null && presentationWeb == null) {
            creerPresentationWeb()
        }

        if (::vueWeb.isInitialized) {
            vueWeb.onResume()
        }
        presentationWeb?.reprendre()
    }

    override fun onPause() {
        super.onPause()
        gestionnaireDaffichage.unregisterDisplayListener(this)

        if (::vueWeb.isInitialized) {
            vueWeb.onPause()
        }
        presentationWeb?.suspendre()
    }

    override fun onDestroy() {
        super.onDestroy()
        presentationWeb?.dismiss()
        if (::vueWeb.isInitialized) {
            vueWeb.clearHistory()
            vueWeb.clearCache(true)
            vueWeb.destroy()
        }
    }

    override fun onDisplayAdded(displayId: Int) {
        Log.d("AFFICHAGE", "Écran ajouté : $displayId")
        initialiserEcrans()
        mettreAJourStatutEcran()
    }

    override fun onDisplayChanged(displayId: Int) {
        Log.d("AFFICHAGE", "Écran modifié : $displayId")
        mettreAJourStatutEcran()
    }

    override fun onDisplayRemoved(displayId: Int) {
        Log.d("AFFICHAGE", "Écran supprimé : $displayId")
        if (ecranSecondaire?.displayId == displayId) {
            presentationWeb?.dismiss()
            presentationWeb = null
            ecranSecondaire = null
        }
        mettreAJourStatutEcran()
    }

    private fun detecterTypeAppareil() {
        // Vérification de la présence d'un appareil SUNMI
        val modele = android.os.Build.MODEL.lowercase()
        val fabricant = android.os.Build.MANUFACTURER.lowercase()
        val produit = android.os.Build.PRODUCT.lowercase()
        val marque = android.os.Build.BRAND.lowercase()

        estUnAppareilSunmi = fabricant.contains("sunmi") ||
                modele.contains("sunmi") ||
                produit.contains("sunmi") ||
                marque.contains("sunmi") ||
                modele.contains("t2") ||
                modele.contains("p2") ||
                modele.contains("t1") ||
                modele.contains("p1")

        Log.d("APPAREIL", "=== INFORMATIONS APPAREIL ===")
        Log.d("APPAREIL", "Fabricant: $fabricant")
        Log.d("APPAREIL", "Modèle: $modele")
        Log.d("APPAREIL", "Produit: $produit")
        Log.d("APPAREIL", "Marque: $marque")
        Log.d("APPAREIL", "Type détecté : ${if (estUnAppareilSunmi) "SUNMI" else "Standard"}")
        Log.d("APPAREIL", "==============================")
    }

    private fun initialiserEcrans() {
        Log.d("AFFICHAGE", "=== DÉBUT INITIALISATION ÉCRANS ===")

        val ecrans = gestionnaireDaffichage.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        Log.d("AFFICHAGE", "Écrans de présentation trouvés : ${ecrans.size}")

        // Liste de tous les écrans
        val tousEcrans = gestionnaireDaffichage.displays
        Log.d("AFFICHAGE", "TOTAL écrans disponibles : ${tousEcrans.size}")

        tousEcrans.forEachIndexed { index, ecran ->
            Log.d("AFFICHAGE", "ÉCRAN $index:")
            Log.d("AFFICHAGE", "  - ID: ${ecran.displayId}")
            Log.d("AFFICHAGE", "  - Nom: '${ecran.name}'")
            Log.d("AFFICHAGE", "  - État: ${ecran.state}")
            Log.d("AFFICHAGE", "  - Taille: ${ecran.mode.physicalWidth}x${ecran.mode.physicalHeight}")
            Log.d("AFFICHAGE", "  - Type: ${if (ecran.displayId == 0) "PRINCIPAL" else "SECONDAIRE"}")
            Log.d("AFFICHAGE", "  - Flags: ${ecran.flags}")
        }

        Log.d("AFFICHAGE", "--- ÉCRANS DE PRÉSENTATION ---")
        ecrans.forEachIndexed { index, ecran ->
            Log.d("AFFICHAGE", "PRÉSENTATION $index:")
            Log.d("AFFICHAGE", "  - ID : ${ecran.displayId}")
            Log.d("AFFICHAGE", "  - Nom : '${ecran.name}'")
            Log.d("AFFICHAGE", "  - État : ${ecran.state}")
        }

        // Forcer l'utilisation de l'écran avec ID = 1 pour SUNMI (écran client)
        ecranSecondaire = if (estUnAppareilSunmi) {
            Log.d("AFFICHAGE", "MODE SUNMI: Recherche de l'écran client...")

            // Essayer d'abord l'écran avec ID = 1
            val ecranClient = tousEcrans.find { it.displayId == 1 }
            if (ecranClient != null) {
                Log.d("AFFICHAGE", "ÉCRAN CLIENT TROUVÉ avec ID=1: ${ecranClient.name}")
                ecranClient
            } else {
                // Sinon chercher dans les écrans de présentation
                val ecranPresentation = ecrans.find {
                    it.name.contains("HDMI", ignoreCase = true) ||
                            it.name.contains("presentation", ignoreCase = true) ||
                            it.name.contains("customer", ignoreCase = true) ||
                            it.name.contains("client", ignoreCase = true)
                }
                if (ecranPresentation != null) {
                    Log.d("AFFICHAGE", "ÉCRAN PRÉSENTATION TROUVÉ: ${ecranPresentation.name}")
                    ecranPresentation
                } else {
                    Log.w("AFFICHAGE", "AUCUN ÉCRAN CLIENT/PRÉSENTATION TROUVÉ")
                    ecrans.firstOrNull()
                }
            }
        } else {
            Log.d("AFFICHAGE", "MODE STANDARD: Utilisation du premier écran de présentation")
            ecrans.firstOrNull()
        }

        if (ecranSecondaire != null) {
            Log.d("AFFICHAGE", "✅ ÉCRAN SECONDAIRE SÉLECTIONNÉ:")
            Log.d("AFFICHAGE", "  - Nom: '${ecranSecondaire?.name}'")
            Log.d("AFFICHAGE", "  - ID: ${ecranSecondaire?.displayId}")
            Log.d("AFFICHAGE", "  - État: ${ecranSecondaire?.state}")
            creerPresentationWeb()
        } else {
            Log.w("AFFICHAGE", "❌ AUCUN ÉCRAN SECONDAIRE DÉTECTÉ")
            Log.w("AFFICHAGE", "Vérifiez que l'écran client SUNMI est bien connecté et allumé")
        }

        mettreAJourStatutEcran()
        Log.d("AFFICHAGE", "=== FIN INITIALISATION ÉCRANS ===")
    }

    private fun creerPresentationWeb() {
        ecranSecondaire?.let { ecran ->
            try {
                // Supprimer la présentation précédente si elle existe
                presentationWeb?.dismiss()

                presentationWeb = PresentationWeb(this, ecran)
                presentationWeb?.show()
                Log.d("AFFICHAGE", "Présentation web créée avec succès sur écran ${ecran.displayId}")
            } catch (e: WindowManager.InvalidDisplayException) {
                Log.e("AFFICHAGE", "Erreur lors de la création de la présentation : ${e.message}")
                presentationWeb = null
                ecranSecondaire = null
            } catch (e: Exception) {
                Log.e("AFFICHAGE", "Erreur inattendue : ${e.message}")
                presentationWeb = null
            }
        }
    }

    private fun mettreAJourStatutEcran() {
        if (::texteStatut.isInitialized) {
            texteStatut.text = if (ecranSecondaire != null) {
                "✅ Écran secondaire connecté (${ecranSecondaire?.name}) - ID: ${ecranSecondaire?.displayId}"
            } else {
                "⚠️ Aucun écran secondaire trouvé"
            }

            texteStatut.setTextColor(
                if (ecranSecondaire != null)
                    Color.parseColor("#10B981")
                else
                    Color.parseColor("#F59E0B")
            )
        }
    }

    private fun configurerParametresGlobauxWebView() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    private fun creerMiseEnPage(): FrameLayout {
        val contexte = this

        val miseEnPagePrincipale = FrameLayout(contexte).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            background = creerFondDegrade()
        }

        layoutSaisie = LinearLayout(contexte).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            setPadding(60, 60, 60, 60)
            background = creerArriereplanCarte()
            elevation = 20f

            val params = layoutParams as FrameLayout.LayoutParams
            params.setMargins(40, 40, 40, 40)
            layoutParams = params

            visibility = View.VISIBLE
            alpha = 1f
        }

        val texteTitre = TextView(contexte).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "🌐 Navigateur SUNMI Double Écran"
            textSize = 36f
            gravity = Gravity.CENTER

            paint.shader = LinearGradient(
                0f, 0f, 0f, textSize,
                intArrayOf(
                    Color.parseColor("#667eea"),
                    Color.parseColor("#764ba2")
                ),
                null,
                Shader.TileMode.CLAMP
            )

            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 48)
        }

        val sousTitre = TextView(contexte).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = if (estUnAppareilSunmi) {
                "Interface de contrôle - WebView sur écran client"
            } else {
                "Navigateur web avec affichage sur écran secondaire"
            }
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#6B7280"))
            typeface = Typeface.DEFAULT
            setPadding(0, 0, 0, 32)
        }

        val miseEnPageHorizontale = LinearLayout(contexte).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
        }

        champUrl = EditText(contexte).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                140,
                1f
            )
            hint = "Entrez l'adresse du site web..."
            setText("")
            setSingleLine(true)
            textSize = 22f
            setPadding(40, 30, 40, 30)
            background = creerArriereplanChampTexte()
            setTextColor(Color.parseColor("#1F2937"))
            setHintTextColor(Color.parseColor("#6B7280"))
            elevation = 8f
            typeface = Typeface.DEFAULT
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
            gravity = Gravity.CENTER_VERTICAL
        }

        val boutonAfficher = Button(contexte).apply {
            layoutParams = LinearLayout.LayoutParams(
                220,
                140
            )
            text = if (estUnAppareilSunmi) "CLIENT" else "AFFICHER"
            textSize = 20f
            background = creerArriereplanBouton()
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            elevation = 12f
            letterSpacing = 0.1f
            definirMarges(20, 0, 0, 0)
            isAllCaps = true
            includeFontPadding = false
            gravity = Gravity.CENTER
        }

        val layoutStatut = LinearLayout(contexte).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 32, 0, 0)
        }

        texteStatut = TextView(contexte).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "🔄 Vérification des écrans..."
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#6B7280"))
            typeface = Typeface.DEFAULT_BOLD
        }

        val boutonRafraichir = Button(contexte).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 24
            }
            text = "🔄 ACTUALISER LES ÉCRANS"
            textSize = 14f
            background = creerArriereplanBoutonSecondaire()
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT
            setPadding(32, 16, 32, 16)

            setOnClickListener {
                Log.d("AFFICHAGE", "Actualisation des écrans...")
                Toast.makeText(context, "Recherche d'écrans en cours...", Toast.LENGTH_SHORT).show()

                presentationWeb?.dismiss()
                presentationWeb = null
                ecranSecondaire = null

                initialiserEcrans()
            }
        }

        layoutChargement = LinearLayout(contexte).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            setPadding(48, 48, 48, 48)
            background = creerArriereplanCarte()
            elevation = 16f
            visibility = View.GONE
        }

        texteChargement = TextView(contexte).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "Chargement en cours..."
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#667eea"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }

        barreProgres = ProgressBar(contexte).apply {
            layoutParams = LinearLayout.LayoutParams(80, 80).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        // WebView principale - uniquement pour les appareils sans écran secondaire
        vueWeb = WebView(contexte).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    afficherEtatChargement(url ?: "")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    masquerEtatChargement()
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    afficherEtatErreur("Erreur de chargement")
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    handler?.proceed()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    mettreAJourProgres(newProgress)
                }
            }

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                allowContentAccess = true
                allowFileAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                userAgentString = "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"
                defaultFontSize = 18
                minimumFontSize = 12
            }

            // Ajout de cette ligne pour améliorer les performances
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
        }

        boutonRetour = Button(contexte).apply {
            layoutParams = FrameLayout.LayoutParams(
                140,
                80,
                Gravity.TOP or Gravity.START
            )
            text = "← RETOUR"
            textSize = 16f
            background = creerArriereplanBoutonSecondaire()
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            elevation = 8f
            visibility = View.GONE

            val marge = 16
            (layoutParams as FrameLayout.LayoutParams).setMargins(marge, marge, 0, 0)

            setOnClickListener {
                retournerAuMenu()
            }
        }

        // Action du bouton principal
        boutonAfficher.setOnClickListener {
            val url = champUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                fermerClavier(champUrl)
                val urlFinale = normaliserUrl(url)

                if (ecranSecondaire != null && presentationWeb != null) {
                    // Chargement sur l'écran secondaire UNIQUEMENT
                    Log.d("WEBVIEW", "=== CHARGEMENT SUR ÉCRAN SECONDAIRE ===")
                    Log.d("WEBVIEW", "URL: $urlFinale")
                    Log.d("WEBVIEW", "Écran cible: ${ecranSecondaire?.name} (ID: ${ecranSecondaire?.displayId})")

                    // S'ASSURER que la WebView principale est CACHÉE
                    vueWeb.visibility = View.GONE
                    Log.d("WEBVIEW", "WebView principale masquée")

                    presentationWeb?.chargerUrl(urlFinale)

                    // Affichage de l'état de chargement sur l'écran principal
                    afficherEtatChargement(urlFinale)
                } else {
                    // Chargement sur la WebView principale (appareils sans écran secondaire)
                    Log.d("WEBVIEW", "=== CHARGEMENT SUR ÉCRAN PRINCIPAL ===")
                    Log.d("WEBVIEW", "URL: $urlFinale")
                    Log.d("WEBVIEW", "Raison: ecranSecondaire=${ecranSecondaire != null}, presentation=${presentationWeb != null}")

                    afficherTransitionChargement()
                    vueWeb.clearCache(false)
                    vueWeb.loadUrl(urlFinale)
                }
            } else {
                Toast.makeText(this@MainActivity, "Veuillez saisir une URL", Toast.LENGTH_SHORT).show()
            }
        }

        champUrl.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                boutonAfficher.performClick()
                true
            } else {
                false
            }
        }

        // Assemblage
        layoutStatut.addView(texteStatut)
        layoutStatut.addView(boutonRafraichir)

        layoutSaisie.addView(texteTitre)
        layoutSaisie.addView(sousTitre)
        miseEnPageHorizontale.addView(champUrl)
        miseEnPageHorizontale.addView(boutonAfficher)
        layoutSaisie.addView(miseEnPageHorizontale)
        layoutSaisie.addView(layoutStatut)

        layoutChargement.addView(texteChargement)
        layoutChargement.addView(barreProgres)

        miseEnPagePrincipale.addView(layoutSaisie)
        miseEnPagePrincipale.addView(layoutChargement)
        miseEnPagePrincipale.addView(vueWeb)
        miseEnPagePrincipale.addView(boutonRetour)

        return miseEnPagePrincipale
    }

    private fun afficherEtatChargement(url: String) {
        runOnUiThread {
            layoutChargement.visibility = View.VISIBLE
            texteChargement.text = if (ecranSecondaire != null) {
                "Chargement sur écran client..."
            } else {
                "Chargement en cours..."
            }
            texteChargement.setTextColor(Color.parseColor("#667eea"))
        }
    }

    private fun masquerEtatChargement() {
        runOnUiThread {
            layoutChargement.animate()
                .alpha(0f)
                .setDuration(500)
                .withEndAction {
                    layoutChargement.visibility = View.GONE

                    if (ecranSecondaire == null) {
                        // Affichage de la WebView pour les appareils normaux
                        vueWeb.visibility = View.VISIBLE
                        boutonRetour.visibility = View.VISIBLE
                        layoutSaisie.visibility = View.GONE
                        vueWeb.alpha = 0f
                        vueWeb.animate()
                            .alpha(1f)
                            .setDuration(300)
                            .start()
                    } else {
                        // Maintien de l'interface de contrôle pour l'écran secondaire
                        boutonRetour.visibility = View.VISIBLE
                        layoutSaisie.visibility = View.VISIBLE

                        // Ajout d'un message de succès
                        Toast.makeText(this@MainActivity, "Page chargée sur écran client !", Toast.LENGTH_SHORT).show()
                    }
                }
                .start()
        }
    }

    private fun afficherEtatErreur(erreur: String) {
        runOnUiThread {
            texteChargement.text = erreur
            texteChargement.setTextColor(Color.parseColor("#EF4444"))

            // Masquer le chargement après 3 secondes
            Handler(Looper.getMainLooper()).postDelayed({
                if (layoutChargement.visibility == View.VISIBLE) {
                    layoutChargement.visibility = View.GONE
                }
            }, 3000)
        }
    }

    private fun mettreAJourProgres(progres: Int) {
        runOnUiThread {
            if (layoutChargement.visibility == View.VISIBLE) {
                texteChargement.text = if (ecranSecondaire != null) {
                    "Chargement sur écran client... $progres%"
                } else {
                    "Chargement... $progres%"
                }
            }
        }
    }

    private fun afficherTransitionChargement() {
        layoutSaisie.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(200)
            .withEndAction {
                layoutChargement.visibility = View.VISIBLE
                layoutChargement.alpha = 0f
                layoutChargement.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            }
            .start()
    }

    private fun retournerAuMenu() {
        Log.d("WEBVIEW", "Retour au menu principal")

        if (ecranSecondaire != null) {
            // Effacer l'écran secondaire
            presentationWeb?.chargerUrl("about:blank")

            boutonRetour.visibility = View.GONE
            layoutChargement.visibility = View.GONE
            layoutSaisie.visibility = View.VISIBLE
            layoutSaisie.alpha = 1f
        } else {
            // Pour les appareils normaux
            vueWeb.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    vueWeb.visibility = View.GONE
                    boutonRetour.visibility = View.GONE
                    layoutChargement.visibility = View.GONE
                    layoutSaisie.visibility = View.VISIBLE
                    layoutSaisie.alpha = 0f
                    layoutSaisie.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .start()
                }
                .start()
        }
    }

    private fun fermerClavier(view: View) {
        val gestionnaire = getSystemService(Context.INPUT_METHOD_SERVICE)
        if (gestionnaire != null) {
            (gestionnaire as InputMethodManager).hideSoftInputFromWindow(view.windowToken, 0)
        }
        view.clearFocus()
    }

    private fun normaliserUrl(url: String): String {
        val urlNettoyee = url.trim()
        return when {
            urlNettoyee.startsWith("http://") || urlNettoyee.startsWith("https://") -> urlNettoyee
            else -> "https://$urlNettoyee"
        }
    }

    // Fonctions d'aide pour l'interface utilisateur
    private fun creerFondDegrade(): android.graphics.drawable.Drawable {
        val couleurs = intArrayOf(
            Color.parseColor("#667eea"),
            Color.parseColor("#764ba2"),
            Color.parseColor("#f093fb"),
            Color.parseColor("#f5576c")
        )
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            colors = couleurs
            orientation = android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
        }
    }

    private fun creerArriereplanCarte(): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 32f
            setColor(Color.parseColor("#FFFFFF"))
            setStroke(1, Color.parseColor("#E5E7EB"))
        }
    }

    private fun creerArriereplanChampTexte(): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 20f
            setColor(Color.parseColor("#FFFFFF"))
            setStroke(3, Color.parseColor("#D1D5DB"))
        }
    }

    private fun creerArriereplanBouton(): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 20f
            colors = intArrayOf(
                Color.parseColor("#667eea"),
                Color.parseColor("#764ba2")
            )
            orientation = android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
        }
    }

    private fun creerArriereplanBoutonSecondaire(): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 16f
            setColor(Color.parseColor("#6B7280"))
        }
    }

    private fun View.definirMarges(gauche: Int, haut: Int, droite: Int, bas: Int) {
        val params = layoutParams as LinearLayout.LayoutParams
        params.setMargins(gauche, haut, droite, bas)
        layoutParams = params
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (ecranSecondaire != null) {
            retournerAuMenu()
        } else if (vueWeb.visibility == View.VISIBLE) {
            if (vueWeb.canGoBack()) {
                vueWeb.goBack()
            } else {
                retournerAuMenu()
            }
        } else {
            retournerAuMenu()
        }
    }

    // Classe Presentation pour afficher la WebView sur l'écran secondaire
    inner class PresentationWeb(context: Context, display: Display) : Presentation(context, display) {

        private lateinit var vueWeb: WebView
        private lateinit var layoutChargement: LinearLayout
        private lateinit var texteChargement: TextView
        private lateinit var barreProgres: ProgressBar

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val layoutPrincipal = creerLayoutPresentation()
            setContentView(layoutPrincipal)

            Log.d("PRESENTATION", "Présentation web créée pour écran ${display.displayId}")
        }

        private fun creerLayoutPresentation(): FrameLayout {
            val contexte = this@PresentationWeb.context

            val layoutPrincipal = FrameLayout(contexte).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.BLACK)
            }

            layoutChargement = LinearLayout(contexte).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
                setPadding(64, 64, 64, 64)
                background = creerFondCarte()
                elevation = 16f
            }

            texteChargement = TextView(contexte).apply {
                text = "Chargement en cours..."
                textSize = 28f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#667eea"))
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 24)
            }

            barreProgres = ProgressBar(contexte).apply {
                layoutParams = LinearLayout.LayoutParams(120, 120).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            }

            vueWeb = WebView(contexte).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                visibility = View.GONE

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        layoutChargement.visibility = View.VISIBLE
                        vueWeb.visibility = View.GONE
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        layoutChargement.visibility = View.GONE
                        vueWeb.visibility = View.VISIBLE

                        // Notification à l'Activity principale
                        this@MainActivity.masquerEtatChargement()
                    }

                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        super.onReceivedError(view, request, error)
                        texteChargement.text = "Erreur de connexion"
                        texteChargement.setTextColor(Color.parseColor("#EF4444"))

                        this@MainActivity.afficherEtatErreur("Erreur de chargement")
                    }

                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                        handler?.proceed()
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        if (layoutChargement.visibility == View.VISIBLE) {
                            texteChargement.text = "Chargement... $newProgress%"
                        }
                        this@MainActivity.mettreAJourProgres(newProgress)
                    }
                }

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    allowContentAccess = true
                    allowFileAccess = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                    // Paramètres spécifiques à l'écran secondaire (généralement plus grand)
                    userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"
                    defaultFontSize = 20
                    minimumFontSize = 16

                    // Amélioration des performances pour les grands écrans
                    cacheMode = WebSettings.LOAD_DEFAULT
                    setRenderPriority(WebSettings.RenderPriority.HIGH)
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                }
            }

            layoutChargement.addView(texteChargement)
            layoutChargement.addView(barreProgres)

            layoutPrincipal.addView(vueWeb)
            layoutPrincipal.addView(layoutChargement)

            return layoutPrincipal
        }

        private fun creerFondCarte(): android.graphics.drawable.Drawable {
            return android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 32f
                setColor(Color.parseColor("#FFFFFF"))
                setStroke(2, Color.parseColor("#E5E7EB"))
            }
        }

        fun chargerUrl(url: String) {
            if (::vueWeb.isInitialized) {
                vueWeb.loadUrl(url)
                Log.d("PRESENTATION", "Chargement de l'URL sur l'écran secondaire : $url")
            }
        }

        fun suspendre() {
            if (::vueWeb.isInitialized) {
                vueWeb.onPause()
            }
        }

        fun reprendre() {
            if (::vueWeb.isInitialized) {
                vueWeb.onResume()
            }
        }

        override fun onDisplayChanged() {
            super.onDisplayChanged()
            Log.d("PRESENTATION", "Propriétés de l'écran modifiées")
        }

        override fun onDisplayRemoved() {
            super.onDisplayRemoved()
            Log.d("PRESENTATION", "Écran supprimé - la présentation sera fermée")
        }
    }
}