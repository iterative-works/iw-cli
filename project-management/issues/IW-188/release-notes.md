# IW-188: Detailní stránka worktree v dashboardu

## Co je nového

Dashboard nově obsahuje **samostatnou detailní stránku pro každý worktree**, kde najdete veškeré informace o rozpracovaném úkolu na jednom místě.

### Kompletní přehled worktree

Na detailní stránce vidíte:

- **Informace o issue** -- název, stav a přiřazená osoba
- **Stav gitu** -- aktuální změny ve worktree
- **Průběh workflow** -- v jaké fázi se práce nachází
- **Pull request** -- odkaz a aktuální stav PR
- **Review artefakty** -- výsledky code review
- **Odkaz do editoru** -- rychlé otevření v Zedu

### Navigace

- Drobečková navigace **Projekty > název projektu > ID issue** usnadňuje orientaci a návrat zpět.
- Kliknutím na **název issue** na kartě worktree v přehledu projektu se dostanete přímo na detailní stránku.
- **Review artefakty** jsou klikatelné -- otevřou se v prohlížeči artefaktů, odkud se snadno vrátíte zpět.
- Pokud worktree neexistuje, zobrazí se srozumitelná stránka s odkazem zpět na přehled projektů.

### Živé aktualizace

Obsah detailní stránky se **automaticky obnovuje každých 30 sekund**, takže vždy vidíte aktuální stav bez nutnosti ručně obnovovat stránku.
