# Server Configuration

The iw-cli dashboard server uses a JSON configuration file for port and host settings.

## Configuration File Location

```
~/.local/share/iw/server/config.json
```

## Configuration Format

```json
{
  "port": 9876,
  "hosts": ["localhost", "100.86.51.92"]
}
```

### Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `port` | integer | `9876` | Server port (must be 1024-65535) |
| `hosts` | string[] | `["localhost"]` | Host addresses to bind to |

### Host Values

- `localhost` or `127.0.0.1` - Local access only (no security warning)
- `0.0.0.0` - Bind to all interfaces (security warning displayed)
- Specific IP (e.g., `100.86.51.92`) - Bind to specific interface (security warning displayed)

## Examples

### Local-only access (default)

```json
{
  "port": 9876,
  "hosts": ["localhost"]
}
```

### Local + specific IP (for remote access)

```json
{
  "port": 9876,
  "hosts": ["localhost", "100.86.51.92"]
}
```

### All interfaces (most permissive)

```json
{
  "port": 9876,
  "hosts": ["0.0.0.0"]
}
```

## Related Files

- `ServerConfig.scala` - Domain model and validation
- `ServerConfigRepository.scala` - File I/O
- `ServerClient.scala` - Uses config to connect to server

## Troubleshooting

If the server isn't binding to expected hosts:

1. Check config file exists: `cat ~/.local/share/iw/server/config.json`
2. Verify JSON is valid and `hosts` array is present
3. Restart server: `./iw server stop && ./iw server start`
