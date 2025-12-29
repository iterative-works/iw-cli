# Test Mermaid Diagram Rendering

This file tests Mermaid diagram rendering in the artifact viewer.

## Test 1: Basic Flowchart

```mermaid
graph TD
  A[Start] --> B{Decision}
  B -->|Yes| C[Process]
  B -->|No| D[Skip]
  C --> E[End]
  D --> E
```

## Test 2: Sequence Diagram

```mermaid
sequenceDiagram
    participant User
    participant System
    User->>System: Request
    System-->>User: Response
```

## Test 3: Regular Code Block (Regression Check)

This Scala code block should **NOT** be transformed:

```scala
def hello(): String = {
  val greeting = "Hello, World!"
  greeting
}
```

## Test 4: Multiple Diagrams

First diagram:

```mermaid
graph LR
  A[Input] --> B[Process]
  B --> C[Output]
```

Second diagram:

```mermaid
flowchart TD
  Start([Start]) --> Check{Is Valid?}
  Check -->|Yes| Process[Process Data]
  Check -->|No| Error[Show Error]
  Process --> End([End])
  Error --> End
```

## Verification Checklist

- [ ] All Mermaid diagrams render as visual graphics
- [ ] Decision nodes appear as diamond shapes
- [ ] Arrows and edge labels display correctly
- [ ] Scala code block renders as syntax-highlighted code
- [ ] No raw Mermaid syntax visible as text
