# Podpora issue trackeru Forgejo

**Issue:** IW-389
**Datum:** 24. 6. 2026

Nástroj iw-cli nově umí pracovat s projekty hostovanými na platformě Forgejo (a kompatibilním Gitea), ať už jde o veřejný Codeberg nebo vlastní firemní instanci. Doposud bylo možné napojit se na Linear, YouTrack, GitHub a GitLab — Forgejo se tak přidává jako pátý plně podporovaný tracker. Díky tomu mohou týmy, které svůj kód i úkoly vedou na Forgejo, využívat stejný komfortní pracovní postup jako u ostatních platforem.

Po napojení projektu na Forgejo si můžete běžným příkazem zobrazit detail libovolného issue přímo v terminálu — uvidíte jeho název, popis i stav, aniž byste museli přepínat do prohlížeče. Stejně tak lze nové issue z příkazové řádky rovnou založit. Průvodce úvodním nastavením projektu nyní nabízí Forgejo jako jednu z voleb a sám se doptá na adresu instance a název repozitáře, takže nastavení zvládnete během chvíle. Kontrolní příkaz pro diagnostiku projektu pak ověří, že je vše správně nakonfigurováno, a upozorní, pokud něco chybí.

Podpora se neomezuje jen na práci s issue. iw-cli umí na Forgejo také automaticky zakládat pull requesty pro jednotlivé fáze práce, slučovat je a průběžně sledovat stav kontrolních běhů (CI). Pracovní postup s fázemi tak na Forgejo funguje stejně plynule jako na GitHubu či GitLabu — od zahájení práce přes vytvoření pull requestu až po jeho sloučení.

Napojení na Forgejo probíhá napřímo přes jeho rozhraní, není tedy potřeba instalovat žádný další nástroj. Stačí připravit přístupový token a vložit ho do připravené proměnné prostředí. Stávající projekty na ostatních platformách nejsou touto novinkou nijak dotčeny a fungují beze změny.
