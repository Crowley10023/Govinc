# External Assessment API

This API allows external systems to read assessment data from Govinc.

## Authentication

All endpoints require an API key in the request header.

`X-API-Key: <your_api_key>`

If the key is missing or invalid, the API returns `401 Unauthorized`.

## Base URL

`/public-api/v1`

## Endpoints

### 1. List security catalogs

- Method: `GET`
- Path: `/public-api/v1/catalogs`

Response example:

```json
{
  "catalogs": [
    {
      "id": 1,
      "name": "ISO 27001",
      "revision": "2022",
      "headline": "ISO Information Security Controls"
    }
  ]
}
```

### 2. List assessments for a catalog

- Method: `GET`
- Path: `/public-api/v1/catalogs/{catalogId}/assessments`

Response example:

```json
{
  "catalogId": 1,
  "assessments": [
    {
      "id": 42,
      "name": "Core Banking Review",
      "status": "OPEN",
      "creationDate": "2026-07-03",
      "closeDate": null,
      "orgUnit": "Risk"
    }
  ]
}
```

### 3. Get all answers for an assessment

- Method: `GET`
- Path: `/public-api/v1/assessments/{assessmentId}/answers`

Response example:

```json
{
  "assessment": {
    "id": 42,
    "name": "Core Banking Review",
    "status": "OPEN",
    "creationDate": "2026-07-03",
    "closeDate": null,
    "catalogId": 1,
    "catalogName": "ISO 27001"
  },
  "answers": [
    {
      "controlId": 101,
      "controlName": "Access Management",
      "controlDetail": "User lifecycle and access rights",
      "domainId": 7,
      "domainName": "Identity",
      "answerId": 5,
      "answer": "Partially Implemented",
      "rating": 50,
      "comment": "MFA rollout in progress",
      "notApplicable": false,
      "override": false
    }
  ]
}
```

## Curl examples

```bash
curl -H "X-API-Key: YOUR_KEY" http://localhost:8080/public-api/v1/catalogs
```

```bash
curl -H "X-API-Key: YOUR_KEY" http://localhost:8080/public-api/v1/catalogs/1/assessments
```

```bash
curl -H "X-API-Key: YOUR_KEY" http://localhost:8080/public-api/v1/assessments/42/answers
```
