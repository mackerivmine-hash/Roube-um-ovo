# Roube um Ovo (Fabric 26.3)

Mod 100% server-side (funciona com cliente vanilla). Inspirado em Steal an Egg (Roblox).

## Como jogar
1. Va ate onde sera o PORTAO, olhando para o LESTE, e rode `/ovo mapa gerar` (uma vez por mundo).
   Ele constroi aos poucos (~40s) uma faixa de 9 biomas (Floresta, Lago, Deserto, Selva, Neve, Vulcao,
   Abismo Oceanico, Pre-Historico, Cosmico). O hub das bases fica a OESTE do portao.
2. `/ovo base` te da um lote no hub.
3. Corra pelos biomas e clique direito num ovo de ninho. O guardiao do bioma acorda e te persegue
   (particulas + distancia na action bar). Fuja para o OESTE ate o portao: la e zona segura.
4. Leve o ovo ao pedestal da sua base. Ele choca e vira um bicho que rende moedas
   (biomas mais fundos = pets muito mais ricos).
5. Gaste moedas em `/ovo upgrade velocidade` para escapar de guardioes mais rapidos.
6. Roube ovos nao chocados de outros jogadores; se o ladrao morrer, o ovo volta ao dono.

## Comandos
/ovo ajuda | mapa | mapa gerar | base | casa | ninhos | status | trancar | top | upgrade velocidade

## Compilar
Opcao A (recomendada): gere um projeto base em https://fabricmc.net/develop/template (Minecraft 26.3),
copie `src/main/java/com/roubeumovo` e `src/main/resources/fabric.mod.json` para ele.
Opcao B: neste projeto, `gradle wrapper --gradle-version 9.6.0` e depois `./gradlew build` (JDK 25).
O .jar fica em `build/libs/`; coloque em `mods/` junto com o Fabric API.
