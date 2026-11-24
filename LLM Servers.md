# LLM Servers

Oct 26, 2026 · @Mukesh CDS

Two Ubuntu machines serve Ollama models to mkpro on the MacBook. All traffic goes over Tailscale; nothing is exposed to the internet. Companion to [mkpro Local LLM Setup Plan](mkpro%20Local%20LLM%20Setup%20Plan.md).

## At a glance

| | H81 (`heavy`) | tvadev (`fast`) |
| --- | --- | --- |
| Hostname | `tva-pd-H81` | `tvadev` |
| Tailscale IP | 100.114.88.64 | 100.101.103.63 |
| LAN IP | 192.168.1.42 | 192.168.1.100 |
| CPU | Intel, H81 desktop board | Intel i3-2348M, 2 cores / 4 threads, **no AVX2** |
| RAM / disk | 15 GB / 233 GB (151 GB free) | 11 GB / 98 GB (61 GB free) |
| Ollama | 0.40.3 | 0.40.11 |
| Role | All mkpro agents | Embeddings only (later improvement) |
| Model in use | `qwen3:14b` (9.3 GB), ~2.5 tokens/s | `nomic-embed-text` (0.3 GB) |
| SSH type | Tailscale SSH | OpenSSH with key |
| Admin user | `tva-pd` | `tvadev01` |
| Restricted user | `aiuser` | `aiuser` |

tvadev runs a 7B model at only 0.28 tokens/s because its CPU lacks AVX2, so it does not serve chat models.

## Ollama configuration (both servers)

Systemd override at `/etc/systemd/system/ollama.service.d/override.conf`:

```ini
[Service]
Environment="OLLAMA_HOST=0.0.0.0:11434"
Environment="OLLAMA_KEEP_ALIVE=-1"
Environment="OLLAMA_CONTEXT_LENGTH=8192"
Environment="OLLAMA_NUM_PARALLEL=1"
```

