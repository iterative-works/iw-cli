# Poznámky k vydání: Nová stavební základna dashboardu iw-cli

**Issue:** IW-345
**Datum:** 2026-04-24

Dashboard iw-cli má novou technologickou základnu, která ho připravuje na roli hlavního ovládacího rozhraní nástroje. Z pohledu běžného používání příkazu `iw dashboard` se chování nemění — dashboard se nadále spouští stejným způsobem a nabízí stejné obrazovky jako dosud. Pod povrchem ale vznikl moderní frontend pipeline, který dovoluje stavět bohatší uživatelské rozhraní s komponentovou knihovnou Web Awesome, stylováním přes Tailwind a rychlým iteračním cyklem přes Vite.

Nejvýraznější změnou pro uživatele je nový vývojový režim dashboardu. Pokud pracujete na samotném dashboardu a chcete vidět úpravy šablon nebo stylů okamžitě v prohlížeči, můžete spustit dashboard s přepínačem `--dev` a souběžně běžícím Vite vývojovým serverem. Úpravy se pak projeví bez nutnosti rebuildu a restartu. Tento režim je záměrně dvojitě pojištěný — aktivuje se pouze tehdy, když je současně nastavena proměnná prostředí `VITE_DEV_URL` a zároveň je předán přepínač `--dev`, a navíc přijímá jen adresy směřující na lokální smyčku. Díky tomu není možné ho omylem zapnout v produkčním běhu.

Dashboard nyní běží jako samostatný proces spouštěný přes `java -jar`, nikoli jako součást skriptového prostředí iw-cli. To zjednodušuje správu jeho životního cyklu a oddělení od zbytku nástroje. Sestavený dashboard je kompletní balíček obsahující server i všechny statické prostředky uvnitř jednoho souboru, takže se chová předvídatelně napříč prostředími. Spouštěcí skripty se postarají o automatické přestavení dashboardu v okamžiku, kdy se změní zdroje, takže při běžné práci s iw-cli si této změny většina uživatelů nevšimne.

Tato verze zavádí také nové nároky na prostředí pro přispěvatele, kteří pracují na dashboardu. K vývoji dashboardu je nově potřeba Node 20 a Yarn 4 aktivovaný přes Corepack, přístupový token `WEBAWESOME_NPM_TOKEN` pro knihovnu Web Awesome Pro a build nástroj Mill ve verzi 1.1.5, který se stáhne automaticky při prvním spuštění. Uživatele, kteří iw-cli pouze používají, ani přispěvatele, kteří pracují jen na základu nebo příkazech, se tyto požadavky nedotýkají — sestavený dashboard je v distribučním balíčku přiložený v hotové podobě a pro jeho spuštění stačí prostředí JVM jako dosud.

Celkově jde o infrastrukturní krok, který sám o sobě nepřináší nové funkce, ale otevírá cestu k rychlejšímu vývoji a bohatšímu uživatelskému rozhraní dashboardu v navazujících vydáních.
