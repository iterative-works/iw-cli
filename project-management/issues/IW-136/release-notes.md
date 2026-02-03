# Release Notes: Formální schéma a nástroje pro review-state.json

**Issue:** IW-136
**Datum:** 2026-02-03

Soubor review-state.json slouží ke komunikaci stavu workflow procesů s dashboardem iw-cli. Dosud nebyla jeho struktura formálně definována — workflow nástroje musely odhadovat správný formát a chyby se projevily až při zobrazení na dashboardu. Tato změna zavádí jasný kontrakt a novou sadu příkazů, které zajišťují správnost těchto souborů a usnadňují práci s nimi.

Nyní existuje formální definice schématu ve formátu JSON Schema, která přesně popisuje všechna povinná i volitelná pole, jejich typy a povolené hodnoty. Tato definice slouží jako autoritativní reference pro všechny, kdo se souborem review-state.json pracují. Schéma bylo navrženo tak, aby oddělilo zobrazovací instrukce pro dashboard od logiky workflow — dashboard nyní pouze zobrazuje, co mu workflow řekne, místo aby interpretoval stavové kódy.

Pro práci se stavovými soubory jsou k dispozici tři nové příkazy pod jednotným názvem `iw review-state`:

Příkaz `iw review-state validate` ověří, zda je daný soubor platný. Stačí zadat cestu k souboru a příkaz vypíše buď potvrzení úspěchu, nebo srozumitelný seznam zjištěných problémů s uvedením konkrétního pole a popisu chyby. Validaci lze provést i nad daty ze standardního vstupu pomocí přepínače `--from-stdin`.

Příkaz `iw review-state write` umožňuje vytvořit kompletní platný soubor review-state.json přímo z příkazové řádky. Automaticky doplní identifikátor issue z aktuální větve, aktuální SHA commitu a časové razítko. Před zápisem provede validaci — pokud by výsledný soubor nebyl platný, příkaz jej nezapíše a místo toho zobrazí chyby. Podporuje zadání hodnot pomocí přepínačů nebo načtení kompletního JSON ze standardního vstupu.

Nově přibylo i `iw review-state update`, které umožňuje měnit jen některá pole existujícího stavového souboru, aniž byste museli znovu zadávat všechny údaje. Můžete například změnit pouze zprávu nebo přidat nový artifact, a zbytek zůstane zachován. To výrazně zjednodušuje workflow skripty, které dříve musely buď předávat všechna pole, nebo používat složité manipulace s JSON přes externí nástroje.

Díky těmto změnám mohou workflow nástroje spolehlivě vytvářet a ověřovat stavové soubory pomocí jednoduchých příkazů. Schéma je verzované — přidání nových volitelných polí nevyžaduje změnu verze, pouze odstranění nebo změna typu existujícího pole vede k novému číslu verze. Všechny tři příkazy jsou zdokumentovány s příklady použití a zárukou zpětné kompatibility v `docs/commands/review-state.md`.
