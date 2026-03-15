# Release Notes: Podpora --help pro review-state příkazy

**Issue:** IW-202
**Datum:** 2026-03-15

## Oprava: Příkazy review-state správně reagují na --help

Přepínač `--help` u příkazů `review-state update`, `review-state write`, `review-state validate` a samotného dispatcheru `review-state` nefungoval správně. Místo zobrazení nápovědy docházelo k nežádoucímu chování -- tichá mutace stavového souboru, zavádějící chybové hlášky nebo odmítnutí příkazu jako neznámého.

### Co se změnilo

- **`iw review-state update --help`** -- dříve tiše přepsal pole `last_updated` ve stavovém souboru. Nyní zobrazí nápovědu bez vedlejších efektů.
- **`iw review-state write --help`** -- dříve selhal s chybou "Cannot infer issue ID" nebo se pokusil zapsat soubor. Nyní zobrazí nápovědu.
- **`iw review-state validate --help`** -- dříve selhal s chybou "No file path provided". Nyní zobrazí nápovědu.
- **`iw review-state --help`** -- dříve hlásil "Unknown subcommand: --help". Nyní zobrazí seznam dostupných podpříkazů.

### Přínos

Všechny `review-state` příkazy se nyní chovají konzistentně s ostatními `iw` příkazy -- `--help` a `-h` vždy zobrazí nápovědu a nikdy neprovede žádnou akci. Agenti i uživatelé mohou bezpečně zjistit použití příkazu, aniž by riskovali nechtěnou změnu stavu.
