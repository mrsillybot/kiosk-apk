# Køleskabs-kiosk (Android-APK)

Kilde, build og udgivelse af appen. Hører til Mr Silly-systemet (privat repo `life-automations`, som ejer nøglen og Max' side).

En lille app til den gamle Samsung-tablet på køleskabet. Den viser en liste af offentlige Claude artifact-sider på skift
(Alex' brief, Max' side, madplan …), holder skærmen tændt i valgte tidsrum og slukker den ellers. Kun standardbiblioteker
og Android-SDK'et, ingen Gradle. Hjemmebrug, ikke til Play Store (Alex droppede det 1/11-2026: konto 25 USD + 12 testere i 14 dage).

## Brug
- Installér: åbn https://raw.githubusercontent.com/mrsillybot/kiosk-apk/main/kiosk.apk på tabletten (offentligt, intet login), tillad "ukendte apps", åbn filen. Nye versioner installeres oven på de gamle (samme nøgle, indstillinger beholdes).
- Indstillinger: tryk 5 gange i nederste højre hjørne.
- Sider: flueben, navn, URL, ugedage, og "lør–man kun hver anden uge" (Alex' weekender, anker 30/10-2026, jf. `familie/samvaer.md` i det private repo; mandag tæller som morgenen efter weekenden).
  Standard: Alex (alle dage); Max, Laura og Stella er "børnesider" (flueben "Børneside"): de vises om morgenen (før 12:00), når børnene har sovet hos Alex (tor, fre, og lør, søn og mandagen efter i hans weekender), og om eftermiddagen (fra 12:00), når de er hos ham (ons, tor, og fre–søn i hans weekender). Mandag eftermiddag er de hos Mor, så siden vises ikke. Ugedage og "lør–man" ignoreres for børnesider. Gamle installationer flyttes automatisk. Madplan og To uger ligger slukket.
- Tidsrum: hver række har start/slut (urskive) og ugedage (Hverdage / Weekend / Alle). Standard man–fre 6:00–8:15 og 15:00–22:00, lør–søn 7:00–22:00. Et tidsrum hører til den dag det starter.
- Stryg til siden for næste/forrige side; siden glider ind, og timeren starter forfra. Standard 60 sekunder pr. side.
- Glidende skift: appen holder én WebView pr. side (den viste og dens to naboer) indlæst i baggrunden og genindlæser dem usynligt, når de er over 5 minutter gamle, så der aldrig vises en spinner. Koster hukommelse: bliver tabletten langsom, så skær ned til kun den viste side + næste (`preload` i `MainActivity`).
- Uden for tidsrummene er skærmen sort og sover efter tablettens egen timeout (sæt den til 30–60 s). En alarm tænder skærmen ved næste start. Tænd-knappen eller et tryk på den sorte skærm viser siderne i 3 minutter.
- Uden netværk: "Ingen forbindelse", nyt forsøg hvert 20. sekund.
- Skærmretning: liggende som standard (Max' side og briefen er lavet til at fylde en liggende tablet uden at scrolle); kan sættes til stående eller følge tabletten.
- Skærmlys dæmpes 20:00–6:00 til 25 % (kan ændres). Fastlås (skærmfastgørelse) er valgfri og slået fra; kræver Android-indstillingen "Skærmfastgørelse".
- Claude-bjælken i toppen skjules med et script efter hver sideindlæsning (bygger på skallens egen `data-header-hidden`; kan gå i stykker, hvis Claude ændrer siden).
- **Morgen/eftermiddag (5/11-2026):** Lister viser kun den del af dagens opgaver, vi er i (før kl. 12 morgenopgaverne, fra 12 eftermiddagsopgaverne), aldrig begge samtidig; "Tal" tæller hele dagen. plan.json har pr. barn `{morning: [...], afternoon: [...]}` (ældre historik er en flad liste).
- **Pad thai-slide (4/11-2026):** Mads opskriftsside (artifact `5ERad3ajxr13cogLnu72NW`) ligger som valgfri side "Pad thai" i listen, slukket som standard; slå flueben til i Indstillinger for at få den i karrusellen.
- **Skrivekø (2/11-2026):** Lister-siden gemmer ændringer pr. fil i en kø (localStorage) og viser dem som gemt; den sendes én ad gangen og prøves igen hver 8. sekund (også ved 409/422: siden læser så om). "N venter på net" vises øverst. Siden henter repoet hvert 60. sekund, kun når den er synlig. Listebroen (`Lists`) sidder kun på den indbyggede side.
- **Lister og børnenes opgaver (2/11-2026):** listerne (indkøb, opgaver, børnenes afkrydsning) er små JSON-filer i det private repo `mrsillybot/life-lists` (format: docs/lists.md i life-automations). Appen har siden `app/assets/lister.html` indbygget: siden "Lister" (url `kiosk://lister`) i karrusellen, (telefonen har sin egen app, se nedenfor). `ListsBridge` (`Lists.call`) læser repoet som én zip og skriver én fil ad gangen (`ListsRepo`, GitHub contents API). Token: Indstillinger → "Lister og opgaver (GitHub)": fine-grained, kun `life-lists`, Contents: Read and write, uden udløb. Børnesiden kalder `KioskBridge.report(json)`, og `Reporter` skriver det som `log/<dato>/<barn>-<hash>.json` (kø på tabletten, til der er net og token). Ingen claude.ai-login og ingen database.
- **Telefon-appen "Mr Silly" (2/11-2026, `phone/`, pakke `dk.mrsilly.phone`):** en lille app kun til Alex med knapper i bunden: **Indkøb · Brief · Opgaver · Tal** (⚙ = token og opdatering). Indkøb/Opgaver/Tal er den samme indbyggede side som køleskabet (`app/assets/lister.html`, i app-tilstand `?app`, hvor siden selv ingen faner har) med `ListsBridge`; Brief er Alex' åbne brief-artifact med Claude-bjælken skjult. Delt kode: `ListsRepo`, `ListsBridge`, `Updater`, `ClaudeBar`. Samme signeringsnøgle som kiosk-appen. Bygges med `./build-phone.sh`, udgives med `./release-phone.sh bump` (`phone.apk` + `phone-version.json`); opdaterer sig selv som køleskabet (søger højst hver 6. time ved start). Installér: https://raw.githubusercontent.com/mrsillybot/kiosk-apk/main/phone.apk. Token (samme som på køleskabet) indtastes under ⚙.
- **KioskBridge:** børnesiden (`kid.html`) kalder `KioskBridge.skip()` (ikke her resten af dagen) eller `KioskBridge.skipUntil(720)` (ikke her før kl. 12), når den rigtige kalender siger noget andet end appens egen rytme. Rytmen er reserve, hvis broen ikke virker.

## Automatisk opdatering
- Appen tjekker højst hver 6. time `https://raw.githubusercontent.com/mrsillybot/kiosk-apk/main/version.json` (dette repo). Er `versionCode` højere end den installerede, hentes APK'en og Android viser "Opdatér" – ét tryk (på nyere Android evt. ingen).
- Første gang beder appen om tilladelsen "Installér ukendte apps" for sig selv (Android-indstilling); slå den til.
- Udgiv en ny version: `./release.sh bump` (se Bygge og udgive).
- Fastlås (skærmfastgørelse) kan blokere opdateringsdialogen; slå den fra hvis opdateringer ikke går igennem.
- Selve v2 (første med opdatering) skal installeres manuelt; derefter går det af sig selv.

## Bygge og udgive
- Struktur: `app/` (manifest, `src/`, `res/`), `tools/make_icon.py` (tegner ikonet), `build.sh`, `release.sh`, `VERSION`, og i roden `kiosk.apk` + `version.json` (det tabletten henter).
- `./test.sh` kører logik-testene (`tests/`, Config: tidsrum, ugedage, Max-rytmen, spring-over, dæmpning) på en almindelig JVM. Sæt `git config core.hooksPath .githooks` én gang pr. clone, så kører pre-push-hooken dem; `release.sh` kører dem også. UI, alarm, WebView og opdatering kan ikke testes her, kun på tabletten.
- `./build.sh` bygger `kiosk.apk`. Kræver `$ANDROID_HOME` (platform 34 + build-tools 34.0.0 + cmdline-tools) og at netværket tillader `dl.google.com`.
- **Signeringsnøglen ligger ikke her** (repoet er offentligt): `kiosk/debug.keystore` i det private `life-automations`. `build.sh` finder den på `/home/user/life-automations/kiosk/debug.keystore`, eller sæt `KIOSK_KEYSTORE`. Lav aldrig en ny nøgle: så kan tabletten ikke opdatere oven på den gamle.
- `./release.sh bump` hæver `VERSION`, bygger, committer og pusher; tabletten henter den selv (højst hver 6. time).
- d8 8.2.2 + JDK 21 crasher på anonyme og indre (ikke-statiske) klasser: brug statiske nested klasser eller lambdaer.
- Arbejder du fra en chat: tilføj dette repo med `add_repo` (push) ved siden af `life-automations`.

## Status og åbne punkter (1/11-2026)
Appen er kun bygget, aldrig kørt på en enhed. Test på tabletten og ret:
1. Alarmen om morgenen: på Android 10+ kan en alarm ikke altid åbne appen i baggrunden. Mangler skærmen at tænde kl. 6/15, så lav fuldskærms-notifikation eller bed om "vis oven på andre apps". Spørg efter tablettens Android-version.
2. KioskBridge: ukendt om broen når ind i Claudes sandkasse-iframe. Vises Max' side på en dag hvor han er hos Karen, så svigter den.
3. Gammel WebView/Chrome på tabletten kan vise siderne forkert: opdatér i Play Butik.
4. Start efter genstart kan blokeres af Samsungs batteri-optimering.
5. Dæmpning og fastlås er uprøvede.

## Idéer (ikke bygget)
- Hente dagens sideliste fra en lille fil (opdateret af morgenrutinen), så ferier/bytter følger kalenderen uden ændringer i appen. KioskBridge er første skridt.
- Automatisk opdatering af APK'en (nu hentes den manuelt fra GitHub).
- PIN på indstillingerne (nu skjult bag 5 tryk i hjørnet).
- Tablet: begræns opladning til ca. 85 % (Samsung batteriindstilling) mod hævet batteri.
- Play Store hvis det alligevel ønskes: `.aab`, release-nøgle, nyeste target-API, privatlivs-link, fjern personlige standardlinks.
