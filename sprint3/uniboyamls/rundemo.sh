#!/bin/bash
# Avvia la demo nell'ordine richiesto dalle dipendenze fra i servizi.
#
# L'ordine non e' opzionale. Alla prima richiesta productpolice cerca
# ctxcargoservice su Eureka: se il productservice non si e' ancora registrato,
# la discovery ritorna null e la NullPointerException che ne segue
# ("hostPort must not be null") cancella il job dell'attore, lasciando il
# contesto inutilizzabile fino al riavvio.
#
# Il client Eureka rilegge l'intero registro all'avvio, quindi il
# cargoholdservice va (ri)avviato DOPO che il productservice si e' registrato.
set -e
cd "$(dirname "$0")"
COMPOSE="docker compose -f cargoserviceqak.yaml"

wait_registered() {   # $1 = nome applicazione su Eureka
  printf "  attendo %s su Eureka" "$1"
  for _ in $(seq 1 60); do
    if curl -s http://localhost:8761/eureka/apps 2>/dev/null | grep -q "<name>$1<"; then
      echo " -> registrato"; return 0
    fi
    printf "."; sleep 5
  done
  echo " -> TIMEOUT"; return 1
}

echo "[1/5] avvio dello stack"
docker network create iss-network 2>/dev/null || true
$COMPOSE up -d

echo "[2/5] attendo productservice e basicrobot"
# Vanno attesi entrambi: cargoservice cerca ctxcargoservice alla prima
# richiesta e cargorobot cerca ctxbasicrobot subito dopo. Il robot si
# registra parecchio piu' tardi degli altri.
wait_registered CTXCARGOSERVICE
wait_registered CTXBASICROBOT

echo "[3/5] riavvio il cargoholdservice perche' rilegga il registro"
docker restart cargoholdservice >/dev/null
wait_registered CTXCARGOHOLDSERVICE

echo "[4/5] popolo il catalogo prodotti"
./seedproducts.sh | sed 's/^/  /'

echo "[5/5] arena del robot"
cat <<'ARENA'

  IMPORTANTE: apri ORA nel browser  http://localhost:8090

  E' l'arena WebGL del robot virtuale. La simulazione del movimento gira
  DENTRO quella pagina (wenv esegue "execMoveOnAllConnectedScenes"): senza
  una scheda aperta il robot compie un passo e si blocca, perche' nessuno
  segnala il completamento e la richiesta di movimento scade.

  Tieni aperta anche la GUI della stiva:  http://localhost:8095/hold.html

ARENA
read -r -p "  Premuto Invio quando l'arena e' aperta, parte la sequenza... " _
docker restart usermock >/dev/null

cat <<MSG

Sequenza avviata.

  Arena del robot:  http://localhost:8090
  Stiva:            http://localhost:8095/hold.html

usermock invia 5 richieste (PID 17, 3, 8, 9 e ancora 9) a 10 secondi di
distanza: i primi quattro riempiono la stiva, il quinto viene rifiutato
perche' non ci sono piu' slot. Il ciclo dura circa un minuto.

Per ripetere la sequenza:      docker restart usermock
Per seguire il flusso:         docker logs -f cargoholdservice
Per fermare tutto:             docker compose -f cargoserviceqak.yaml down
MSG
