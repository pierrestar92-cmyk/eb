# eBesucher Monitor

Android-App zum Überwachen von zwei eBesucher-Surflinks über die offizielle eBesucher-API.

## Version 0.1.2

### Neu

- Status basiert nicht mehr auf `lastActivity`, sondern auf dem **BTP-Zuwachs zwischen erfolgreichen Prüfungen**
- **VERDIENT AKTUELL**: BTP sind seit der letzten Prüfung gestiegen
- **KEIN NEUER VERDIENST**: seit der letzten Prüfung kein Zuwachs, aber ausdrücklich kein Offline-Nachweis
- **MÖGLICHER STILLSTAND**: mindestens 15 Minuten ohne neuen BTP-Zuwachs
- erste Messung wird als **NOCH NICHT BEURTEILBAR** behandelt
- `lastActivity` bleibt nur als API-Zusatzinfo sichtbar
- lokale Baseline und Zeitpunkt des letzten BTP-Zuwachses werden gespeichert
- Rate-Limit-Countdown deaktiviert den Button bis eine vollständige Prüfung wieder sicher möglich ist
- API-Limit wird verständlich als `x von y Anfragen verfügbar` dargestellt
- die Zeit der **letzten erfolgreichen Aktualisierung** bleibt auch bei Fehlern erhalten

### Weitere Funktionen

- zeigt automatisch die 2 zuletzt aktiven Surflinks aus der API
- BTP heute je Surflink
- BTP der letzten 60 Minuten je Surflink
- Gesamt-BTP der beiden angezeigten Surflinks
- manuelle Aktualisierung
- automatische Aktualisierung alle 2 Minuten, solange die App geöffnet ist
- benötigt nur eBesucher-Benutzername + API-Key, nicht das normale Passwort

## Warum die Statuslogik geändert wurde

`lastActivity` erwies sich nicht als zuverlässiger Live-Heartbeat für den geöffneten Browser. Eine Surfbar kann sichtbar weiter Webseiten laden, während der API-Wert deutlich älter ist. Deshalb behauptet die App seit v0.1.2 nicht mehr allein aufgrund dieses Feldes, eine Surfbar sei offline.

Der Monitor beantwortet stattdessen die belastbarere Frage: **Steigen die verdienten BTP weiter?**

Auch länger ausbleibender Verdienst ist kein sicherer Beweis dafür, dass der Browser gestoppt ist. Darum lautet die stärkste Warnung bewusst nur **MÖGLICHER STILLSTAND**.

## eBesucher API

Verwendete Endpunkte:

- `visitor_exchange.json/surflinks`
- `visitor_exchange.json/surflink/{name}/earnings_hourly/{date}`
- `visitor_exchange.json/surflink/{name}/earnings/{from}-{to}`

Eine vollständige Aktualisierung benötigt derzeit 5 Requests. Der lokale Client begrenzt sich auf 5 Requests pro rollender Minute und lässt damit Reserve zum gemeldeten Server-Limit von 7 Requests pro Minute.

## Android-Kompatibilität

- minSdk 21 / Android 5.0+
- targetSdk 35
- Java 11
- keine externen Laufzeitbibliotheken

## APK bauen

Bei jedem Push auf `main` startet GitHub Actions den Workflow **Android APK**.

Nach erfolgreichem Build befindet sich unter **Artifacts**:

`eBesucher-Monitor-v0.1.2-debug`

## Datenschutz

Benutzername, API-Key und lokale Messwerte werden ausschließlich in den Android-App-Einstellungen (`SharedPreferences`) gespeichert. Netzwerkzugriffe gehen direkt an `https://www.ebesucher.de/api/`.

## Grenzen

- Die App überwacht, sie startet oder repariert eine Surfbar nicht automatisch.
- Sie kann über die API nicht sicher erkennen, ob ein Browser-Tab technisch noch geöffnet ist.
- `MÖGLICHER STILLSTAND` bedeutet nur: über längere Zeit kein neuer BTP-Zuwachs.
- Die automatische Aktualisierung läuft nur, solange die App aktiv ist.

Dieses Projekt ist ein privates Monitoring-Werkzeug und kein offizielles Produkt von eBesucher.de.
