# Release Notes: Formální schéma a nástroje pro review-state.json

**Issue:** IW-136
**Datum:** 2026-01-28

Soubor review-state.json slouží ke komunikaci stavu workflow procesů s dashboardem iw-cli. Dosud nebyla jeho struktura formálně definována — workflow nástroje musely odhadovat správný formát a chyby se projevily až při zobrazení na dashboardu. Tato změna zavádí jasný kontrakt a dva nové příkazy, které zajišťují správnost těchto souborů.

Nyní existuje formální definice schématu ve formátu JSON Schema, která přesně popisuje všechna povinná i volitelná pole, jejich typy a povolené hodnoty. Tato definice slouží jako autoritativní reference pro všechny, kdo se souborem review-state.json pracují — ať už jde o workflow nástroje, dashboard nebo ruční úpravy.

Nový příkaz `iw validate-review-state` umožňuje ověřit, zda je daný soubor platný. Stačí zadat cestu k souboru a příkaz vypíše buď potvrzení úspěchu, nebo srozumitelný seznam zjištěných problémů s uvedením konkrétního pole a popisu chyby. Validaci lze provést i nad daty ze standardního vstupu pomocí přepínače `--stdin`.

Druhý nový příkaz `iw write-review-state` umožňuje vytvořit platný soubor review-state.json přímo z příkazové řádky. Automaticky doplní identifikátor issue z aktuální větve, aktuální SHA commitu a časové razítko. Před zápisem provede validaci — pokud by výsledný soubor nebyl platný, příkaz jej nezapíše a místo toho zobrazí chyby. Podporuje zadání hodnot pomocí přepínačů (např. `--status`, `--phase`, `--artifact`) nebo načtení kompletního JSON ze standardního vstupu.

Díky těmto změnám mohou workflow nástroje spolehlivě vytvářet a ověřovat stavové soubory, aniž by musely znát interní detaily iw-cli. Schéma je verzované — přidání nových volitelných polí nevyžaduje změnu verze, pouze odstranění nebo změna typu existujícího pole vede k novému číslu verze.
