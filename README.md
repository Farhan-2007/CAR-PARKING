
## System Architecture

ParkEasy follows a three-layer architecture consisting of the presentation layer, application layer, and data layer.

```mermaid
flowchart TD
    U["User / Admin"]
    F["Frontend Layer<br/>HTML, CSS, JavaScript"]
    B["Java Backend<br/>Main.java"]
    A["Authentication.java"]
    R["UserReg.java"]
    S["SlotBooking.java"]
    P["Payment.java"]
    AB["AdminBookings.java"]
    D[("PostgreSQL Database<br/>carparkings")]
    T["Tables: users, slots,<br/>bookings, payments"]

    U --> F
    F <-->|"HTTP Requests / JSON"| B
    B --> A
    B --> R
    B --> S
    B --> P
    B --> AB
    A <--> D
    R <--> D
    S <--> D
    P <--> D
    AB <--> D
    D --- T
```

### Architecture Layers

1. **Presentation Layer:** Provides registration, login, parking slot booking, booking history, and admin pages using HTML, CSS, and JavaScript.
2. **Application Layer:** Java handles API requests, authentication, user registration, slot booking, payment logic, and admin booking records.
3. **Data Layer:** PostgreSQL stores user details, parking slots, bookings, and payment records.

### Technology Stack

| Component | Technology |
|---|---|
| Frontend | HTML, CSS, JavaScript |
| Backend | Java |
| Database | PostgreSQL |
| Database Connectivity | JDBC |
| Communication | HTTP and JSON |

**Data flow:** User/Admin → Frontend → Java Backend → PostgreSQL → Response to Frontend.

*Note: The payment component represents the project's implemented payment logic; it does not imply integration with a live payment gateway.*
