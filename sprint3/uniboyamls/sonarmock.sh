#!/bin/sh
# Mock del sonar: pubblica eventi sonardata sul broker MQTT al posto del
# sensore fisico HC-SR04 montato sul Raspberry Pi.
#
# Formato del messaggio, come da unibo.basicomm23 ApplMessage.toString():
#   msg(MSGID, MSGTYPE, SENDER, RECEIVER, CONTENT, SEQNUM)
# Per l'evento dichiarato "Event sonardata : distance(D)" emesso da mind:
#   msg(sonardata,event,mind,none,distance(5),42)
#
# Il sonaradapter interpreta la distanza cosi' (DFREE = 10):
#   D <= DFREE/2  -> container presente alla IOPort, la richiesta prosegue
#   D >  DFREE    -> sospetto guasto, la richiesta viene rifiutata
#
# Variabili d'ambiente:
#   BROKER            host del broker MQTT           (default: mosquitto)
#   SONAR_DISTANCE    distanza normale in cm         (default: 5)
#   FAILURE_EVERY     ogni quanti campioni simulare  (default: 0 = mai)
#                     un guasto
#   FAILURE_LEN       durata del guasto in campioni  (default: 4)

BROKER="${BROKER:-mosquitto}"
DIST="${SONAR_DISTANCE:-5}"
FAILURE_EVERY="${FAILURE_EVERY:-0}"
FAILURE_LEN="${FAILURE_LEN:-4}"
FAILURE_DIST=25

echo "[sonarmock] broker=$BROKER distanza=$DIST cm, guasto ogni ${FAILURE_EVERY} campioni"

# Attende che il broker accetti connessioni
until mosquitto_pub -h "$BROKER" -p 1883 -t sonarmock/ping -m up 2>/dev/null; do
  echo "[sonarmock] attendo il broker $BROKER ..."
  sleep 2
done
echo "[sonarmock] broker raggiungibile, inizio a pubblicare"

n=0
while true; do
  n=$((n + 1))

  d="$DIST"
  if [ "$FAILURE_EVERY" -gt 0 ]; then
    # il guasto cade in fondo a ogni periodo, cosi' la demo parte sana
    pos=$((n % FAILURE_EVERY))
    if [ "$pos" -ge $((FAILURE_EVERY - FAILURE_LEN)) ]; then
      d="$FAILURE_DIST"
    fi
  fi

  mosquitto_pub -h "$BROKER" -p 1883 -q 2 -t sonardata \
    -m "msg(sonardata,event,mind,none,distance($d),$n)"

  # il sonar fisico emette un campione al secondo: il sonaradapter conta
  # le letture per approssimare la finestra di 3 secondi del requisito
  sleep 1
done
