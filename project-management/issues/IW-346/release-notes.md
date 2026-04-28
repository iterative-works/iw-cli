# Rychlejší instalace iw-cli s předpřipravenými balíčky

**Issue:** IW-346
**Datum:** 2026-04-28

Nově nainstalované iw-cli je po rozbalení okamžitě připravené k práci. Distribuovaný balíček nyní obsahuje předem připravené spustitelné komponenty, takže první spuštění nemusí nic stahovat ani sestavovat. To znamená, že po stažení a rozbalení můžete rovnou začít používat všechny příkazy bez čekání.

Pro běžného uživatele se tím odstraňuje největší zdrojová nepříjemnost dosavadní instalace — nutnost mít na svém počítači vývojářské nástroje pro sestavení nástroje při prvním spuštění. Nově stačí pouze prostředí pro běh, které je obvykle k dispozici, a iw-cli se rovnou rozběhne. Příkaz `iw-bootstrap` po nainstalování pouze ověří, že je vše připravené, místo toho aby cokoli kompiloval. Hlášení po dokončení jasně potvrzuje, že balíček je v pořádku a připravený k použití.

Sestavení distribučního balíčku se přesunulo do automatizovaného procesu na straně serveru, takže každé vydání má spolehlivě stejný obsah a vychází z reprodukovatelného postupu. Pro tým, který nástroj udržuje, to znamená méně manuální práce při vydávání nových verzí a větší jistotu, že to, co se nahrává ke stažení, odpovídá tomu, co bylo otestováno.

Pro vývojáře, kteří pracují přímo se zdrojovým kódem iw-cli ve vlastním klonu repozitáře, se nic nemění — vývojový režim funguje stejně jako dříve. Změna se týká pouze způsobu, jak je nástroj distribuován koncovým uživatelům.
