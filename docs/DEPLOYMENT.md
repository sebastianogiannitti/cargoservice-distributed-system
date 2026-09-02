# Deployment

Il sistema e composto da 8 servizi containerizzati, orchestrati da
[`sprint3/uniboyamls/cargoserviceqak.yaml`](../sprint3/uniboyamls/cargoserviceqak.yaml).

## Prerequisiti

- Docker e Docker Compose
- JDK 17+ solo se vuoi ricompilare l'immagine del servizio

## Avvio

```bash
# la rete e' dichiarata "external": va creata una volta sola
docker network create iss-network

cd sprint3/uniboyamls
docker compose -f cargoserviceqak.yaml up
```

## Configurazione degli indirizzi

Le variabili `SERVICE_IP` / `SERVICE_HOST` nel file compose devono contenere
**l'indirizzo IP della macchina host**, non `localhost` e non l'IP interno del
container.

Il motivo: ogni servizio si registra su Eureka pubblicando l'indirizzo a cui gli
altri devono contattarlo. Se pubblica il proprio IP interno Docker, i servizi
fuori da quella rete — o il browser che apre la GUI — non lo raggiungono.

Per trovare l'IP dell'host:

```bash
hostname -I | awk '{print $1}'
```

e sostituirlo nelle occorrenze di `10.152.235.74` nel file compose.

> Questo e un limite noto del progetto: gli indirizzi andrebbero esternalizzati
> in un file `.env` invece che ripetuti nel compose.

## Porte esposte

| Servizio | Porta | Uso |
|---|---|---|
| `cargoholdservice` | 8888 | contesto qak principale |
| | 8095 | web GUI — **http://localhost:8095/hold.html** |
| | 8096 | WebSocket per gli aggiornamenti push |
| | 5683/udp | risorsa CoAP observable `holdstate` |
| `cargoserviceqak` | 8111 | productservice |
| `basicrobot24` | 8020, 8085 | robot |
| `wenv` | 8090, 8091 | mondo virtuale del robot |
| `usermock` | 8887 | client di test |
| `mosquitto` | 1883, 9001 | broker MQTT (eventi sonar) |
| `mongodb` | 27017 | database prodotti |
| `eureka` | 8761 | service discovery — console web |

## Verifica

Dopo l'avvio:

1. la console Eureka su http://localhost:8761 elenca i servizi registrati;
2. la GUI su http://localhost:8095/hold.html mostra i 4 slot;
3. `usermock` invia automaticamente una sequenza di richieste di carico: gli
   slot nella GUI si aggiornano in push mentre il robot lavora.

## Ricompilare l'immagine

```bash
cd sprint3/cargoservice_sprint3
./gradlew distTar
docker build -t cargoholdservice_local:latest .
```

Il [dockerfile](../sprint3/cargoservice_sprint3/dockerfile) estrae la
distribuzione Tar prodotta da Gradle ed esegue lo script di avvio.
