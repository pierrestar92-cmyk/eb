# eBesucher Monitor

Android-App zum Überwachen von zwei eBesucher-Surflinks über die offizielle eBesucher-API.

## Version 0.1

- zeigt automatisch die **2 zuletzt aktiven Surflinks**
- Status je Surfbar anhand von `lastActivity`
  - **Aktiv:** letzte Aktivität vor höchstens 3 Minuten
  - **Verzögert:** 3 bis 10 Minuten
  - **Offline / keine Aktivität:** mehr als 10 Minuten
- zeigt verdiente **BTP heute** je Surflink
- zeigt verdiente **BTP der letzten 60 Minuten** je Surflink
- zeigt die Summe der heutigen BTP der beiden angezeigten Surflinks
- manuelle Aktualisierung
- automatische Aktualisierung alle 2 Minuten, solange die App geöffnet ist
- zeigt das von der API gemeldete Rate-Limit an
- benötigt nur eBesucher-Benutzername + API-Key, **nicht das eBesucher-Passwort**

## eBesucher API aktivieren

Die API muss im eBesucher-Mitgliederbereich unter **Benutzername und API** aktiviert werden. Danach den dortigen API-Key in der App eintragen.

Die App verwendet die offiziellen Endpunkte für:

- `visitor_exchange.json/surflinks`
- `visitor_exchange.json/surflink/{name}/earnings_hourly/{date}`
- `visitor_exchange.json/surflink/{name}/earnings/{from}-{to}`

## Android-Kompatibilität

- minSdk 21 / Android 5.0+
- targetSdk 35
- Java 11 Quellcode
- keine externen Laufzeitbibliotheken

Damit ist v0.1 auch für ältere Android-Geräte bewusst schlank gehalten.

## APK automatisch bauen

Bei jedem Push auf `main` startet GitHub Actions den Workflow **Android APK**.

Nach einem erfolgreichen Build:

1. GitHub Repository öffnen
2. **Actions** öffnen
3. neuesten Lauf von **Android APK** auswählen
4. unter **Artifacts** `eBesucher-Monitor-v0.1-debug` herunterladen
5. ZIP entpacken und `app-debug.apk` auf dem Android-Gerät installieren

## Datenschutz

Benutzername und API-Key werden in v0.1 ausschließlich lokal in den Android-App-Einstellungen (`SharedPreferences`) gespeichert und nicht an einen eigenen Server übertragen. Netzwerkzugriffe gehen direkt an `https://www.ebesucher.de/api/`.

## Grenzen von v0.1

- Die App ist ein **Monitor**. Sie startet oder repariert eine ausgefallene Surfbar nicht automatisch.
- Es werden derzeit immer die zwei zuletzt aktiven Surflinks ausgewählt. Eine feste Auswahl bestimmter Surflinks folgt in einer späteren Version.
- Die automatische Aktualisierung läuft nur, solange die App aktiv ist.

Dieses Projekt ist ein privates Monitoring-Werkzeug und kein offizielles Produkt von eBesucher.de.
