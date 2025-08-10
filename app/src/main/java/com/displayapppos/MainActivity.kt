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

    // Handler pour les vérifications périodiques
    private val verificationHandler = Handler(Looper.getMainLooper())
    private var verificationRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialiser le gestionnaire d'affichage
        gestionnaireDaffichage = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

        // Détecter le type d'appareil SUNMI
        detecterTypeAppareil()

        configurerParametresGlobauxWebView()
        setContentView(creerMiseEnPage())

        // Initialiser les écrans avec délai
        Handler(Looper.getMainLooper()).postDelayed({
            initialiserEcrans()
            demarrerVerificationPeriodique()
        }, 1000)
    }

    override fun onResume() {
        super.onResume()
        gestionnaireDaffichage.registerDisplayListener(this, null)

        // Vérifier et recréer la présentation si nécessaire
        Handler(Looper.getMainLooper()).postDelayed({
            if (ecranSecondaire != null && presentationWeb == null) {
                creerPresentationWeb()
            }
        }, 500)

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

        // Arrêter les vérifications périodiques
        arreterVerificationPeriodique()

        presentationWeb?.dismiss()
        if (::vueWeb.isInitialized) {
            vueWeb.clearHistory()
            vueWeb.clearCache(true)
            vueWeb.destroy()
        }
    }

    override fun onDisplayAdded(displayId: Int) {
        Log.d("AFFICHAGE", "Écran ajouté : $displayId")
        Handler(Looper.getMainLooper()).postDelayed({
            initialiserEcrans()
        }, 1000)
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
        val modele = android.os.Build.MODEL.lowercase()
        val fabricant = android.os.Build.MANUFACTURER.lowercase()
        val produit = android.os.Build.PRODUCT.lowercase()
        val marque = android.os.Build.BRAND.lowercase()
        val device = android.os.Build.DEVICE.lowercase()
        val hardware = android.os.Build.HARDWARE.lowercase()

        Log.d("APPAREIL", "=== INFORMATIONS COMPLÈTES APPAREIL ===")
        Log.d("APPAREIL", "Fabricant: $fabricant")
        Log.d("APPAREIL", "Modèle: $modele")
        Log.d("APPAREIL", "Produit: $produit")
        Log.d("APPAREIL", "Marque: $marque")
        Log.d("APPAREIL", "Device: $device")
        Log.d("APPAREIL", "Hardware: $hardware")
        Log.d("APPAREIL", "Board: ${android.os.Build.BOARD.lowercase()}")
        Log.d("APPAREIL", "Bootloader: ${android.os.Build.BOOTLOADER.lowercase()}")

        // تشخیص دقیق‌تر دستگاه‌های SUNMI
        estUnAppareilSunmi = fabricant.contains("sunmi") ||
                modele.contains("sunmi") ||
                produit.contains("sunmi") ||
                marque.contains("sunmi") ||
                device.contains("sunmi") ||
                hardware.contains("sunmi") ||
                // مدل‌های مشخص SUNMI
                modele.contains("t2") ||
                modele.contains("p2") ||
                modele.contains("t1") ||
                modele.contains("p1") ||
                modele.contains("v2") ||
                modele.contains("k2") ||
                modele.contains("l2") ||
                // بررسی خصوصیات سیستم
                android.os.Build.BOARD.lowercase().contains("sunmi") ||
                android.os.Build.BOOTLOADER.lowercase().contains("sunmi")

        Log.d("APPAREIL", "Type détecté (première phase) : ${if (estUnAppareilSunmi) "SUNMI" else "Standard"}")

        // بررسی وجود ویژگی‌های خاص SUNMI
        try {
            val packageManager = packageManager
            val hasSunmiFeature = packageManager.hasSystemFeature("sunmi.customer.display") ||
                    packageManager.hasSystemFeature("sunmi.dual.display") ||
                    packageManager.hasSystemFeature("com.sunmi.customer.display")
            Log.d("APPAREIL", "SUNMI System Feature: $hasSunmiFeature")

            if (hasSunmiFeature) {
                estUnAppareilSunmi = true
            }
        } catch (e: Exception) {
            Log.w("APPAREIL", "Erreur lors de la vérification des features: ${e.message}")
        }

        Log.d("APPAREIL", "FINAL - Est un appareil SUNMI: $estUnAppareilSunmi")
        Log.d("APPAREIL", "==============================")
    }

    private fun initialiserEcrans() {
        Log.d("AFFICHAGE", "=== DÉBUT INITIALISATION ÉCRANS ===")

        try {
            // دریافت همه صفحه نمایش‌ها
            val tousEcrans = gestionnaireDaffichage.displays
            val ecransPresentacion = gestionnaireDaffichage.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)

            Log.d("AFFICHAGE", "TOTAL écrans disponibles : ${tousEcrans.size}")
            Log.d("AFFICHAGE", "Écrans de présentation : ${ecransPresentacion.size}")

            // لاگ تفصیلی از همه صفحه نمایش‌ها
            tousEcrans.forEachIndexed { index, ecran ->
                try {
                    Log.d("AFFICHAGE", "ÉCRAN $index:")
                    Log.d("AFFICHAGE", "  - ID: ${ecran.displayId}")
                    Log.d("AFFICHAGE", "  - Nom: '${ecran.name}'")
                    Log.d("AFFICHAGE", "  - État: ${ecran.state}")
                    Log.d("AFFICHAGE", "  - Type: ${getDisplayType(ecran)}")
                    Log.d("AFFICHAGE", "  - Taille: ${ecran.mode.physicalWidth}x${ecran.mode.physicalHeight}")
                    Log.d("AFFICHAGE", "  - Resolution: ${getDisplayResolution(ecran)}")
                    Log.d("AFFICHAGE", "  - Flags: ${ecran.flags}")
                    Log.d("AFFICHAGE", "  - isValid: ${ecran.isValid}")

                    // بررسی ویژگی‌های اضافی
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        try {
                            Log.d("AFFICHAGE", "  - HDR: ${ecran.hdrCapabilities != null}")
                        } catch (e: Exception) {
                            Log.d("AFFICHAGE", "  - HDR: N/A")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("AFFICHAGE", "Erreur lors du log de l'écran $index: ${e.message}")
                }
            }

            // انتخاب صفحه نمایش مناسب برای SUNMI
            ecranSecondaire = if (estUnAppareilSunmi) {
                Log.d("AFFICHAGE", "MODE SUNMI: Recherche de l'écran client...")

                // استراتژی چندگانه برای پیدا کردن صفحه نمایش مناسب
                var ecranMناسب: Display? = null

                // 1. جستجو بر اساس ID (معمولاً 1 برای صفحه مشتری)
                ecranMناسب = tousEcrans.find { it.displayId == 1 && it.isValid }
                if (ecranMناسب != null) {
                    Log.d("AFFICHAGE", "✓ پیدا شد با ID=1: ${ecranMناسب.name}")
                }

                // 2. جستجو در صفحه‌های ارائه
                if (ecranMناسب == null && ecransPresentacion.isNotEmpty()) {
                    ecranMناسب = ecransPresentacion.find { it.isValid }
                    if (ecranMناسب != null) {
                        Log.d("AFFICHAGE", "✓ پیدا شد در presentation displays: ${ecranMناسب.name}")
                    }
                }

                // 3. جستجو بر اساس نام
                if (ecranMناسب == null) {
                    ecranMناسب = tousEcrans.find { display ->
                        display.isValid && display.displayId != 0 && (
                                display.name.contains("HDMI", ignoreCase = true) ||
                                        display.name.contains("customer", ignoreCase = true) ||
                                        display.name.contains("client", ignoreCase = true) ||
                                        display.name.contains("secondary", ignoreCase = true) ||
                                        display.name.contains("external", ignoreCase = true) ||
                                        display.name.contains("副屏", ignoreCase = true) ||
                                        display.name.contains("客户", ignoreCase = true)
                                )
                    }
                    if (ecranMناسب != null) {
                        Log.d("AFFICHAGE", "✓ پیدا شد بر اساس نام: ${ecranMناسب.name}")
                    }
                }

                // 4. آخرین تلاش: هر صفحه غیر از اصلی
                if (ecranMناسب == null) {
                    ecranMناسب = tousEcrans.find { it.displayId != 0 && it.isValid }
                    if (ecranMناسب != null) {
                        Log.d("AFFICHAGE", "✓ پیدا شد صفحه دوم: ${ecranMناسب.name}")
                    }
                }

                ecranMناسب
            } else {
                Log.d("AFFICHAGE", "MODE STANDARD: Utilisation du premier écran de présentation")
                ecransPresentacion.firstOrNull { it.isValid }
            }

            if (ecranSecondaire != null) {
                Log.d("AFFICHAGE", "✅ ÉCRAN SECONDAIRE SÉLECTIONNÉ:")
                Log.d("AFFICHAGE", "  - Nom: '${ecranSecondaire?.name}'")
                Log.d("AFFICHAGE", "  - ID: ${ecranSecondaire?.displayId}")
                Log.d("AFFICHAGE", "  - État: ${ecranSecondaire?.state}")
                Log.d("AFFICHAGE", "  - Valid: ${ecranSecondaire?.isValid}")

                // تاخیر کوتاه قبل از ایجاد presentation
                Handler(Looper.getMainLooper()).postDelayed({
                    creerPresentationWeb()
                }, 800)
            } else {
                Log.w("AFFICHAGE", "❌ AUCUN ÉCRAN SECONDAIRE DÉTECTÉ")

                // در صورت عدم وجود صفحه دوم، بررسی مجدد با تاخیر
                if (!estUnAppareilSunmi) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        Log.d("AFFICHAGE", "Nouvelle tentative de détection d'écran...")
                        initialiserEcrans()
                    }, 3000)
                }
            }

            mettreAJourStatutEcran()

        } catch (e: Exception) {
            Log.e("AFFICHAGE", "Erreur lors de l'initialisation des écrans: ${e.message}")
            Log.e("AFFICHAGE", "Stack trace: ${e.stackTrace.contentToString()}")
        }

        Log.d("AFFICHAGE", "=== FIN INITIALISATION ÉCRANS ===")
    }

    private fun getDisplayResolution(display: Display): String {
        return try {
            val metrics = android.util.DisplayMetrics()
            display.getMetrics(metrics)
            "${metrics.widthPixels}x${metrics.heightPixels} (${metrics.densityDpi} DPI)"
        } catch (e: Exception) {
            "N/A"
        }
    }

    private fun getDisplayType(display: Display): String {
        return when {
            display.displayId == 0 -> "PRIMARY"
            display.displayId == 1 -> "SECONDARY/CUSTOMER"
            else -> "EXTERNAL_${display.displayId}"
        }
    }

    private fun creerPresentationWeb() {
        ecranSecondaire?.let { ecran ->
            try {
                Log.d("AFFICHAGE", "Tentative de création de présentation sur écran ${ecran.displayId}")

                // بررسی validity صفحه نمایش
                if (!ecran.isValid) {
                    Log.e("AFFICHAGE", "L'écran ${ecran.displayId} n'est pas valide")
                    ecranSecondaire = null
                    mettreAJourStatutEcran()
                    return
                }

                // حذف presentation قبلی
                presentationWeb?.let { oldPresentation ->
                    try {
                        oldPresentation.dismiss()
                        Log.d("AFFICHAGE", "Ancienne présentation supprimée")
                    } catch (e: Exception) {
                        Log.w("AFFICHAGE", "Erreur lors de la suppression de l'ancienne présentation: ${e.message}")
                    }
                }
                presentationWeb = null

                // تاخیر کوتاه برای اطمینان از پاک‌سازی
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        // ایجاد presentation جدید
                        presentationWeb = PresentationWeb(this, ecran)

                        // تنظیمات اضافی برای دستگاه‌های POS
                        presentationWeb?.window?.let { window ->
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
                            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                window.attributes.layoutInDisplayCutoutMode =
                                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                            }
                        }

                        presentationWeb?.show()
                        Log.d("AFFICHAGE", "✅ Présentation web créée avec succès sur écran ${ecran.displayId}")

                        // تست اتصال با تاخیر بیشتر
                        Handler(Looper.getMainLooper()).postDelayed({
                            presentationWeb?.chargerUrl("data:text/html,<html><body style='background:linear-gradient(45deg, #667eea, #764ba2);color:white;text-align:center;font-size:48px;padding:100px;font-family:Arial;'>✅ CONNEXION RÉUSSIE<br><br>ÉCRAN CLIENT ACTIF<br><br>🌐 Prêt pour la navigation</body></html>")
                        }, 1500)

                    } catch (e: WindowManager.InvalidDisplayException) {
                        Log.e("AFFICHAGE", "InvalidDisplayException: ${e.message}")
                        Log.e("AFFICHAGE", "L'écran ${ecran.displayId} n'est plus disponible")
                        presentationWeb = null
                        ecranSecondaire = null
                        mettreAJourStatutEcran()

                        // تلاش مجدد بعد از 5 ثانیه
                        Handler(Looper.getMainLooper()).postDelayed({
                            initialiserEcrans()
                        }, 5000)

                    } catch (e: IllegalArgumentException) {
                        Log.e("AFFICHAGE", "IllegalArgumentException: ${e.message}")
                        presentationWeb = null

                    } catch (e: Exception) {
                        Log.e("AFFICHAGE", "Erreur inattendue lors de la création: ${e.message}")
                        Log.e("AFFICHAGE", "Stack trace: ${e.stackTrace.contentToString()}")
                        presentationWeb = null

                        // امتحان با صفحه نمایش دیگر
                        ecranSecondaire = null
                        Handler(Looper.getMainLooper()).postDelayed({
                            initialiserEcrans()
                        }, 3000)
                    }
                }, 300)

            } catch (e: Exception) {
                Log.e("AFFICHAGE", "Erreur générale: ${e.message}")
                presentationWeb = null
                ecranSecondaire = null
                mettreAJourStatutEcran()
            }
        } ?: run {
            Log.w("AFFICHAGE", "Aucun écran secondaire disponible pour créer la présentation")
        }
    }

    private fun demarrerVerificationPeriodique() {
        arreterVerificationPeriodique()

        verificationRunnable = object : Runnable {
            override fun run() {
                try {
                    if (ecranSecondaire != null && !ecranSecondaire!!.isValid) {
                        Log.w("AFFICHAGE", "Écran secondaire n'est plus valide, recherche d'un nouveau...")
                        ecranSecondaire = null
                        presentationWeb?.dismiss()
                        presentationWeb = null
                        initialiserEcrans()
                    }

                    // بررسی اگر presentation از بین رفته
                    if (ecranSecondaire != null && presentationWeb == null) {
                        Log.w("AFFICHAGE", "Présentation perdue, tentative de recréation...")
                        creerPresentationWeb()
                    }

                } catch (e: Exception) {
                    Log.e("AFFICHAGE", "Erreur lors de la vérification périodique: ${e.message}")
                }

                // بررسی هر 10 ثانیه
                verificationHandler.postDelayed(this, 10000)
            }
        }

        verificationRunnable?.let { verificationHandler.post(it) }
    }

    private fun arreterVerificationPeriodique() {
        verificationRunnable?.let { verificationHandler.removeCallbacks(it) }
        verificationRunnable = null
    }

    private fun mettreAJourStatutEcran() {
        if (::texteStatut.isInitialized) {
            runOnUiThread {
                texteStatut.text = if (ecranSecondaire != null) {
                    val status = if (presentationWeb != null) "CONNECTÉ ET ACTIF" else "CONNECTÉ"
                    "✅ Écran secondaire $status\n${ecranSecondaire?.name} - ID: ${ecranSecondaire?.displayId}"
                } else {
                    if (estUnAppareilSunmi) {
                        "⚠️ Écran client SUNMI non détecté\nVérifiez la connexion"
                    } else {
                        "⚠️ Aucun écran secondaire trouvé"
                    }
                }

                texteStatut.setTextColor(
                    if (ecranSecondaire != null && presentationWeb != null)
                        Color.parseColor("#10B981")
                    else if (ecranSecondaire != null)
                        Color.parseColor("#F59E0B")
                    else
                        Color.parseColor("#EF4444")
                )
            }
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
            text = if (estUnAppareilSunmi) {
                "🏪 SUNMI POS - Affichage Client"
            } else {
                "🌐 Navigateur Double Écran"
            }
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
                "Interface de contrôle - Affichage sur écran client"
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
            setText("https://www.google.com")
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

        val layoutBoutons = LinearLayout(contexte).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 24
            }
            gravity = Gravity.CENTER
        }

        val boutonRafraichir = Button(contexte).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = 16
            }
            text = "🔄 ACTUALISER"
            textSize = 14f
            background = creerArriereplanBoutonSecondaire()
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT
            setPadding(32, 16, 32, 16)

            setOnClickListener {
                Log.d("AFFICHAGE", "Actualisation manuelle des écrans...")
                Toast.makeText(context, "Recherche d'écrans en cours...", Toast.LENGTH_SHORT).show()

                presentationWeb?.dismiss()
                presentationWeb = null
                ecranSecondaire = null

                Handler(Looper.getMainLooper()).postDelayed({
                    initialiserEcrans()
                }, 500)
            }
        }

        val boutonTest = Button(contexte).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "🔍 TESTER"
            textSize = 14f
            background = creerArriereplanBoutonSecondaire()
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT
            setPadding(32, 16, 32, 16)

            setOnClickListener {
                if (ecranSecondaire != null && presentationWeb != null) {
                    presentationWeb?.chargerUrl("data:text/html,<html><body style='background:linear-gradient(45deg, #10B981, #059669);color:white;text-align:center;font-size:36px;padding:50px;font-family:Arial;'>🧪 TEST D'AFFICHAGE<br><br>⏰ ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}<br><br>✅ Écran fonctionnel</body></html>")
                    Toast.makeText(context, "Test envoyé sur écran client", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Aucun écran secondaire disponible", Toast.LENGTH_SHORT).show()
                }
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
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            // Ajout de cette ligne pour améliorer les performances
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
        }

        boutonRetour = Button(contexte).apply {
            layoutParams = FrameLayout.LayoutParams(
                160,
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
        layoutBoutons.addView(boutonRafraichir)
        layoutBoutons.addView(boutonTest)

        layoutStatut.addView(texteStatut)
        layoutStatut.addView(layoutBoutons)

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
            presentationWeb?.chargerUrl("data:text/html,<html><body style='background:linear-gradient(45deg, #667eea, #764ba2);color:white;text-align:center;font-size:48px;padding:100px;font-family:Arial;'>🏠 ÉCRAN CLIENT SUNMI<br><br>En attente...</body></html>")

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
                try {
                    vueWeb.loadUrl(url)
                    Log.d("PRESENTATION", "Chargement de l'URL sur l'écran secondaire : $url")
                } catch (e: Exception) {
                    Log.e("PRESENTATION", "Erreur lors du chargement de l'URL: ${e.message}")
                }
            }
        }

        fun suspendre() {
            if (::vueWeb.isInitialized) {
                try {
                    vueWeb.onPause()
                } catch (e: Exception) {
                    Log.e("PRESENTATION", "Erreur lors de la suspension: ${e.message}")
                }
            }
        }

        fun reprendre() {
            if (::vueWeb.isInitialized) {
                try {
                    vueWeb.onResume()
                } catch (e: Exception) {
                    Log.e("PRESENTATION", "Erreur lors de la reprise: ${e.message}")
                }
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