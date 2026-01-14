# Release Notes: Otevření složky v editoru Zed

**Issue:** IW-74
**Datum:** 2026-01-14

Dashboard nyní umožňuje otevřít pracovní složku přímo v editoru Zed jediným kliknutím. Tato funkce výrazně zjednodušuje přechod od prohlížení issue na dashboardu k aktivní práci na kódu, protože odpadá nutnost ručně spouštět Zed a připojovat se ke vzdálené složce.

Na každé kartě worktree se nyní zobrazuje ikona editoru Zed. Po kliknutí na tuto ikonu se Zed automaticky otevře s připojením ke správné složce na vzdáleném serveru. Díky tomu můžete okamžitě začít editovat soubory bez jakýchkoliv dalších kroků.

Pro správné fungování je potřeba nastavit SSH hostname, který Zed použije pro připojení. V záhlaví dashboardu se nachází vstupní pole, kde můžete zadat alias nebo název vašeho SSH hostitele. Toto nastavení se uloží do URL adresy, takže si ho můžete uložit jako záložku a příště se automaticky použije. Pokud hostname nezadáte, použije se výchozí název serveru.

Funkce vyžaduje nainstalovaný editor Zed na vašem lokálním počítači. Pokud Zed nemáte nainstalovaný, tlačítko nebude mít žádný efekt.
