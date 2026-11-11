# AB-538: JWT key compatibility

JJWT 0.12 enforces HS512 keys of at least 512 bits (64 decoded bytes).
`jwt.secret` is Base64 decoded, exactly as JJWT 0.8 decoded its String key.
The old default `aSecret` decoded to only 40 bits. The tracked development
default has been rotated to a randomly generated 64-byte key. This public
development key must not be used in production.

Deployments must configure their own Base64-encoded secret containing at least
64 decoded bytes. Invalid Base64 or shorter keys now fail application startup
with an explanatory error. Rotating a weak key invalidates its existing tokens;
users must log in again. Tokens issued with an unchanged strong secret remain
valid with the same claims, expiry, HS512 algorithm and key derivation.

`legacy-jjwt-0.8.token` was generated before upgrading using JJWT 0.8.0,
the new development key, subject `admin@shopizer.com`, audience `api`, issued
2026-01-01T00:00:00Z and expiring 2099-01-01T00:00:00Z. It is a public test
fixture, not a production credential.
