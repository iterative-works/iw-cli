# Nový příkaz: `iw batch-implement`

## Co je nového

iw-cli nově obsahuje příkaz `batch-implement`, který umožňuje nechat automaticky implementovat celý úkol fázi po fázi bez nutnosti ručního zásahu.

## K čemu to slouží

Implementace větších úkolů v iw-cli probíhá po fázích — každá fáze má svůj plán, Claude ji implementuje, otevře pull request, a vy ji zkontrolujete a přijmete. Dříve se celý tento proces řídil shell skriptem, který bylo těžké udržovat a rozšiřovat.

Příkaz `batch-implement` tento proces přebírá: automaticky projde všechny fáze úkolu, počká na výsledek každé z nich, sloučí přijatý pull request a pokračuje dál — dokud nejsou všechny fáze hotové.

Pokud implementace fáze selže, příkaz se pokusí o obnovu a v případě potřeby zastaví s informací o tom, co se stalo.

## Jak se příkaz používá

```bash
# Automatická detekce úkolu z aktuální větve
./iw batch-implement

# Explicitní zadání úkolu a workflow
./iw batch-implement IW-275 ag

# Volitelné parametry
./iw batch-implement IW-275 ag --model claude-opus-4-5 --max-turns 50 --max-retries 2 --max-budget-usd 10
```

Příkaz automaticky rozpozná číslo úkolu z názvu aktuální větve a typ workflow z konfigurace projektu, takže ve většině případů stačí spustit `./iw batch-implement` bez dalších argumentů.

## Proč je to lepší než dřív

Předchozí řešení byl shell skript o 473 řádcích závislý na externích nástrojích (`jq`, `sed`). Nový příkaz:

- Je napsán v jazyce projektu (Scala), takže je snazší ho upravovat a testovat
- Logika rozhodování je pokryta jednotkovými testy
- Chybové stavy jsou ošetřeny srozumitelněji
- Nevyžaduje žádné externí závislosti nad rámec toho, co iw-cli již používá
