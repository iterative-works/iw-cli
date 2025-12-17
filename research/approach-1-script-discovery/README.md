# Approach 1: Script Discovery Prototype

This prototype demonstrates a command discovery architecture where commands are individual scala-cli scripts that are automatically discovered and executed.

## Structure

```
approach-1-script-discovery/
├── iw                      # Bootstrap shell script
├── core/                   # Shared library code
│   ├── Command.scala       # Command trait
│   └── Config.scala        # Config loading stub
├── commands/               # Discoverable commands
│   ├── version.scala       # Version command
│   ├── hello.scala         # Hello command (with args)
│   └── status.scala        # Status command (uses config)
├── FINDINGS.md            # Detailed findings and analysis
└── README.md              # This file
```

## How It Works

1. The `iw` bootstrap script discovers all `.scala` files in `commands/`
2. When invoked, it checks if the first argument matches a command file
3. If found, it runs the command with `scala-cli run commands/<name>.scala -- <args>`
4. If not found or no args, it lists available commands

## Usage

### List all commands
```bash
$ ./iw
Available commands:

  hello           Greet someone (usage: hello <name>)
  status          Show project status
  version         Show version information
```

### Run a command
```bash
$ ./iw version
iw-cli version 0.1.0-SNAPSHOT

$ ./iw hello World
Hello, World!

$ ./iw status
Project status:
  Tracker: not configured
  Project: not configured
```

### Error handling
```bash
$ ./iw hello
Error: Missing name argument
Usage: hello <name>

$ ./iw nonexistent
Error: Unknown command 'nonexistent'
[lists available commands]
```

## Adding a New Command

Create a new `.scala` file in `commands/`:

```scala
//> using scala "3.3.1"
//> using file "../core/Command.scala"
//> using file "../core/Config.scala"

import iwcli.core.{Command => CommandTrait}

object MyCommand extends CommandTrait {
  def name: String = "mycommand"
  def description: String = "What my command does"

  def run(args: List[String]): Int = {
    CommandTrait.println("Hello from my command!")
    0  // exit code
  }
}

@main def main(args: String*): Unit = {
  val exitCode = MyCommand.run(args.toList)
  sys.exit(exitCode)
}
```

That's it! The command is immediately available:
```bash
$ ./iw mycommand
Hello from my command!
```

## Requirements

- scala-cli must be installed and available on PATH
- Scala 3.3.1 (downloaded automatically by scala-cli)

## Key Findings

**Pros:**
- Extremely simple to add new commands
- No build step or registration needed
- Commands are independent and flexible

**Cons:**
- Slow startup (2-4 seconds per command)
- Limited ability to share runtime state
- Fragile metadata extraction

See [FINDINGS.md](FINDINGS.md) for detailed analysis and recommendations.
