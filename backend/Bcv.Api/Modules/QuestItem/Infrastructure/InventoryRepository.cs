using Npgsql;
using NpgsqlTypes;
using Bcv.Api.Modules.QuestItem.Application;

namespace Bcv.Api.Modules.QuestItem.Infrastructure;

public sealed class InventoryRepository(NpgsqlDataSource db)
{
    public async Task<InventoryOverview> LoadAsync(long userId, CancellationToken ct)
    {
        await using var connection = await db.OpenConnectionAsync(ct);
        var items = new List<InventoryItem>();
        await using (var command = new NpgsqlCommand("""
            SELECT i.item_id, i.name, i.item_type, i.description, i.effect_value, i.effective_range,
                   i.usage_limit, i.status <> 'INACTIVE', COALESCE(ui.quantity, 0)
            FROM public.item i
            LEFT JOIN public.user_item ui ON ui.item_id = i.item_id AND ui.user_id = $1
            ORDER BY i.item_id
            """, connection))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            await using var reader = await command.ExecuteReaderAsync(ct);
            while (await reader.ReadAsync(ct))
            {
                items.Add(new InventoryItem(reader.GetInt64(0), reader.GetString(1), reader.GetString(2),
                    reader.IsDBNull(3) ? null : reader.GetString(3),
                    reader.IsDBNull(4) ? null : reader.GetDecimal(4),
                    reader.IsDBNull(5) ? null : reader.GetInt32(5),
                    reader.IsDBNull(6) ? null : reader.GetInt32(6),
                    reader.GetBoolean(7), reader.GetInt32(8)));
            }
        }

        long? journeyId;
        await using (var command = new NpgsqlCommand("""
            SELECT journey_id FROM public.journey WHERE user_id = $1 AND status = 'IN_PROGRESS'
            """, connection))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            journeyId = await command.ExecuteScalarAsync(ct) as long?;
        }
        // A speed-shoe boost still running on this journey blocks starting another one.
        var boostActive = false;
        if (journeyId is long id)
        {
            var raw = await SpeedShoesStore.RawStepsAsync(connection, null, userId, id, ct);
            boostActive = (await SpeedShoesStore.LoadBoostsAsync(connection, null, userId, id, ct))
                .Any(b => raw < b.From + b.Range);
        }
        return new InventoryOverview(items.Sum(i => i.Quantity), journeyId is not null, boostActive, items);
    }
}
