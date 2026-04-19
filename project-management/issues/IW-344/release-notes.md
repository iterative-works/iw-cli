# Zrychlení spouštění příkazů iw

**Issue:** IW-344
**Datum:** 2026-04-19

Příkazy `iw` se nově spouštějí výrazně rychleji. Při prvním použití po změně větve, po čerstvém naklonování repozitáře nebo po vyčištění mezivýsledků trvalo doposud prvotní spuštění téměř půl minuty — nyní se stejný příkaz spustí obvykle do jedné až dvou sekund. V praxi to znamená zhruba dvacetisedminásobné zrychlení studeného startu, což se projeví u každého, kdo iw používá v denní práci, a zvlášť výrazně při přeskakování mezi větvemi nebo na nových pracovních stanicích.

Zrychlení je úplně transparentní. Při prvním spuštění si iw samo připraví potřebný předkompilovaný podklad a uloží ho pro další použití. Pokud se změní zdrojové kódy jádra, iw to pozná a podklad automaticky přestaví, přičemž na to upozorní krátkou zprávou. Autoři příkazů ani běžní uživatelé nemusí nic konfigurovat, instalovat ani upravovat — stačí normálně používat `iw` jako dosud.

Pro prostředí automatizovaných testů a pro pokročilé scénáře je k dispozici možnost nasměrovat iw na připravený podklad pomocí proměnné prostředí, takže sady testů mohou provést předkompilaci jednou a sdílet ji napříč všemi testovacími případy. To zkracuje dobu běhu testovacích sad a zjednodušuje nastavení v kontinuální integraci.

Změna nevyžaduje žádnou akci ze strany uživatelů kromě jednoho — po prvním stažení této verze proběhne automaticky počáteční příprava předkompilovaného jádra (cca 30 sekund). Další spuštění již využívají výhod nového řešení a jsou téměř okamžitá.
