# Release Notes: Deterministické fázové lifecycle příkazy (IW-238)

## Shrnutí

Nové příkazy `phase-start`, `phase-commit`, `phase-pr` a `phase-advance` zapouzdřují kompletní životní cyklus implementační fáze do jednoduchých CLI volání. Dříve museli LLM agenti při práci na fázích ručně sestavovat ~20 git/gh příkazů -- správné pořadí, formátování commit zpráv, vytváření větví, push a PR. Každý krok znamenal riziko chyby a plýtvání tokeny na boilerplate bash. Nové příkazy tuto deterministickou orchestraci přesouvají do testovaného Scala kódu.

## Nové příkazy

### `iw phase-start <phase-number> [--issue-id ID]`

Zahájí novou implementační fázi. Vytvoří sub-branch `{feature-branch}-phase-{NN}` z aktuální feature větve, zaznamená baseline SHA a aktualizuje review-state na "implementing". Automaticky detekuje issue ID z názvu větve (nebo jej přijme jako argument).

**Výstup (JSON):** `issueId`, `phaseNumber`, `branch`, `baselineSha`

### `iw phase-commit --title TITLE [--items ITEM1,ITEM2,...] [--issue-id ID]`

Commitne práci v aktuální fázi. Provede `git add -A`, vytvoří strukturovaný commit s titulkem a volitelnými položkami (bullet list), a aktualizuje fázový task soubor -- nastaví Phase Status na "Complete" a označí splněné `[impl]` úkoly jako `[reviewed]`.

**Výstup (JSON):** `issueId`, `phaseNumber`, `commitSha`, `filesCommitted`, `message`

### `iw phase-pr --title TITLE [--body BODY] [--batch] [--issue-id ID]`

Pushne fázovou větev a vytvoří pull request (GitHub) nebo merge request (GitLab). Automaticky detekuje forge typ z git remote URL. V `--batch` režimu navíc PR rovnou mergne (squash), smaže vzdálenou větev, přepne zpět na feature branch a synchronizuje ji s remote.

**Výstup (JSON):** `issueId`, `phaseNumber`, `prUrl`, `headBranch`, `baseBranch`, `merged`

### `iw phase-advance [--issue-id ID] [--phase-number N]`

Ověří, že fázový PR byl mergnut, a poté synchronizuje feature branch s remote. Slouží jako záchranný příkaz pro případy, kdy `phase-pr --batch` selže uprostřed operace nebo kdy PR mergnul člověk ručně.

**Výstup (JSON):** `issueId`, `phaseNumber`, `branch`, `previousBranch`, `headSha`

## Technické detaily

### Doménový model (`model/`)

- **PhaseNumber** -- opaque type zajišťující validní čísla fází (1-99, zero-padded)
- **PhaseBranch** -- value object pro odvození názvu sub-branch + pattern matching extractor
- **CommitMessage** -- čistá konstrukce strukturovaných commit zpráv
- **PhaseTaskFile** -- parsování a přepis markdown checkboxů ve fázových task souborech
- **PhaseOutput** -- typované case classes pro JSON výstup všech příkazů
- **ForgeType** -- enum GitHub/GitLab s detekcí z remote URL
- **PhaseArgs** -- čisté parsování CLI argumentů, resolve issue ID a phase number z větve
- **FileUrlBuilder** -- konstrukce browseable URL pro soubory na GitHub/GitLab

### Infrastrukturní vrstva (`adapters/`)

- **GitAdapter** rozšířen o: `createAndCheckoutBranch`, `stageAll`, `commit`, `push`, `fetchAndReset`, `getFullHeadSha`, `diffNameOnly`, `checkoutBranch`
- **GitHubClient** rozšířen o: `createPullRequest`, parsování odpovědí `gh pr create`
- **GitLabClient** rozšířen o: `createMergeRequest`, parsování odpovědí `glab mr create`
- **ReviewStateAdapter** -- sdílený adaptér pro atomické read/merge/validate/write operace nad `review-state.json`

### Architektura

Implementace dodržuje FCIS (Functional Core, Imperative Shell) vzor: čistá logika bez I/O v `model/`, I/O operace v `adapters/`, orchestrace v `commands/`. Všechny adaptéry vrací `Either[String, T]` pro konzistentní error handling. Výstup na stdout je strojově čitelný JSON, lidsky čitelné zprávy jdou na stderr.

## Zlepšení workflow

- **Jeden příkaz místo dvaceti:** Agent zavolá `iw phase-start 1`, pracuje, zavolá `iw phase-commit --title "..."` a `iw phase-pr --title "..." --batch`. Hotovo.
- **Spolehlivost:** Deterministische kroky jsou v testovaném Scala kódu, ne v ad-hoc bash skriptech generovaných agentem.
- **Úspora tokenů:** Agent neplýtvá tokeny na sestavování git/gh příkazů a řešení chyb v shellu.
- **Podpora GitHub i GitLab:** Automatická detekce forge typu z remote URL, funguje s oběma platformami.
- **Strukturovaný výstup:** JSON výstup umožňuje agentům spolehlivě parsovat výsledky (SHA, PR URL, počet souborů).
- **Automatická aktualizace review-state:** Příkazy průběžně aktualizují review-state.json, takže dashboard vždy ukazuje aktuální stav.
- **Batch režim:** `phase-pr --batch` provede push, vytvoření PR, merge, checkout a sync v jednom volání.
