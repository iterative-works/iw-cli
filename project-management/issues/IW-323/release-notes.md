# Poznámky k vydání: Podpora pluginových příkazů

**Issue:** IW-323

## Nové funkce

### Pluginové příkazy

iw-cli nyní podporuje příkazy z externích pluginů. Pluginy mohou registrovat vlastní příkazy, které se objeví v `iw --list` a lze je spouštět přes příkaz `iw`.

**Syntaxe volání:**
```bash
iw kanon/implement IW-123
iw kanon/batch-implement IW-123
```

**Zobrazení dostupných pluginových příkazů:**
```bash
iw --list
# Výstup obsahuje sekci "Plugin commands (kanon):" s dostupnými příkazy

iw --describe kanon/implement
# Zobrazí popis, použití a zdroj příkazu
```

### Registrace pluginu

Plugin se registruje vytvořením symbolického odkazu do adresáře `~/.local/share/iw/plugins/`:

```bash
ln -s /cesta/k/pluginu ~/.local/share/iw/plugins/nazev-pluginu
```

Každý plugin obsahuje:
- `commands/` — příkazy (soubory `.scala`)
- `lib/` — sdílené knihovny kompilované společně s příkazy pluginu
- `hooks/` — rozšíření pro základní příkazy iw-cli (např. `doctor`)

### Alternativní cesta pro vývoj

Pro vývoj a testování lze pluginové adresáře specifikovat přes proměnnou prostředí:

```bash
export IW_PLUGIN_DIRS="/cesta/k/pluginum"
```

### Kontrola verze

Příkazy pluginů mohou vyžadovat minimální verzi iw-cli pomocí hlavičky:

```scala
// REQUIRES: iw-cli >= 0.4.0
```

Pokud verze iw-cli nesplňuje požadavek, příkaz se nespustí a zobrazí se informace o potřebě aktualizace.

### Hooky pluginů

- **Pluginové hooky pro základní příkazy:** Soubory v `hooks/` adresáři pluginu se automaticky spouštějí při volání základních příkazů (např. `*.hook-doctor.scala`).
- **Projektové hooky pro pluginové příkazy:** Soubory v `.iw/commands/` projektu se spouštějí jako hooky při volání pluginových příkazů.

## Technické změny

- Nový soubor `.iw/VERSION` — jediný zdroj pravdy pro verzi iw-cli
- Příkaz `iw version` nyní čte verzi z `.iw/VERSION`
- Nové konstanty: `IW_PLUGIN_DIRS`, `plugins`, `REQUIRES`
- 53 nových E2E testů pokrývajících veškerou pluginovou funkcionalitu
