# Immagini

- `gui-demo.gif` — ciclo di carico completo, referenziato dal README principale.
  A sinistra l'arena WebGL del robot virtuale, a destra la web GUI della stiva.
  I quattro slot si riempiono via WebSocket; il quarto carico fallisce, lo slot
  viene rilasciato e la richiesta successiva lo recupera.

  Registrato con il registratore di GNOME e convertito con Pillow: ritaglio
  delle barre del browser, 760 px di larghezza, 64 colori, un fotogramma ogni 5
  (circa 5x la velocita' reale), `disposal=1` per evitare le scie sui
  fotogrammi parziali.
