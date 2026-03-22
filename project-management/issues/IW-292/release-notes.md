# IW-292: Workflow příkazy nyní správně commitují review-state.json

## Shrnutí

Tři workflow příkazy (`phase-pr`, `phase-merge`, `phase-advance`) aktualizovaly soubor `review-state.json` na disku, ale nikdy nezapsaly změny do gitu. Výsledkem byl špinavý pracovní strom, který blokoval automatizované workflow (např. `batch-implement`), protože ty před spuštěním vyžadují čistý stav repozitáře.

## Opravené problémy

- **`phase-pr`** — po aktualizaci stavu review nyní automaticky commituje `review-state.json` (opraveno v obou větvích: batch i standardní).
- **`phase-merge`** — po závěrečné aktualizaci stavu na „phase_merged" nyní commituje `review-state.json`.
- **`phase-advance`** — po aktualizaci stavu nyní commituje `review-state.json`.

## Sdílená infrastruktura

Do `GitAdapter` byla přidána metoda `stageFiles`, která umožňuje cíleně přidat konkrétní soubory do staging area. Tím se zamezí nechtěnému zahrnutí nesouvisejících souborů, ke kterému by mohlo dojít při použití `stageAll`.

## Testy

- 4 unit testy pro `GitAdapter.stageFiles`
- 3 E2E testy (BATS) — jeden pro každý opravený příkaz
