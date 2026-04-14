# IW-340: Automatický commit stavu review po aktualizaci

**Datum:** 2026-04-14

Příkazy `review-state update` a `review-state write` nově podporují volitelný přepínač `--commit`. Když je tento přepínač uveden, nástroj po zápisu souboru review-state.json automaticky provede jeho uložení do gitu. Uživatel tak nemusí ručně commitovat změny stavu a při přechodu mezi fázemi workflow nedochází k chybám způsobeným neuloženými změnami v pracovním adresáři.

Přepínač je čistě volitelný a stávající chování příkazů se bez něj nijak nemění. Pokud by commit z jakéhokoli důvodu selhal, hlavní operace (zápis souboru) proběhne úspěšně a uživatel je na neúspěšný commit upozorněn varováním. Příkaz v takovém případě neskončí chybou.

Tato změna je plně zpětně kompatibilní a nevyžaduje žádné úpravy na straně uživatele.
