# HereKitty relay — home deployment

The relay is a byte pipe for ADB traffic. It is intentionally not exposed with a Docker port
mapping: the Cloudflare Tunnel container is its only ingress.

## One-time setup

1. In Cloudflare Zero Trust, create a tunnel and add a public hostname for the relay. Its service
   must be `http://relay:7050`, which resolves to the Compose service on the Docker network. Do
   not enable Cloudflare Access in front of this hostname: the desktop and phone authenticate with
   their short-lived relay JWT instead.
2. Create one secret and use the *same value* in both places below. It signs short-lived relay
   session JWTs; it is not a client credential.

   ```sh
   openssl rand -hex 32
   cd mobile/firebase
   firebase functions:secrets:set RELAY_JWT_SECRET
   firebase deploy --only functions:claimRelaySession
   ```

3. Copy `.env.example` to `.env`, set `RELAY_JWT_SECRET` to that same value, and set
   `CLOUDFLARE_TUNNEL_TOKEN` to the tunnel token Cloudflare gives you.

## Run and update

For a localhost-only Docker Desktop smoke test (no Cloudflare token required):

```sh
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build relay
curl --fail http://127.0.0.1:7050/health
```

For a same-Wi-Fi test from a phone or another computer, use the LAN override instead. It exposes
port 7050 to the trusted local network, so do not use it on an untrusted network:

```sh
docker compose -f docker-compose.yml -f docker-compose.lan.yml up -d --build relay
```

Set the test clients' `relayUrl` to `ws://YOUR_MAC_LAN_IP:7050`. Once Cloudflare DNS is live,
switch back to the tunnel deployment and `wss://relay.your-domain.example`.

For the public Cloudflare deployment:

```sh
docker compose --profile tunnel up -d --build
docker compose logs -f relay cloudflared
```

The relay should report that it is responding on port 7050; Cloudflare then supplies TLS at the
public hostname. Configure both clients with `relayUrl=wss://relay.your-domain.example` (no port)
instead of the LAN-only `relayHost`/`relayPort` pair.

To update a user-managed deployment after pulling newer relay sources:

```sh
docker compose --profile tunnel up -d --build
```

Do not expose port 7050 directly or reuse the removed `RELAY_AUTH_TOKEN`. A relay only accepts a
valid, ten-minute JWT issued for the exact session id by `claimRelaySession`.
