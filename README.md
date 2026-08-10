# marketplace-integration-service

Optional **SugamFlow Omnichannel** add-on (Amazon / Flipkart / Shopify / Website).

## Non-impact guarantees

- Own database `marketplacedb` (Flyway `flyway_schema_history_marketplace`)
- Compose profile **`marketplace`** — not started with the default ERP stack
- Entitlement / channel flags **OFF** by default
- `ModuleCode.MARKETPLACE` is **not** on STARTER/STANDARD/PROFESSIONAL/ENTERPRISE static plans — opt-in only
- Does **not** change stock-service math; reads sellable qty via HTTP only

## Local

```powershell
# create DB once
psql -U postgres -h localhost -f scripts/create-marketplacedb.sql

cd D:\sugamFlow\marketplace-integration-service
mvn -q spring-boot:run
# :8096  GET /api/v1/marketplace/status  (header X-Tenant-Id required)
```

Docker (opt-in):

```powershell
docker compose --env-file .env.local --profile marketplace up -d --build marketplace-integration-service
```

## Phase 2 APIs

Headers: `X-Tenant-Id`, `X-Shop-Id` (required for catalog/orders).

| Method | Path | Purpose |
|--------|------|---------|
| GET/POST | `/api/v1/marketplace/accounts` | Upsert marketplace account |
| GET/POST | `/api/v1/marketplace/channels` | Enable Amazon/Flipkart/Shopify/Website |
| GET/POST | `/api/v1/marketplace/mappings` | Map channel listing → `productId` |
| DELETE | `/api/v1/marketplace/mappings/{id}` | Soft-delete mapping |
| POST | `/api/v1/marketplace/orders/ingest` | Ingest order; optional `reserve-batch` |
| POST | `/api/v1/marketplace/orders/{id}/cancel` | Cancel + release reserved stock |
| GET | `/api/v1/marketplace/inventory/products/{id}/available` | Preview sellable qty |

When `MARKETPLACE_ENTITLEMENT_ENABLED=true`, shop-service must list module `MARKETPLACE` for the shop.


| Concept | Source |
|---------|--------|
| Physical | stock-service `quantity` |
| Reserved | stock-service `reserved` |
| Available | `quantity - reserved` |
| Marketplace allocation | `marketplace_inventory_sync.allocated_qty` (this DB) |

Existing clients without the module keep Physical → Available via POS reserve only.
