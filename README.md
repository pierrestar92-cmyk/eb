# eBesucher Monitor

Android-App zum Überwachen von zwei eBesucher-Surflinks über die offizielle eBesucher-API.

## Version 0.1.4

### Neu

- entfernt die irreführende 10-Minuten-Auswertung aus v0.1.3
- wertet die offizielle Stundenstatistik `earnings_hourly` direkt aus
- zeigt pro Surfbar:
  - **Heute laut API**
  - **Aktuelle Stunde**
  - **Vorherige Stunde**
  - `lastActivity` nur als Zusatzinfo
- ein positiver Wert in der laufenden Stunde wird als **BTP IN LAUFENDER STUNDE BESTÄTIGT** angezeigt
- 0 BTP oder ein fehlender Wert in der laufenden Stunde wird ausdrücklich **nicht** mehr als Stillstand oder Offline-Zustand bewertet
- Hinweis in der App: Webseite und CSV können bei der laufenden Stunde bereits weiter sein als die API
- vollständiger Refresh benötigt nur noch 3 API-Requests statt 5

## Warum die Logik erneut geändert wurde

Ein Vergleich mit der eBesucher-Webstatistik und einer exportierten CSV zeigte, dass die laufende Stunde auf der Webseite bereits BTP enthalten kann, während die öffentliche API für denselben Zeitraum noch 0 oder keinen aktuellen Wert liefert. Deshalb darf ein 0-Wert der laufenden Stunde nicht als Beweis für fehlende Vergütung verwendet werden.

Die App unterscheidet jetzt bewusst zwischen:

- **positiver Stundenwert vorhanden** → BTP-Verdienst für die laufende Stunde ist durch die API bestätigt
- **0 oder kein aktueller Stundenwert** → die API bestätigt die laufende Stunde noch nicht; kein Offline-Nachweis

## eBesucher API

Verwendete Endpunkte:

- `visitor_exchange.json/surflinks`
- `visitor_exchange.json/surflink/{name}/earnings_hourly/{date}?timezone=Europe/Berlin`

Die eBesucher-Dokumentation beschreibt die Stundenstatistik mit Werten von 1 bis 24. In der App wird deshalb die lokale Berliner Uhrzeit auf diese API-Schlüssel abgebildet.

## Android-Kompatibilität

- minSdk 21 / Android 5.0+
- targetSdk 35
- Java 11
- keine externen Laufzeitbibliotheken

## APK bauen

Bei jedem Push auf `main` startet GitHub Actions den Workflow **Android APK**.

Nach erfolgreichem Build befindet sich unter **Artifacts**:

`eBesucher-Monitor-v0.1.4-debug`

## Datenschutz

Benutzername und API-Key werden ausschließlich in den Android-App-Einstellungen (`SharedPreferences`) gespeichert. Netzwerkzugriffe gehen direkt an `https://www.ebesucher.de/api/`.

## Grenzen

- Die öffentliche API kann der Webseite bei der laufenden Stunde zeitlich hinterherhinken.
- Die App kann nicht sicher feststellen, ob ein Browser-Tab technisch geöffnet ist.
- `lastActivity` ist kein zuverlässiger Live-Heartbeat und wird deshalb nur als Zusatzinformation angezeigt.
- Die App überwacht, sie startet oder repariert eine Surfbar nicht automatisch.
- Die automatische Aktualisierung läuft nur, solange die App aktiv ist.

Dieses Projekt ist ein privates Monitoring-Werkzeug und kein offizielles Produkt von eBesucher.de.
