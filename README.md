# Friends (Fabric 1.21.4, client-side)

## Budowanie
1. Skopiuj `gradlew`, `gradlew.bat` i folder `gradle/` z oficjalnego szablonu Fabric
   (https://github.com/FabricMC/fabric-example-mod) albo wykonaj `gradle wrapper --gradle-version 8.12`.
2. `./gradlew build` -> gotowy jar w `build/libs/friendsmod-1.0.0.jar` (bez `-sources`).
3. Wrzuc do `.minecraft/mods` razem z Fabric API.

## Uzycie
- Klawisz **J** albo przycisk **Friends** w menu pauzy (ESC).
- Zakladki: Znajomi / Zaproszenia / Dodaj.
- Konfiguracja: `config/friendsmod.json` (pole `messageCommand` - domyslnie `msg`, zmien na `w`/`tell` jesli serwer nie ma /msg).
