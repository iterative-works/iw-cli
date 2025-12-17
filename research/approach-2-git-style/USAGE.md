# Usage Examples

## Running Commands

```bash
# List all available commands
./iw

# Run a specific command
./iw version
./iw hello
./iw status

# Pass arguments to commands
./iw hello "World"
./iw hello "Michal Příhoda"
```

## Adding a New Command

Adding a new command is trivial - just create a new directory:

```bash
# 1. Create command directory
mkdir iw-mycommand

# 2. Create project.scala with scala version and core import
cat > iw-mycommand/project.scala << 'EOF'
//> using scala 3.3.4
//> using file ../core/Command.scala
EOF

# 3. Create main.scala with command implementation
cat > iw-mycommand/main.scala << 'EOF'
import iw.core.{Command, CommandHelpers}

object MyCommand extends Command:
  def run(args: Array[String]): Int =
    CommandHelpers.success("My command works!")

@main def main(args: String*): Unit =
  val exitCode = MyCommand.run(args.toArray)
  sys.exit(exitCode)
EOF

# 4. Done! The command is now available
./iw mycommand
```

## Development Workflow

```bash
# Compile a single command during development
cd iw-mycommand
scala-cli compile .

# Run a command directly (bypassing bootstrap script)
scala-cli run iw-mycommand/

# Package a command as standalone binary
cd iw-mycommand
scala-cli package . -o mycommand-bin
./mycommand-bin

# Clean build cache for a command
rm -rf iw-mycommand/.scala-build
```

## Pre-compiling All Commands

For distribution, you can pre-compile all commands:

```bash
# Compile all commands in parallel
for dir in iw-*/; do
  (cd "$dir" && scala-cli compile . > /dev/null 2>&1) &
done
wait
echo "All commands compiled"
```

## Testing

```bash
# Run the test suite
./test.sh
```

## File Organization

Each command follows this structure:

```
iw-commandname/
├── main.scala       # Command implementation with @main entry point
└── project.scala    # scala-cli directives (scala version, imports)
```

The core library is shared:

```
core/
├── Command.scala    # Shared traits and utilities
└── project.scala    # Core configuration
```
