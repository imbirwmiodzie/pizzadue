# Pizzaioli server

The community behind the app's **Pizzaioli** tab: accounts, photos, swipes, comments and
"Mangiamo insieme?" invitations. Kotlin + Ktor, PostgreSQL, photos on disk.

The app works without it (everything then stays on the phone, with sample pizzaioli), so this can
be set up whenever you are ready.

## Try it on your computer

```bash
./gradlew -p server run        # from the repository root; listens on http://localhost:8080
```

With no settings it keeps its data in `server/data/` (an H2 database file and the photos).

## Run it for real (Oracle Cloud or any Linux machine with Docker)

1. Create an Ubuntu VM. On Oracle Cloud the Always Free `VM.Standard.A1.Flex` shape is plenty.
2. Open ports **80** and **443** twice: in the VCN security list, and on the VM itself
   (`sudo iptables -I INPUT -p tcp -m multiport --dports 80,443 -j ACCEPT && sudo netfilter-persistent save`;
   Oracle's Ubuntu images block them by default).
3. Point a DNS name at the VM's public IP. A name is needed because Android only talks HTTPS.
4. Install Docker, clone this repository, then:

   ```bash
   cd server
   cp .env.example .env     # fill in DOMAIN and DB_PASSWORD
   docker compose up -d --build
   curl https://$DOMAIN/health    # -> ok
   ```

5. Tell the app: in the Pizzaioli tab, **Server address** → `https://your.domain`. To bake it into
   every build instead, add the repository variable `VIBEPIZZAIOLO_COMMUNITY_URL`.

Back up the two Docker volumes `server_db` and `server_photos`; that is all the state there is.

## Settings (environment variables)

| Variable | Default | |
|---|---|---|
| `PORT` | 8080 | |
| `DATABASE_URL` | H2 file in `DATA_DIR` | e.g. `jdbc:postgresql://db:5432/pizzaioli` |
| `DATABASE_USER`, `DATABASE_PASSWORD` | | |
| `DATA_DIR` | `data` | photos live in `DATA_DIR/photos` unless `PHOTO_DIR` is set |
| `ANTHROPIC_API_KEY` | | when set, Claude names the pizza on each new photo |
| `CLASSIFIER_MODEL` | `claude-opus-5-5` | about one cent per photo; `claude-haiku-5-5` is far cheaper |

## What it does

| | |
|---|---|
| `POST /auth/signup`, `/auth/login`, `/auth/logout` | passwords hashed with Argon2id; a login is a random token valid 90 days, stored hashed |
| `GET /me`, `PUT /me/card`, `DELETE /me` | the style card; deleting removes pizzas, photos, comments, swipes, chats |
| `POST /photos`, `GET /photos/{id}/card.jpg` \| `thumb.jpg` | uploads are re-encoded (no EXIF) to 1080 px and 360 px and served as immutable files |
| `POST /photos/sniff` | type proposal for a photo; `501` without `ANTHROPIC_API_KEY` |
| `POST /pizzas`, `GET /feed`, `GET /pizzas?type=&style=&base=&minSpice=&minTemp=&maxTemp=&q=&sort=` | posting, the swipe deck, search |
| `POST /pizzas/{id}/swipe`, `GET`/`POST /pizzas/{id}/comments` | |
| `POST /pizzas/{id}/invite`, `GET /invites`, `POST /invites/{id}/answer`, `GET`/`POST /invites/{id}/messages` | chat opens once the invited pizzaiolo accepts |
| `POST /pizzas/{id}/report`, `POST /pizzaioli/{id}/block` | a report hides the pizza for the reporter, three hide it for everyone; a block hides both people from each other |

Sign-up and login are limited to 10 a minute per address, everything else to 90 a minute per login.

Not there yet: email verification and password reset (both need an email sender), push
notifications, and an admin view of reports (they are in the `reports` table).

## Tests

```bash
./gradlew -p server test                                  # the API against an in-memory database
COMMUNITY_TEST_URL=http://localhost:8080 ./gradlew testDebugUnitTest --tests '*RemoteCommunityTest*'   # the app's client against a running server
```
