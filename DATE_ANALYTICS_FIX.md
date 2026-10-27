# CartRush date and revenue analytics fix

- Daily labels now include the year. Weekly labels show the full Monday–Sunday range.
- Orders with a NULL `created_at` are no longer silently counted as today's revenue. They remain in order-status counts but are excluded from dated trends/KPIs until corrected.
- `order_items.unit_price` stores the unit price at checkout for newly created order items. Hibernate `ddl-auto=update` will add this column when the application starts.
- Existing order items have no reliable purchase-time price snapshot. Analytics falls back to the current product price for those legacy rows; this cannot reconstruct historical prices that were never stored.

## Optional legacy-data review

Find orders that need their creation timestamp reviewed:

```sql
SELECT id, user_id, status, created_at
FROM orders
WHERE created_at IS NULL;
```

Do not fill these timestamps with the current date unless you have verified the actual order date.

Check legacy items without a saved purchase price:

```sql
SELECT id, order_id, product_id, quantity, unit_price
FROM order_items
WHERE unit_price IS NULL;
```
