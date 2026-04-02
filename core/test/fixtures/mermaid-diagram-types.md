# Mermaid Diagram Types Test Fixtures

This file contains examples of different Mermaid diagram types to validate that MarkdownRenderer correctly transforms them all.

## Flowchart

```mermaid
graph TD
  A[Start] --> B{Decision}
  B -->|Yes| C[Action 1]
  B -->|No| D[Action 2]
  C --> E[End]
  D --> E
```

## Sequence Diagram

```mermaid
sequenceDiagram
  participant A as Alice
  participant B as Bob
  participant C as Charlie
  A->>B: Hello Bob
  B->>C: Forward to Charlie
  C->>A: Response to Alice
  Note right of C: Charlie processes request
```

## Class Diagram

```mermaid
classDiagram
  class Animal {
    +String name
    +int age
    +makeSound()
  }
  class Dog {
    +String breed
    +bark()
  }
  class Cat {
    +meow()
  }
  Animal <|-- Dog
  Animal <|-- Cat
```

## State Diagram

```mermaid
stateDiagram-v2
  [*] --> Idle
  Idle --> Processing : Start
  Processing --> Complete : Success
  Processing --> Error : Failure
  Complete --> [*]
  Error --> Idle : Retry
  Error --> [*]
```

## Pie Chart

```mermaid
pie title Project Time Distribution
  "Development" : 40
  "Testing" : 30
  "Documentation" : 20
  "Meetings" : 10
```

## ER Diagram

```mermaid
erDiagram
  CUSTOMER ||--o{ ORDER : places
  ORDER ||--|{ LINE-ITEM : contains
  CUSTOMER {
    string name
    string email
  }
  ORDER {
    int orderNumber
    date orderDate
  }
```

## Gantt Chart

```mermaid
gantt
  title Project Schedule
  dateFormat  YYYY-MM-DD
  section Phase 1
  Task 1 :a1, 2025-01-01, 7d
  Task 2 :a2, after a1, 5d
  section Phase 2
  Task 3 :a3, 2025-01-15, 10d
```

## Git Graph

```mermaid
gitGraph
  commit
  commit
  branch develop
  checkout develop
  commit
  commit
  checkout main
  merge develop
  commit
```