- `0.0.0.0:11434` listens on all interfaces; the firewall limits it to Tailscale.
- `KEEP_ALIVE=-1` keeps a loaded model in RAM until another model replaces it or Ollama restarts.
- 8192-token context. mkpro is patched to send the same value (see [mkpro notes](#mkpro-notes)).
- After any Ollama upgrade, confirm the override survived: `systemctl cat ollama`.

Installed models:

| H81 | Size | Used by | tvadev | Size | Used by |
| --- | --- | --- | --- | --- | --- |
| `qwen3:14b` | 9.3 GB | mkpro (all agents) | `nomic-embed-text` | 0.3 GB | Later: codebase search |
| `qwen2.5-coder:14b` | 9.0 GB | Unused: no tool calling | `qwen2.5-coder:7b` | 4.7 GB | Unused: too slow |
| `qwen2.5-coder:7b` | 4.7 GB | Unused | | | |
| `qwen2.5:7b`, `llama3.2:3b`, `qwen2.5-coder:1.5b`, `qwen2.5:0.5b` | 8.1 GB | Older tests | | | |

Unused models can be removed with `ollama rm <name>` to free disk.

## Firewall (ufw)

| Rule | H81 | tvadev |
| --- | --- | --- |
| Ollama 11434 on `tailscale0` only | ✅ | ✅ |
| OpenSSH | ✅ Anywhere | ✅ Anywhere |
| Other | RDP 3389 (Anywhere and tailscale0) | Many pre-existing service rules (Jenkins, Grafana, Cockpit, web), unrelated to this setup |

Never forward port 11434 on the router: Ollama has no login.

Optional on H81: `sudo ufw delete allow 3389/tcp` limits Remote Desktop to Tailscale (the tailscale0 rule stays).

## Users and access

| User | Where | Login | sudo |
| --- | --- | --- | --- |
| `tva-pd` | H81 | Tailscale SSH | Full (password) |
| `tvadev01` | tvadev | SSH key | Full (password) |
| `aiuser` | Both | H81: Tailscale SSH; tvadev: SSH key | Limited, no password (below) |

`aiuser` is for automated and AI-assisted work. It is not in the `sudo` group. Its only passwordless sudo rule, in `/etc/sudoers.d/aiuser-ollama` on each server:

```
aiuser ALL=(root) NOPASSWD: /usr/bin/systemctl restart ollama, /usr/bin/systemctl status ollama, /usr/bin/journalctl -u ollama *, /usr/sbin/ufw status *
```

Commands must match exactly: `sudo systemctl status ollama` works, `sudo systemctl status ollama --no-pager` does not. `ufw status` needs an argument, such as `sudo ufw status verbose`.

## How to log in

### 1. Check Tailscale (on the Mac)

```bash
/Applications/Tailscale.app/Contents/MacOS/Tailscale status
```

Both `tva-pd-h81` and `tvadev` should show `active` or `idle`, not `offline`. If one is offline, check that machine is on and run `sudo tailscale up` on it.

### 2. Log in

```bash
# H81
ssh tva-pd@100.114.88.64      # admin
ssh aiuser@100.114.88.64      # restricted

# tvadev
ssh tvadev01@100.101.103.63   # admin
ssh aiuser@100.101.103.63     # restricted
```

Optional shortcuts in `~/.ssh/config` on the Mac:

```
Host h81
  HostName 100.114.88.64
  User tva-pd

Host tvadev
  HostName 100.101.103.63
  User tvadev01
```

Then `ssh h81` or `ssh aiuser@tvadev` (the `User` line is overridden by `aiuser@`).

### 3. If H81 asks for a Tailscale check

H81 uses Tailscale SSH, which ignores `~/.ssh/authorized_keys`. If you see:

```
# Tailscale SSH requires an additional check.
# To authenticate, visit: https://login.tailscale.com/a/...
```

open the link and approve. To stop the prompt permanently, in the Tailscale admin console go to **Access controls** and change the `ssh` rule's `"action": "check"` to `"accept"`:

```json
"ssh": [
  {
    "action": "accept",
    "src":    ["autogroup:member"],
    "dst":    ["autogroup:self"],
    "users":  ["autogroup:nonroot", "root"]
  }
]
```

As of Oct 26 the check prompt came back for H81 after the change, so confirm the saved policy says `accept`.

### 4. If tvadev refuses the key

tvadev uses normal OpenSSH. Your Mac key (`~/.ssh/id_ed25519`) must be in `~/.ssh/authorized_keys` for that user. To add a key for another user, as `tvadev01`:

```bash
sudo mkdir -p /home/<user>/.ssh
cat ~/.ssh/authorized_keys | sudo tee -a /home/<user>/.ssh/authorized_keys >/dev/null
sudo chown -R <user>:<user> /home/<user>/.ssh
sudo chmod 700 /home/<user>/.ssh && sudo chmod 600 /home/<user>/.ssh/authorized_keys
```

## Everyday commands

From the Mac (no login needed):

```bash
curl http://100.114.88.64:11434/api/tags      # H81 models
curl http://100.101.103.63:11434/api/tags     # tvadev models
curl http://100.114.88.64:11434/api/ps        # what's loaded in RAM
```

On a server (no sudo needed):

```bash
ollama list                        # installed models
ollama ps                          # loaded models and context size
ollama pull qwen3:14b              # download a model
ollama stop qwen2.5-coder:7b       # unload a model from RAM
ollama run qwen3:14b --verbose "write a Java hello world"   # speed test: see "eval rate"
free -h                            # memory
```

With the `aiuser` sudo rule:

```bash
sudo systemctl restart ollama
sudo systemctl status ollama
sudo journalctl -u ollama -n 50
sudo ufw status verbose
```

Changing the Ollama override, installing packages or editing firewall rules needs the admin user.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `curl ... :11434` times out | Ollama stopped, machine off, or Tailscale down | `tailscale status`; then `sudo systemctl restart ollama` |
| First reply takes about a minute | Model loading after a restart | Normal; it stays loaded afterwards |
| H81 slow, swap in use | Chrome, VS Code or other apps on the H81 desktop | Close them while using mkpro; the 14B needs about 10 GB |
| Ollama restarts when loading a second 14B | Not enough RAM for two 14B models | Use one 14B model at a time |
| `does not support thinking` | Unpatched mkpro jar | See mkpro notes |

## mkpro notes

mkpro runs on the Mac from `~/tools/mkpro` and points at `heavy` only:

```
/ollama add heavy http://100.114.88.64:11434
/config set default OLLAMA qwen3:14b@heavy
/config set Coordinator OLLAMA qwen3:14b@heavy
/config set Coder OLLAMA qwen3:14b@heavy
/config set CodeEditor OLLAMA qwen3:14b@heavy
```

- The jar `~/tools/mkpro/target/mkpro-4.9.0.jar` is patched to send `think: false` and `num_ctx: 8192`. The original is `mkpro-4.9.0.jar.orig`. Downloading a new release removes the patch.
- `qwen2.5-coder` models can't make tool calls through Ollama, so mkpro agents can't read or write files with them. Use `qwen3:14b`.
- Start each task with `/history new`. Restart mkpro after any `/config set`.
- Don't use `/remember`: it overwrites project memory. Helpers are in `~/tools/mkpro-setup/`.
