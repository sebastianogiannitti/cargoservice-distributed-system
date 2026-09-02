#!/bin/bash
# Popola il database dei prodotti che usermock richiede.
#
# Un container MongoDB appena creato e' vuoto: senza prodotti registrati ogni
# richiesta di carico viene rifiutata in verifyProductID, perche' productpolice
# interroga il productservice e non trova il PID.
#
# I prodotti si potrebbero creare con la richiesta createProduct del
# productservice, ma quel percorso e' fragile: se il client chiude la
# connessione prima che la reply venga scritta, l'eccezione in
# TcpConnection.forward cancella il job dell'attore, che smette di servire.
# Si scrive quindi direttamente sulla collezione, nello stesso formato:
#   { productId: <int>, jsonRep: '<json>' }
#
# Uso:  ./seedproducts.sh
set -e

# PID e peso. usermock chiede 17, 3, 8, 9 e poi ancora 9: con quattro slot
# la quinta richiesta li trova occupati e viene rifiutata (FULL).
# Somma dei pesi = 700, sotto il MAXLOAD di 1000 della stiva.
read -r -d '' SCRIPT <<'JS' || true
const items = [
  { productId: 17, name: "p17", weight: 100 },
  { productId: 3,  name: "p3",  weight: 150 },
  { productId: 8,  name: "p8",  weight: 200 },
  { productId: 9,  name: "p9",  weight: 250 }
];
items.forEach(it => {
  const jsonRep = JSON.stringify(it);
  db.products.updateOne(
    { productId: it.productId },
    { $set: { productId: it.productId, jsonRep: jsonRep } },
    { upsert: true }
  );
});
print("prodotti in catalogo: " + db.products.countDocuments());
db.products.find({}, { _id: 0 }).forEach(d => print("  " + d.jsonRep));
JS

echo "=== inserimento prodotti ==="
docker exec mongodb mongosh cargodb --quiet --eval "$SCRIPT"

echo
echo "Il productservice interroga MongoDB a ogni richiesta (AdapterStorageMongo),"
echo "quindi non serve riavviarlo. Rilancia la sequenza con:"
echo "  docker restart usermock"
