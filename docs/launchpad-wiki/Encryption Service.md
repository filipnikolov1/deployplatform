# Encryption Service

AES-256-GCM encryption for [[Environment Variables]] at rest.

## Source

`EncryptionServiceImpl.java` in `envvar/crypto/impl/`

## Algorithm

- **AES-256-GCM** (authenticated encryption)
- Random 12-byte IV per encryption
- IV prepended to ciphertext for storage
- Provides confidentiality + integrity (GCM tag)

## Configuration

```properties
encryption.key=${ENCRYPTION_KEY}
```

Generate with: `openssl rand -base64 32`

## Usage

- `encrypt(plaintext)` -> base64(IV + ciphertext + GCM tag)
- `decrypt(encrypted)` -> plaintext

Called by `EnvVarServiceImpl` when storing/retrieving env vars in [[PostgreSQL]].

See also: [[Environment Variables]], [[Security]]

#security