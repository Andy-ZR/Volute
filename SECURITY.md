# Security Policy

## Reporting a vulnerability

Please **do not open a public issue** for security problems.

Use GitHub's **private vulnerability reporting**: open the repository's
[Security tab](https://github.com/Andy-ZR/Volute/security) → *Report a vulnerability*. If that is
not available, open a minimal issue asking for a private channel (no exploit details in the issue).

This is a personal fork maintained by one person, so there is **no response-time commitment**, but
security reports get looked at first.

## Scope

This repository is a fork of Termux. Vulnerabilities that live in the **upstream code** (the
terminal emulator, the terminal view, the API apps) should also be reported to
[upstream](https://github.com/termux/termux-app/security); if the upstream fix lands, this fork will
pick it up when it rebases.

## Releases

- Binaries published from this repository are signed with a key held outside the repository.
- Builds signed with a shared/public test key are **development-only** and are never released.
