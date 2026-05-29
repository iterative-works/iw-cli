# Poznámky k vydání: Odkaz na repozitář a vždy viditelný odkaz na PR

**Issue:** IW-347
**Datum:** 2026-05-29

Karty pracovních stromů (worktree) v dashboardu nově nabízejí rychlejší cestu k tomu, na čem skutečně pracujete. Doposud karta obsahovala pouze odkaz do nástroje pro sledování úkolů, ale chyběla přímá cesta na webovou stránku git repozitáře a odkaz na pull request občas zmizel. Tato úprava obojí řeší a obě klíčové vazby — repozitář i pull request — máte teď vždy na očích přímo na kartě, bez nutnosti ji rozbalovat nebo přecházet do detailu.

Na každé kartě se objevuje nové tlačítko vedoucí na webovou stránku repozitáře (GitHub či GitLab). Odkaz se odvozuje z konfigurace projektu, takže jediným kliknutím se dostanete ke zdrojovému kódu v prohlížeči — ať už si chcete prohlédnout historii, otevřít konkrétní soubor nebo jen sdílet adresu s kolegou.

Odkaz na pull request je nyní spolehlivě viditelný pokaždé, když pro danou větev pull request existuje. Dříve se stávalo, že odkaz chyběl jen proto, že si dashboard ještě nestihl načíst aktuální data. Nově, pokud jsou uložená data zastaralá, dashboard zobrazí poslední známý odkaz na pull request namísto toho, aby ho skryl, a doplní k němu nenápadné označení „stale", které vás upozorní, že informace nemusí být zcela aktuální. Neztratíte tak přístup k pull requestu kvůli časování načítání a zároveň víte, kdy se na zobrazený stav plně spolehnout.

Součástí změny je i sjednocení názvosloví konfigurace: pole pro adresu trackeru nese obecnější označení odpovídající tomu, že iw-cli podporuje více nástrojů pro sledování úkolů, nejen jeden konkrétní. V souborech konfigurace projektu (`.iw/config.conf`) zůstává klíč `tracker.baseUrl` beze změny, takže existující projekty není třeba upravovat. Krátká aliasová zkratka pro starší pojmenování tohoto pole se již nepoužívá; pokud jste ji někde používali, přejděte prosím na aktuální název pole.
