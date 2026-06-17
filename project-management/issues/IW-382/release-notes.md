# Úklid běžících procesů při odstranění pracovního stromu

**Issue:** IW-382
**Datum:** 17. 6. 2026

Odstranění pracovního stromu příkazem `./iw rm` nyní samo zastaví procesy, které k danému stromu patří, dříve než smaže jeho adresář. Doteď příkaz adresář odstranil, ale procesy nastartované projektem běžely dál — postupně se hromadily osiřelé démony sestavovacích nástrojů, které zbytečně zatěžovaly procesor i paměť. Jediným řešením bylo pracovní strom znovu vytvořit, ručně procesy ukončit a teprve potom strom odstranit. Tento krok teď odpadá.

O nejčastější případy se postará vestavěný úklid, který nevyžaduje žádné nastavení. Pokud k pracovnímu stromu patří běžící démon nástroje Mill, server Bloop nebo spuštěná sestava `docker compose`, `./iw rm` je před odstraněním stromu korektně zastaví. Úklid je vždy „best-effort“ — pokud se některý proces nepodaří ukončit, odstranění stromu se tím nezastaví a uživatel dostane jen upozornění.

Projekty, které potřebují zastavit i vlastní procesy (například vývojové servery nebo aplikační služby), si mohou doplnit vlastní úklidové kroky. `./iw rm` je objeví a spustí automaticky ve stanoveném pořadí. Pokud některý z těchto kroků ohlásí chybu, odstranění se přeruší a pracovní strom zůstane zachován, aby nedošlo ke ztrátě rozpracované práce; běžná upozornění naopak odstranění nebrání a jen se vypíší.

Chování zůstává plně zpětně kompatibilní: tam, kde žádný úklid není potřeba, pracuje `./iw rm` přesně jako dosud. Vestavěný úklid lze v případě potřeby vypnout volbou `cleanup.builtin = false` v konfiguraci projektu.
