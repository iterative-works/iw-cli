# Nový příkaz: `iw phase-merge`

## Shrnutí

iw-cli nově obsahuje příkaz `phase-merge`, který automatizuje poslední krok implementační fáze — čekání na CI kontroly, sloučení pull requestu při úspěchu, a v případě selhání CI pokus o automatickou opravu pomocí agenta.

## K čemu to slouží

Při práci na fázích úkolu agent vytvoří PR pomocí `iw phase-pr`, ale před mergem je potřeba počkat na výsledek CI pipeline. Dříve se buď čekalo ručně, nebo se merge řešil inline logikou v `batch-implement`. Příkaz `phase-merge` tuto logiku zapouzdřuje do samostatného, testovaného příkazu s podporou:

- Automatického pollování CI statusů (GitHub i GitLab)
- Sloučení PR po úspěšném průchodu všech kontrol
- Opakovaných pokusů o opravu při selhání CI (přes agenta)
- Konfigurovatelného timeoutu, intervalu pollování a počtu opakování

## Klíčové schopnosti

- **Polling CI kontrol:** Periodicky dotazuje GitHub Checks API nebo GitLab Pipelines/Jobs API na stav všech kontrol přiřazených k PR
- **Automatický merge:** Při úspěchu všech kontrol provede squash merge, smaže vzdálenou větev a synchronizuje feature branch
- **Obnova po selhání:** Při selhání CI sestaví recovery prompt a spustí Claude agenta, který se pokusí problém opravit a pushne opravu — poté se polling opakuje
- **Podpora GitHub i GitLab:** Automatická detekce forge typu z remote URL nebo konfigurace projektu
- **Čisté rozhodování:** Doménová logika (vyhodnocení výsledků, rozhodnutí o opakování, sestavení promptu) je čistě funkční a pokrytá testy

## Použití

```bash
# Základní použití (na fázové sub-branch po vytvoření PR)
./iw phase-merge

# S vlastním timeoutem a intervalem pollování
./iw phase-merge --timeout 45m --poll-interval 1m

# Bez pokusů o opravu (selhání CI = okamžité ukončení)
./iw phase-merge --max-retries 0

# Více pokusů o opravu
./iw phase-merge --max-retries 3
```

Příkaz se spouští na fázové sub-branch (např. `IW-289-phase-03`) po tom, co `iw phase-pr` vytvořil pull request. URL pull requestu čte z `review-state.json`.

## Konfigurační parametry

| Parametr | Výchozí hodnota | Popis |
|---|---|---|
| `--timeout` | `30m` | Maximální doba čekání na CI kontroly (formát: `30s`, `5m`, `1h`) |
| `--poll-interval` | `30s` | Interval mezi dotazy na stav CI kontrol |
| `--max-retries` | `2` | Počet pokusů o opravu při selhání CI (0 = žádné pokusy) |
| `--issue-id` | z názvu větve | Explicitní zadání ID úkolu |
| `--phase-number` | z názvu větve | Explicitní zadání čísla fáze |

## Integrace s batch-implement

Příkaz `batch-implement` nyní místo vlastní inline logiky pro merge volá `iw phase-merge` jako subprocess. Tím se:

- Odstraňuje duplicitní kód pro čekání na CI a merge
- Zajišťuje jednotné chování při ručním i automatickém spuštění
- Umožňuje nezávislé testování a vývoj merge logiky
