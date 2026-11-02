# 🔕 Noti Leve

**Silêncio com hora pra acabar.** Um bloqueador de notificações leve para Android (sem anúncios, sem internet, sem bibliotecas), que segura tudo por um tempo escolhido — e nunca te deixa esquecer que está ligado.

## Download

Vá em **[Releases](https://github.com/Gabrielbatman266/Noti-leve/releases/latest)** e baixe o `NotiLeve.apk`.
Cada push na `main` gera um APK novo pelo GitHub Actions. Ele é sempre assinado com a mesma chave, então instala **por cima** da versão anterior.

## O que ele faz

| | |
|---|---|
| ⏱ **Modos prontos** | Foco 25 min, Estudo 50 min, Jogo 1h, Filme 2h, Respiro 10 min, Sono 8h, ou **Até eu desligar** |
| 🎚 **Tempo personalizado** | − / + de 5 em 5 (segure para pular 1 hora) |
| 🛡 **Escudo** | Recolhe cada notificação que chega e guarda no **Cofre** |
| 🗄 **Cofre** | Lista do que chegou durante o silêncio, por app. Toque para abrir o app, segure para apagar |
| 🔇 **Silenciar o sistema junto** | Liga o Não Perturbe durante o bloqueio e devolve exatamente como estava depois |
| ⚡ **Pulso de urgência** | Se o mesmo app insistir 3 vezes em 3 minutos, deixa passar e te avisa |
| ✅ **Apps liberados** | Escolha quem pode furar o silêncio |
| 📞 **Chamadas e alarmes passam** | Ligações e despertador nunca são bloqueados |
| 🔲 **Bloco nos atalhos rápidos** | "Silêncio": um toque liga com o último tempo, outro desliga |

## Lembretes para desativar

- **Contador fixo** na barra com tempo regressivo, botões **+15 min** e **Encerrar**.
- **Aviso antes do fim** (1, 5, 10 ou 15 min antes).
- **Ao acabar o tempo**, você escolhe:
  - **Desligar sozinho** → as notificações voltam e aparece o resumo ("Durou 1h · 12 notificações no cofre: WhatsApp 8, Gmail 3…").
  - **Só me lembrar** → o silêncio continua e o app insiste a cada 5 min (*Desativar · Mais 30 min · Deixar*) até você decidir.
- **Modo "Até eu desligar"** → lembrete a cada 15/30/60/120 min: "🔕 Silêncio há 1h 20min — desativar?"
- **Segure para encerrar**: o botão de parar precisa ser segurado, pra não desligar sem querer.
- Sobrevive a reinício do celular.

## Permissões

- **Acesso às notificações** (essencial) — é assim que o escudo funciona. Ative "Noti Leve · Escudo".
- **Não Perturbe** (opcional) — para cortar até o som de chegada.
- **Alarmes exatos** (Android 12+) — para o fim e os lembretes caírem no minuto certo.
- **Notificações** (Android 13+) — para os lembretes aparecerem.

Nada sai do celular: o app nem tem permissão de internet.

## Compilar

Android Gradle Plugin 8.5, Gradle 8.9, Java 17, minSdk 23 (Android 6), targetSdk 34.

```
gradle assembleRelease
```
