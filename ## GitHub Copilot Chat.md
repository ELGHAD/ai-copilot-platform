## GitHub Copilot Chat

- Extension: 0.37.9 (prod)
- VS Code: 1.109.4 (c3a26841a84f20dfe0850d0a5a9bd01da4f003ea)
- OS: win32 10.0.22631 x64
- GitHub Account: ELGHAD

## Network

User Settings:
```json
  "http.systemCertificatesNode": true,
  "github.copilot.advanced.debug.useElectronFetcher": true,
  "github.copilot.advanced.debug.useNodeFetcher": false,
  "github.copilot.advanced.debug.useNodeFetchFetcher": true
```

Connecting to https://api.github.com:
- DNS ipv4 Lookup: 140.82.121.6 (20 ms)
- DNS ipv6 Lookup: Error (17 ms): getaddrinfo ENOENT api.github.com
- Proxy URL: None (0 ms)
- Electron fetch (configured): HTTP 200 (233 ms)
- Node.js https: HTTP 200 (228 ms)
- Node.js fetch: HTTP 200 (185 ms)

Connecting to https://api.githubcopilot.com/_ping:
- DNS ipv4 Lookup: 140.82.113.21 (18 ms)
- DNS ipv6 Lookup: Error (30 ms): getaddrinfo ENOENT api.githubcopilot.com
- Proxy URL: None (3 ms)
- Electron fetch (configured): HTTP 200 (502 ms)
- Node.js https: HTTP 200 (292 ms)
- Node.js fetch: HTTP 200 (478 ms)

Connecting to https://copilot-proxy.githubusercontent.com/_ping:
- DNS ipv4 Lookup: 4.225.11.192 (29 ms)
- DNS ipv6 Lookup: Error (20 ms): getaddrinfo ENOENT copilot-proxy.githubusercontent.com
- Proxy URL: None (3 ms)
- Electron fetch (configured): HTTP 200 (276 ms)
- Node.js https: HTTP 200 (260 ms)
- Node.js fetch: HTTP 200 (266 ms)

Connecting to https://mobile.events.data.microsoft.com: HTTP 404 (159 ms)
Connecting to https://dc.services.visualstudio.com: HTTP 404 (319 ms)
Connecting to https://copilot-telemetry.githubusercontent.com/_ping: HTTP 200 (449 ms)
Connecting to https://copilot-telemetry.githubusercontent.com/_ping: HTTP 200 (476 ms)
Connecting to https://default.exp-tas.com: HTTP 400 (233 ms)

Number of system certificates: 91

## Documentation

In corporate networks: [Troubleshooting firewall settings for GitHub Copilot](https://docs.github.com/en/copilot/troubleshooting-github-copilot/troubleshooting-firewall-settings-for-github-copilot).