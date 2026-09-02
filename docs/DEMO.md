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

### 2. Avviare tutto con un comando

```bash
cd ../uniboyamls
./rundemo.sh
```

Lo script rispetta l'ordine imposto dalle dipendenze (vedi
[Ordine di avvio](#ordine-di-avvio-perche-serve-uno-script)), popola il
catalogo prodotti e lancia la sequenza di richieste. Chiede di aprire l'arena
del robot prima di partire.

### 2-bis. Passo per passo (equivalente manuale)

```bash
cd ../uniboyamls
docker network create iss-network   # solo la prima volta
docker compose -f cargoserviceqak.yaml up
```

Non serve configurare indirizzi: ogni servizio si registra su Eureka con il
proprio **nome di container**, che Docker risolve all'interno della rete
`iss-network`. Il browser non passa da Eureka — raggiunge la GUI sulle porte
pubblicate su `localhost`.

Le immagini pubbliche vengono scaricate al primo avvio: circa 2 GB in totale,
mettere in conto qualche minuto.

### 3. Verificare che il mock stia pubblicando

```bash
docker logs sonarmock
docker exec mosquitto mosquitto_sub -t sonardata -C 3
```

Attese tre righe `msg(sonardata,event,mind,none,distance(5),N)`.

### 4. Aprire la GUI e registrare

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

## Se qualcosa non si registra su Eureka

Sintomo tipico nei log di un servizio:

```
EurekaUniboUtils | checkEureka Errore: Impossibile raggiungere il server.
NON VA EUREKA
```

`checkEureka()` fa solo due tentativi e, se falliscono, il servizio **non
riprova piu'**: resta su senza essersi registrato, e chi lo cerca non lo trova.

Due cause, entrambe gestite nel compose:

1. **Ordine di avvio.** `depends_on` da solo attende che il container parta,
   non che il servizio sia pronto. Eureka impiega qualche decina di secondi:
   per questo i servizi usano `condition: service_healthy` sull'healthcheck di
   Eureka.

2. **Lista di URL sbagliata.** Senza la variabile d'ambiente il client ricade
   su `eureka-client.properties`, che comincia con `http://localhost:8761/`
   (dentro un container non esiste) e contiene un IP obsoleto che produce
   `NoRouteToHostException` con timeout lunghi. La variabile
   `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka:8761/eureka/` ha la
   precedenza e risolve.

Per controllare chi si e' registrato:

```bash
curl -s http://localhost:8761/eureka/apps | grep -oE '<name>[^<]*|<ipAddr>[^<]*'
```

Devono comparire `CTXCARGOHOLDSERVICE`, `CTXCARGOSERVICE` e `CTXUSERMOCK`.

## Le due pagine da tenere aperte

| URL | Cosa e' |
|---|---|
| **http://localhost:8090** | Arena WebGL del robot virtuale |
| **http://localhost:8095/hold.html** | GUI della stiva |

L'arena **non e' opzionale**. Il container `wenv` non simula il robot al suo
interno: distribuisce i comandi di movimento alle scene collegate
(`execMoveOnAllConnectedScenes`), e la scena e' la pagina WebGL nel browser.
Senza una scheda aperta il robot compie un passo e si ferma, la richiesta di
movimento scade e il ciclo di carico non si chiude.

Per la registrazione conviene affiancare le due finestre: a sinistra il robot
che si muove nell'arena, a destra gli slot che si accendono.

## Ordine di avvio: perche' serve uno script

Alla prima richiesta `productpolice` cerca `ctxcargoservice` su Eureka e subito
dopo `cargorobot` cerca `ctxbasicrobot`. Se un servizio non si e' ancora
registrato la discovery ritorna `null` e la `NullPointerException` che ne segue
(`hostPort must not be null`) cancella il job dell'attore: il contesto resta in
piedi ma non elabora piu' nulla fino al riavvio.

I tempi di registrazione sono molto diversi fra i servizi — il `basicrobot`
arriva parecchio dopo gli altri — e Eureka espone le registrazioni con qualche
decina di secondi di ritardo per via della cache di risposta. Da qui la
sequenza:

1. avvio dello stack;
2. attesa che **CTXCARGOSERVICE** e **CTXBASICROBOT** compaiano nel registro;
3. riavvio del `cargoholdservice`, il cui client Eureka rilegge l'intero
   registro all'avvio;
4. popolamento del catalogo prodotti;
5. avvio della sequenza di richieste.

E' una fragilita' reale del sistema, non dello script: un client di service
discovery che fallisce con NPE e uccide l'attore, invece di ritentare, non
tollera un ordine di avvio diverso da quello previsto.

## Il catalogo prodotti

Un MongoDB appena creato e' vuoto: senza prodotti registrati ogni richiesta
viene rifiutata in `verifyProductID`. [`seedproducts.sh`](../sprint3/uniboyamls/seedproducts.sh)
inserisce i quattro PID che `usermock` richiede, con pesi che sommano a 700
(sotto il MAXLOAD di 1000), cosi' i quattro slot si riempiono e la quinta
richiesta viene rifiutata per stiva piena.
