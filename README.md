# School Clinical and Administrative Supervision Platform

## Projects
- `backend-spring/`: Spring Boot backend API with multi-tenant schema, JWT auth, dynamic checklist, review execution, GPS validation, and report endpoint.
- `mobile-flutter/`: Mobile-first Flutter app shell with dynamic checklist renderer (`ALL_AT_ONCE`, `ONE_BY_ONE`, `GROUPED`), location capture hook, and signature capture.
- `web-portal/`: Simple role-gated web portal scaffold for `SUPER_ADMIN` and `CLUSTER_COORDINATOR`.

## Example Checklist Render JSON
```json
{
  "checklistId": "eebf40da-9f2f-4d33-bc4a-2d6436d3f82a",
  "version": 2,
  "displayMode": "GROUPED",
  "items": [
    {
      "id": "7f598f49-ac9b-4955-8bfd-e8f422e2cbcf",
      "question": "Is lesson plan prepared?",
      "type": "YES_NO",
      "groupKey": "AcademicReadiness",
      "order": 1,
      "options": {},
      "validation": { "required": true }
    },
    {
      "id": "90bc5940-3adc-4993-ae0b-c842d1024b8d",
      "question": "Upload class activity photos",
      "type": "PHOTO",
      "groupKey": "Evidence",
      "order": 2,
      "options": {},
      "validation": { "required": true, "maxPhotos": 3 }
    }
  ]
}
```
