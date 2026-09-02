# Eseguire la demo senza il sonar fisico

Il requisito 3 del committente si appoggia a un sonar HC-SR04 collegato ai GPIO
di un Raspberry Pi. Questa guida esegue il sistema completo sostituendo quel
sensore con un mock, senza toccare il codice del dominio.

## Perché serve un mock

L'attore `sonaradapter` resta in attesa di eventi `sonardata` prima di
autorizzare il movimento del robot:

```
State handleRequest { ... }  Transition whenEvent sonardata -> handleSonarData
State handleSonarData { ... delay 2000 }  Transition whenEvent sonardata -> verifyData
```

Servono **due letture** per completare un ciclo. Senza una sorgente di
`sonardata` il `cargoservice` resta bloccato in `verifySonar`: la GUI mostra il
primo slot riservato e poi il sistema non elabora altre richieste.

## Come funziona il mock

[`sonarmock.sh`](../sprint3/uniboyamls/sonarmock.sh) pubblica sul broker MQTT
lo stesso messaggio che emetterebbe l'attore `mind` del progetto
`sonarLed2025`. Il formato è quello di `ApplMessage.toString()` della libreria
`unibo.basicomm23`:

```
msg(MSGID, MSGTYPE, SENDER, RECEIVER, CONTENT, SEQNUM)
```

che per l'evento `Event sonardata : distance(D)` diventa:

```
msg(sonardata,event,mind,none,distance(5),42)
```

pubblicato sul topic `sonardata`, dichiarato in `cargoservice.pl` con
`mqttBroker("mosquitto","1883","sonardata")`.

Non serve alcuna modifica agli attori: la libreria qak iscrive automaticamente
ogni attore FSM al topic degli eventi all'avvio (`ActorBasicFsm` chiama
`checkMqtt()`, che esegue `mqtt.subscribe(this, sysUtil.getMqttEventTopic())`).

La distanza determina il comportamento, con `DFREE = 10` nel `sonaradapter`:

| Distanza | Interpretazione | Effetto |
|---|---|---|
| `D <= DFREE/2` (default: 5) | container presente alla IOPort | la richiesta prosegue, il robot carica |
| `D > DFREE` (25 nel mock) | sospetto guasto del sensore | `sonarReplyFailure`, la richiesta viene rifiutata |

## Procedura

### 1. Costruire le due immagini locali

Il compose usa immagini pubbliche tranne due, che vanno costruite:

```bash
# il servizio principale
cd sprint3/cargoservice_sprint3
./gradlew distTar
docker build -t cargoholdservice_local:latest .

# il client di test
cd ../userMock
./gradlew distTar
docker build -t usermock:2.0 .
```

### 2. Configurare l'indirizzo dell'host

Eureka pubblica agli altri servizi l'indirizzo con cui contattarli, quindi
serve l'IP reale della macchina, non `localhost`:

```bash
cd ../uniboyamls
cp .env.example .env
sed -i "s/^SERVICE_IP=.*/SERVICE_IP=$(hostname -I | awk '{print $1}')/" .env
cat .env
```

### 3. Avviare

```bash
docker network create iss-network   # solo la prima volta
docker compose -f cargoserviceqak.yaml up
```

Le immagini pubbliche vengono scaricate al primo avvio: circa 2 GB in totale,
mettere in conto qualche minuto.

### 4. Verificare che il mock stia pubblicando

```bash
docker logs sonarmock
docker exec mosquitto mosquitto_sub -t sonardata -C 3
```

Attese tre righe `msg(sonardata,event,mind,none,distance(5),N)`.

### 5. Aprire la GUI e registrare

La GUI è su **http://localhost:8095/hold.html**.

`usermock` invia da solo cinque richieste di carico (PID 17, 3, 8, 9, 9) a
distanza di 10 secondi. Il ciclo completo dura circa un minuto: è la finestra
da registrare per la GIF.

Nella GUI si vedono gli slot passare a occupato con PID e peso, spinti via
WebSocket senza polling.

## Simulare un guasto del sonar

Per mostrare anche il requisito 6, basta abilitare i guasti periodici nel
compose:

```yaml
sonarmock:
  environment:
    - FAILURE_EVERY=8    # ogni 8 campioni...
    - FAILURE_LEN=3      # ...3 campioni a distanza anomala
```

poi `docker compose -f cargoserviceqak.yaml up -d sonarmock`. Il mock parte
sempre con letture normali e introduce il guasto in fondo a ogni periodo, così
la registrazione mostra prima il funzionamento corretto e poi il rifiuto.

Nei log del `cargoholdservice` compare:

```
from SONAR | ledON | FAILURE
CargoService | SONAR failure -- retrying
```

e lo slot prenotato viene rilasciato con `slotrelease`.

## Fermare tutto

```bash
docker compose -f cargoserviceqak.yaml down
```
