# Nové příkazy `iw analyze` a `iw implement`

**Issue:** IW-309
**Datum:** 2026-03-24

Práce s iw-cli nyní nabízí dva nové příkazy, které zjednodušují každodenní workflow při analýze a implementaci úkolů.

Příkaz `iw analyze` slouží jako rychlý vstupní bod pro zahájení analýzy nového úkolu. Místo zadávání složitého příkazu s parametry stačí napsat `iw analyze <číslo-úkolu>` a systém automaticky spustí triážní proces. Uživatel se tak nemusí pamatovat na přesnou syntaxi a může se rovnou soustředit na obsah úkolu.

Příkaz `iw implement` automaticky rozpozná, jaký typ workflow byl zvolen při analýze (agilní, vodopádový nebo diagnostický), a spustí odpovídající implementační příkaz. Uživatel už nemusí přemýšlet nad tím, který ze tří různých implementačních příkazů použít — stačí zadat `iw implement` a systém se postará o správné směrování. Příkaz podporuje i dávkový režim pomocí `iw implement --batch`, který umožňuje automatickou implementaci všech fází bez manuálních kroků mezi nimi.

Oba příkazy jsou čistě novou funkcionalitou a nijak nemění chování stávajících příkazů.
