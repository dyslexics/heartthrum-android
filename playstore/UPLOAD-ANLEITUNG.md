# Heartthrum in der Play Console anlegen — Schritt für Schritt

Konto: legasthenie@gmail.com → https://play.google.com/console

## 1. App anlegen
„App erstellen" → Name **Heartthrum – Pulsmesser**, Standardsprache Deutsch, **App** (kein Spiel), **Kostenlos**. Häkchen bei Richtlinien setzen.

## 2. Store-Eintrag (Hauptseite)
Alle Texte aus `LISTING-TEXTE.md` (DE anlegen, EN als Übersetzung hinzufügen).
- App-Symbol: `play_icon_512.png`
- Funktionsgrafik: `play_feature_1024x500.png`
- Telefon-Screenshots: die 5 PNGs aus `screenshots/`
- Kategorie: **Gesundheit & Fitness** · E-Mail: marioengel@me.com · Website: https://heartthrum.com

## 3. Fragebögen (linke Seitenleiste „Inhalt der App")
- **Datenschutzerklärung:** https://heartthrum.com/privacy.html
- **App-Zugriff:** Alle Funktionen ohne Anmeldung verfügbar
- **Werbung:** Nein, keine Werbung
- **Datensicherheit:** „Keine Daten erhoben oder weitergegeben" (stimmt wörtlich — die App hat keine Internet-Berechtigung)
- **Einstufung des Inhalts (Content Rating):** Kategorie „Dienstprogramm/Produktivität/…", alle Fragen Nein → USK 0/Everyone
- **Gesundheits-Apps:** Als Wellness-App deklarieren (KEINE Medizinprodukt-Funktion). Falls gefragt: keine Verbindung zu Health-Diensten.
- **Zielgruppe:** 18+ (einfachste Variante, vermeidet Family-Policy-Auflagen)

## 4. Release
„Test und Release" → **Interner Test** (empfohlen als erster Schritt) → Neues Release → `app-release.aab` hochladen.
Beim ersten Upload fragt Google nach „Play App Signing" → **Zustimmen** (Google verwaltet den App-Signaturschlüssel, unser Keystore bleibt der Upload-Key).
Dich selbst als internen Tester eintragen (E-Mail-Liste) → Link auf dem Android-Gerät öffnen → testen (v. a. die Puls-Messung real!).
Danach: „Produktion" → Release erstellen → dasselbe AAB → Prüfung dauert meist 1–3 Tage.

**Hinweis:** Falls die Console vor der Produktion einen geschlossenen Test mit 12 Testern über 14 Tage verlangt (Regel für neuere Privatkonten), sag mir Bescheid — dann organisieren wir die 12 Tester über den EÖDL/DVLD-Kreis.

## Dateien in diesem Ordner
- `app-release.aab` — die signierte App (versionCode 1)
- `play_icon_512.png`, `play_feature_1024x500.png`
- `screenshots/` — 5 Telefon-Screenshots (1080×2400)
- `LISTING-TEXTE.md` — alle Store-Texte DE/EN

## Optional: API-Zugang für Henry (einmalig, ~10 Min)
Damit ich künftig AABs selbst hochladen kann: Play Console → Einstellungen → API-Zugriff → Google-Cloud-Projekt verknüpfen → Dienstkonto erstellen → JSON-Schlüssel herunterladen → Schlüssel mir geben → in der Console dem Dienstkonto „Releases verwalten" gewähren.
