# Existing application audit — 11 November 2026

Active application: Spring Boot 4.1.2 / Java 26, Thymeleaf, vendored Alpine, compiled Tailwind. Component scanning and Maven deliberately exclude the earlier database/security implementation. No database is active. README.md is stale; CAREPLUS.md describes the active application.

Reusable: 47 schema-driven management/catalog screens, CRUD drawers, form validation, optimistic versions, filters/calendar, attachments, laboratory and imaging state machines, FEFO dispensing, procurement receipt, invoices/payments/refunds, audit and browser tests.

Gaps found:
- Clinical records are related by patient only; consultation/order/queue records have no encounter reference. OPD check-in creates a visit and queue without linking them. Appointment Complete can bypass clinical work.
- Doctor documentation lives in generic drawers. Patient history and current visit are mixed; certificate is free text and print action rather than an issuance workflow.
- Queue transfer ends a step without a destination step; on-hold/in-service/station states and focused doctor queue are missing.
- Clinical drafts require a diagnosis; signed documentation can be edited. Vitals lack format/unit validation.
- Role switching changes navigation only; all session users can call all generic APIs. Browser permission preferences are not authorization.
- English strings are hardcoded throughout templates, JS and catalog. No Khmer architecture or Khmer typography.
- Theme uses semantic variables but legacy literal colors remain; system preference and uniform print treatment are incomplete.
- No Telegram/mobile portal, scoped check-in tokens, released patient-record projection, or account-linking boundary.
- Operational workflows already function; retain and connect rather than duplicate them.

Implementation: extend the existing HospitalService/in-memory store, add encounter workspace and mobile projections, keep generic modules, apply shared localization/theme controls, add service/API/browser regression coverage. Synthetic demo only.

Workflow references: [HL7 Appointment](https://fhir.hl7.org/fhir/appointment.html), [ServiceRequest](https://hl7.org/fhir/servicerequest.html), [DiagnosticReport](https://fhir.hl7.org/fhir/diagnosticreport.html). Resource distinctions inform this prototype; it does not implement a FHIR API. [Telegram verification](https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app).
