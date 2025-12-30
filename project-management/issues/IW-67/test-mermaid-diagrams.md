# Mermaid Diagram Types - Manual E2E Verification

This document contains examples of all major Mermaid diagram types for manual browser testing. Each diagram should render correctly in the artifact viewer.

## Test Instructions

1. Start the iw-cli server: `./iw server`
2. Register this worktree: `./iw register`
3. Open the dashboard in a browser
4. Click on this artifact to view it
5. Verify that all diagrams below render correctly

---

## 1. Flowchart (Already verified in Phase 1)

This is the basic diagram type verified in Phase 1. It should still work (regression test).

```mermaid
graph TD
  A[Start] --> B{Decision}
  B -->|Yes| C[Action 1]
  B -->|No| D[Action 2]
  C --> E[End]
  D --> E
```

**Expected result:** A flowchart with a diamond decision node and arrows labeled "Yes" and "No".

---

## 2. Sequence Diagram

Sequence diagrams show interactions between participants over time.

```mermaid
sequenceDiagram
  participant User
  participant API
  participant Database

  User->>API: Request data
  activate API
  API->>Database: Query
  activate Database
  Database-->>API: Results
  deactivate Database
  API-->>User: Response
  deactivate API
```

**Expected result:** Three vertical participant lines with arrows showing message flow between them.

---

## 3. Class Diagram

Class diagrams show object-oriented relationships.

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
    +boolean indoor
    +meow()
  }
  Animal <|-- Dog
  Animal <|-- Cat
```

**Expected result:** Three boxes with class names, attributes, and methods, connected by inheritance arrows.

---

## 4. State Diagram

State diagrams show state transitions in a system.

```mermaid
stateDiagram-v2
  [*] --> Idle
  Idle --> Processing : User starts task
  Processing --> Complete : Success
  Processing --> Error : Failure
  Complete --> [*]
  Error --> Idle : Retry
  Error --> [*] : Give up
```

**Expected result:** Circles/boxes representing states with labeled transition arrows.

---

## 5. Pie Chart

Pie charts show data distribution.

```mermaid
pie title Team Time Distribution
  "Development" : 40
  "Testing" : 30
  "Code Review" : 15
  "Documentation" : 10
  "Meetings" : 5
```

**Expected result:** A circular pie chart with colored segments and a legend.

---

## 6. Entity-Relationship Diagram

ER diagrams show database schema relationships.

```mermaid
erDiagram
  CUSTOMER ||--o{ ORDER : places
  ORDER ||--|{ LINE-ITEM : contains
  PRODUCT ||--o{ LINE-ITEM : "ordered in"

  CUSTOMER {
    string name
    string email
    string phone
  }
  ORDER {
    int orderNumber
    date orderDate
    float total
  }
  PRODUCT {
    string sku
    string name
    float price
  }
```

**Expected result:** Boxes representing entities with their attributes, connected by relationship lines.

---

## 7. Gantt Chart

Gantt charts show project timelines.

```mermaid
gantt
  title Development Timeline
  dateFormat  YYYY-MM-DD

  section Phase 1
  Mermaid Integration    :done, phase1, 2025-12-28, 2d

  section Phase 2
  Error Handling         :done, phase2, 2025-12-29, 1d

  section Phase 3
  Diagram Types          :active, phase3, 2025-12-30, 1d
```

**Expected result:** A horizontal bar chart showing tasks on a timeline.

---

## 8. Git Graph

Git graphs visualize branch and merge operations.

```mermaid
gitGraph
  commit id: "Initial"
  commit id: "Add feature A"
  branch develop
  checkout develop
  commit id: "Start feature B"
  commit id: "Complete feature B"
  checkout main
  merge develop
  commit id: "Release v1.0"
```

**Expected result:** A graph showing commits and branches with merge operations.

---

## Verification Checklist

After viewing this artifact in the browser, verify:

- [ ] All 8 diagram types render without errors
- [ ] No "Syntax error" messages from Mermaid
- [ ] Diagrams are readable and properly styled
- [ ] No JavaScript console errors
- [ ] Flowchart still works (regression test for Phase 1)
- [ ] Error handling still works (regression test for Phase 2)

---

## Notes

- All diagrams use Mermaid.js v10.9.4 from CDN
- Theme: `neutral`
- Security level: `loose` (for error display)
- The existing `transformMermaidBlocks()` function handles all diagram types uniformly
- No code changes were needed for Phase 3 - the implementation is diagram-type agnostic
