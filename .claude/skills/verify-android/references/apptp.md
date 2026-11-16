# App Tracking Protection

AppTP blocks other apps' connections to tracker domains by reading the server name (SNI) from the TLS ClientHello in netguard (`vpn-network`) and asking `NgVpnNetworkStack.isDomainBlocked`. The DDG app itself, system apps, and apps on the exclusion lists are never blocked, and neither is an app without a label, so the subject is always the probe app.

## Run it

```
$V prove .claude/skills/verify-android/proofs/apptp.py                   # every mode
$V prove .claude/skills/verify-android/proofs/apptp.py --param modes=window --like-moto
```

`proofs/apptp.py` probes once with AppTP off (baseline), turns AppTP on (`steps.enable_apptp`: wipes app data, verbose logging) and checks the VPN start pixel, then probes `app.adjust.com`, `api2.branch.io` (trackers) and `example.com` (control) in each mode, reading each mode's logs from its own `mark()`. It checks the table below; the report adds a table of window scales and first-segment sizes per mode.

The hosts are live: AppTP only routes public addresses through its tunnel, so a fixture on the host (`10.0.2.2`) or the LAN would bypass it. The manifest lists them. The tracker blocklist is downloaded at runtime: if a tracker check fails with "block decision missing" and the logs show `= null`, check the domain is still on the list.

## Modes and expectations

The probe (`probe/.../ProbeActivity.java`) sends a hand-built ClientHello so the run controls where the SNI sits and how the hello is segmented.

| Mode | What it sends | Trackers | Control |
|---|---|---|---|
| `single` | one write, SNI first | blocked | allowed |
| `window` | one write, SNI after ~400 bytes: the kernel segments it by the window AppTP advertised | blocked | allowed |
| `split` | two writes, cut right after the SNI | blocked | allowed |
| `late-sni` | two writes, cut right before the SNI | allowed: netguard reads only the first segment (known limitation) | allowed |

"Blocked" means the probe got `TIMEOUT` (netguard drops the connection without a reset) **and** the log has `shouldAllowDomain for <host> (<uid>) = AppTracker(...)`. A tracker blocked at the DNS lookup instead (the probe's `NO_ADDRESS` with that decision logged) fails as "blocked by DNS, TLS blocking not reached": AppTP only routes DNS through its tunnel when a build sets tunnel DNS, as the removed moto g workaround did, and then the TLS path this proof is about never runs. The run also fails if AppTP's tunnel is gone at the end: without the VPN, the "allowed" results (control, `late-sni`) would pass for the wrong reason. A `CONNECT_TIMEOUT` or an incomplete probe run means the network failed before the hello was sent: the run stops with exit 2, never a pass. Per-mode logs (`logcat-<mode>.txt`) keep the netguard lines: `TLS server <host> (<ip>) found` is the SNI it parsed, and a garbled name there is a parser bug.

## Device-specific bugs

`window` is the mode that exercises the SYN-ACK window: netguard before 1.10.3 scaled it by the app's window scale. Linux picks an app's window scale from the larger of `/proc/sys/net/core/rmem_max` and the last value of `/proc/sys/net/ipv4/tcp_rmem`. The moto g(60) has `rmem_max` = 128 MB, so apps advertise scale 12 and netguard's 524288-byte window became 128 bytes: apps sent the hello in 128-byte segments that cut the SNI, and trackers went through. Stock emulators have `rmem_max` = 256 KB (scale 6, an 8 KB window), so both netguard versions block there.

`--like-moto` sets the moto's `rmem_max` on the skill's rooted emulator before probing. Verified on API 34 and on a real moto g(60): netguard 1.10.2 then shows window scale 12 and 128-byte first segments in the report and lets `window` trackers through; 1.10.3 blocks them. A Play Store emulator image can't be rooted, so `device start` uses a `google_apis` image.
