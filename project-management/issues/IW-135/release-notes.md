# Release Notes: iw config command

**Issue:** IW-135
**Date:** 2026-01-28

## Nová funkce: Příkaz pro dotazování konfigurace projektu

Nový příkaz `iw config` umožňuje workflow skriptům a automatizačním nástrojům dotazovat se na konfiguraci projektu bez přímého čtení konfiguračního souboru.

### Co je nového

#### Dotazování konkrétní hodnoty

```bash
# Získání typu issue trackeru
iw config get trackerType
# Výstup: GitHub

# Získání názvu repozitáře
iw config get repository
# Výstup: iterative-works/iw-cli
```

#### Export celé konfigurace jako JSON

```bash
# Export kompletní konfigurace
iw config --json
# Výstup: {"trackerType":"GitHub","team":"","projectName":"iw-cli",...}

# Pretty-print pomocí jq
iw config --json | jq .
```

#### Nápověda k použití

```bash
# Zobrazení nápovědy
iw config
# Zobrazí dostupné příkazy a seznam polí
```

### Dostupná pole

| Pole | Popis |
|------|-------|
| `trackerType` | Typ issue trackeru (GitHub, GitLab, Linear, YouTrack) |
| `team` | Identifikátor týmu (Linear/YouTrack) |
| `projectName` | Název projektu |
| `repository` | Repozitář ve formátu owner/repo (GitHub/GitLab) |
| `teamPrefix` | Prefix ID issue (GitHub/GitLab) |
| `version` | Verze nástroje |
| `youtrackBaseUrl` | Base URL pro YouTrack/GitLab self-hosted |

### Příklady použití ve workflow skriptech

```bash
# Určení typu trackeru v bash skriptu
TRACKER=$(iw config get trackerType)
if [ "$TRACKER" = "GitHub" ]; then
  # GitHub-specifická logika
fi

# Získání všech hodnot najednou
CONFIG=$(iw config --json)
REPO=$(echo "$CONFIG" | jq -r '.repository')
PREFIX=$(echo "$CONFIG" | jq -r '.teamPrefix')
```

### Exit kódy

- `0` - Úspěch (hodnota vypsána)
- `1` - Chyba (konfigurace nenalezena, neznámé pole, pole není nastaveno)

---

*Implementováno v rámci IW-135*
