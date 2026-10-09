# Security Policy

## Supported Versions

Security reports are welcomed for the current development branch. No versioned release is currently published, so all reports apply to the head of the repository.

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Report vulnerabilities privately by opening a [GitHub Security Advisory](https://github.com/slythnox/Napster/security/advisories/new) on the repository.

## Scope

This policy covers the Napster Android application and the code in this repository. It does not cover third-party services that the application communicates with (YouTube Music, Spotify public API, Last.fm, ListenBrainz, and similar). Report issues with those services to their respective owners.

## Notes

- Napster communicates exclusively over HTTPS for all remote services.
- No credentials, tokens, or personal identifiers are transmitted by the application itself except where the user explicitly configures a third-party integration (Last.fm, ListenBrainz).
- The application contains no analytics, crash reporting, or telemetry frameworks.
