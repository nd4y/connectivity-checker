# Connectivity Checker

🇬🇧 **English** | [🇷🇺 Русский](README.ru.md)

An Android app for testing network connectivity from a phone: ICMP, DNS, HTTP, TCP, UDP and TLS
checks are described in YAML, run manually or on a schedule, and the results can be written as
metrics into VictoriaMetrics.

The practical point is to look at the network through the client's eyes: from a particular Wi-Fi,
from a mobile carrier, from behind a VPN. What the server sees and what the phone sees often
disagree.

| A run of the check set | HTTP check results |
|---|---|
| ![The check list during a run](docs/screenshots/running.jpg) | ![HTTP checks with a failure](docs/screenshots/http-checks.jpg) |

On the left, `Run All` in progress: the summary `48/59 passing • 2 failed • 9 running`, with a
status indicator, latency and last run time on every check; the ⟳ glyph shows the auto-run
interval. On the right, HTTP checks after a run: `wikipedia.org` is red because it returned 403
instead of the expected 200.

---

## Features

- **Six check types** — `icmp`, `dns`, `http`, `tcp`, `udp`, `tls`
- **YAML config** — import from a file, export back, a built-in sample with copy-to-clipboard
- **In-app editor** — add and edit checks without YAML, through a bottom sheet
- **Periodic runs** — the `interval` field on a check (while the app is alive, see [Limitations](#limitations))
- **Export to VictoriaMetrics** — Prometheus format, Basic Auth, a local buffer while the server is unreachable
- **Material You** — dynamic colours on Android 12+

Minimum version is Android 8.0 (API 26), target is Android 14 (API 34).

---

## Building

```bash
git clone https://github.com/nd4y/connectivity-checker.git
```

```bash
cd connectivity-checker && ./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/app-debug.apk`.

JDK 17 and Android SDK 34 (build-tools 34.0.0) are required. Gradle is pulled in by the wrapper (8.6).

GitHub Actions also builds a debug APK on every push to `main` — download it from the artifacts of
the `Android CI` workflow.

### Self-hosted runner

`runner/` holds a Dockerfile with Ubuntu 22.04, JDK 17, the Android SDK and the GitHub Actions
runner, in case the build has to move to your own machine. To start it:

```bash
docker build -t cc-runner ./runner && docker run -d --name cc-runner -e GITHUB_REPO=https://github.com/nd4y/connectivity-checker -e RUNNER_TOKEN=<registration-token> cc-runner
```

`RUNNER_TOKEN` comes from Settings → Actions → Runners → New self-hosted runner; it is single-use
and lives for about an hour. Optionally: `RUNNER_NAME` (default `docker-runner`), `RUNNER_LABELS`
(default `self-hosted,Linux,android`). The container deregisters the runner itself on stop.

The workflow currently builds on `ubuntu-latest` — to switch it to your own runner, change
`runs-on` in [.github/workflows/android.yml](.github/workflows/android.yml).

---

## Config format

The file is ordinary YAML with a single top-level key, `checks`:

```yaml
checks:
  - name: "Ping Google DNS"
    type: icmp
    host: 8.8.8.8
    timeout: 3000
    interval: 60
```

A full sample with every type is in
[app/src/main/assets/sample_config.yaml](app/src/main/assets/sample_config.yaml); the same one is
available in the app under menu → **Sample Config**.

To load: the **Load Config** button (the picker accepts any file, only the content matters).
To dump the current set: menu → **Export YAML**. The check set is auto-saved into the app settings
and restored on the next launch.

### The editor, without YAML

<img src="docs/screenshots/edit-check.png" alt="The check editing form" width="320">

Tapping ⋮ → **Edit** (or ＋ for a new check) opens the same model as a form: the type is picked with
chips and the set of fields changes to match it — for `http` that is the URL, method, expected code,
body and headers; for `dns` the resolver server; for `tls` the SNI. Headers are entered one per line
as `Key: Value`. Changes land immediately in the auto-saved set and in **Export YAML**.

### Common fields

| Field | Type | Default | Description |
|---|---|---|---|
| `name` | string | `Unnamed` | The name in the list and in the metric label |
| `type` | string | **required** | `icmp` \| `dns` \| `http` \| `tcp` \| `udp` \| `tls` (case-insensitive) |
| `timeout` | int | `5000` | Timeout in milliseconds |
| `interval` | int | `0` | Auto-run period in seconds; `0` means manual only |

### Per-type fields

| Field | Types | Description |
|---|---|---|
| `host` | icmp, dns, tcp, udp, tls | Host or IP |
| `port` | tcp, udp, tls | Port; for `tls` it defaults to `443`, for `tcp`/`udp` it is required |
| `url` | http | Full URL including the scheme |
| `method` | http | `GET`, `HEAD`, `POST`; anything else is treated as `GET` |
| `expected_code` | http | Expected HTTP code; a mismatch turns the check red |
| `body` | http | Body for `POST` |
| `headers` | http | A map of headers; `Content-Type` from it also sets the `POST` body type |
| `dns_server` | dns | Resolver IP; without it the system DNS is used |
| `sni` | tls | SNI in the ClientHello; defaults to `host` |

An unknown `type` value, or a missing `type`, is a parse error — the app shows it in a snackbar and
keeps the previous check set.

### What each check actually does

**`icmp`** — runs `/system/bin/ping -c 1`. If the binary is missing or returned an error, it falls
back to `InetAddress.isReachable()`, which on Android usually means a TCP probe of port 7 rather
than ICMP. The result message shows which path worked.

**`dns`** — without `dns_server` it calls `InetAddress.getAllByName()` (the system resolver, with
the OS cache). With `dns_server` it builds the DNS query by hand and sends it over UDP to port 53 of
the given server. The built-in resolver asks only for **A** records, reads at most 512 bytes of the
response and parses A records only; CNAME chains and AAAA show up as "Resolved (no A records)".

**`http`** — OkHttp with redirects enabled (including http→https). The timeout applies separately to
connect, read and write. Any response received counts as success unless `expected_code` is set.

**`tcp`** — a plain `connect()` to `host:port`, measuring the time to establish the connection.

**`udp`** — sends a single `0x00` byte and waits for a reply. Note that a **UDP check is inherently
ambiguous**: a timeout is marked as a failure even though silence is normal behaviour for most UDP
services; only an ICMP port unreachable is a trustworthy negative. In practice it is useful for DNS
(53) and similar services that answer any garbage.

**`tls`** — a TCP connect, then a handshake with an explicit SNI. The message carries hostname
validity, the certificate CN, how many days remain until expiry, and a warning when fewer than 30
are left. An expired certificate is a failure. The combination `host: <IP>` + `sni: <name>` lets you
test a specific backend bypassing DNS.

---

## Exporting metrics to VictoriaMetrics

<img src="docs/screenshots/vm-settings.png" alt="VictoriaMetrics settings" width="320">

Menu → **VictoriaMetrics**. Set the URL (e.g. `https://vm.example.com`) and, optionally, a login and
password for Basic Auth. The **Test connection** button calls `/api/v1/query?query=up`. Export is
enabled by the URL being non-empty.

After every check the following goes to `<url>/api/v1/import/prometheus`:

```
connectivity_check_up{name="…",type="…",host="…",url="…",port="…"} 1|0 <ms>
connectivity_check_latency_ms{name="…",type="…",host="…",url="…",port="…"} <ms> <ms>
```

The `host`, `url` and `port` labels are present only on checks where the corresponding field is set.
`connectivity_check_latency_ms` is not sent if no measurement happened.

If the server is unreachable, points are stored in a local Room database and re-sent in batches of
500: `MetricsSyncWorker` (WorkManager) tries every 15 minutes, and there is also a manual
**Flush buffer** in the settings dialog. After a successful flush, records older than 7 days are
purged. The current buffer size is shown right there as `Buffered: N metric point(s)`, and the
**Clear buffer** button drops it entirely.

Example queries:

```promql
min_over_time(connectivity_check_up{name="TLS google.com:443"}[1h])
```

```promql
histogram_quantile(0.95, sum by (le, name) (rate(connectivity_check_latency_ms_bucket[5m])))
```

(the second one only works if you aggregate latency into a histogram on the VM side — out of the box
the metric is written as a gauge, and `quantile_over_time` is simpler for percentiles).

---

## Limitations

Worth knowing before relying on the app for monitoring:

- **Periodic checks live only as long as the process does.** `interval` is implemented with
  coroutines in `viewModelScope`: minimise the app and Android will eventually kill the process,
  stopping the auto-runs. There is no foreground service. The only thing that runs in the background
  is the re-sending of already collected metrics.
- **The VictoriaMetrics password is stored in SharedPreferences in plain text.** On a non-rooted
  device that is inaccessible to other apps, but there is no encryption — do not use an account with
  broad privileges.
- **Cleartext HTTP is allowed** for the whole app
  ([network_security_config.xml](app/src/main/res/xml/network_security_config.xml)), otherwise
  internal services without TLS could not be checked.
- **ICMP is not guaranteed on Android** — see the `icmp` description above.
- **Check indices and periodic jobs** are tied to the position in the list; bulk editing through
  YAML is more reliable than many pinpoint deletions in a row.

---

## Project layout

```
app/src/main/java/com/connectivity/checker/
├── checker/     — check implementations (NetworkChecker + 6 classes + a factory)
├── model/       — CheckConfig, CheckResult, enums
├── yaml/        — the YAML parser and exporter (snakeyaml, SafeConstructor)
├── metrics/     — the Room buffer, sending to VM, WorkManager
├── settings/    — a SharedPreferences wrapper
├── ui/          — bottom sheets for the check editor and the VM settings
├── adapter/     — the RecyclerView adapter for the list
└── viewmodel/   — MainViewModel: state, running, scheduling
runner/          — the Docker image of the self-hosted GitHub Actions runner
```

Stack: Kotlin, coroutines, ViewBinding, Material 3, OkHttp, Room, WorkManager, snakeyaml.
Compose is not used — the layouts are XML.

---

## Adding a new check type

1. Add a value to `CheckType` ([model/CheckConfig.kt](app/src/main/java/com/connectivity/checker/model/CheckConfig.kt)).
2. Implement `NetworkChecker` in `checker/` — return a `CheckResult` with a status, latency and a
   human-readable message; catch exceptions inside.
3. Register it in `CheckerFactory`.
4. If new config fields appeared, add them to `CheckConfig`, `YamlParser` and `YamlExporter` (all
   three, or the field will be lost on export), and to the `EditCheckBottomSheet` form.
5. Add an example to `sample_config.yaml` and a row to the field table in this README.
