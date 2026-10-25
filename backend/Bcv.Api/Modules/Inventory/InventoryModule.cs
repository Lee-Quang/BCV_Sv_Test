using Bcv.Api.Modules.Account.Application;
using Npgsql;
using NpgsqlTypes;

namespace Bcv.Api.Modules.Inventory;

/// <summary>An item type with how many the user holds; unowned types are listed with quantity 0.</summary>
public sealed record InventoryItem(long ItemId, string Name, string ItemType, string? Description,
    decimal? EffectValue, int? EffectiveRange, int? UsageLimit, bool Active, int Quantity);

/// <summary>HasActiveJourney drives the usage conditions: shoes need one, a flight ticket needs none.</summary>
public sealed record InventoryOverview(int TotalQuantity, bool HasActiveJourney, bool BoostActive,
    IReadOnlyList<InventoryItem> Items);

public sealed record InventoryError(string Code, string Message);

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
            var raw = await SpeedShoes.RawStepsAsync(connection, null, userId, id, ct);
            boostActive = (await SpeedShoes.LoadBoostsAsync(connection, null, userId, id, ct))
                .Any(b => raw < b.From + b.Range);
        }
        return new InventoryOverview(items.Sum(i => i.Quantity), journeyId is not null, boostActive, items);
    }
}

public static class InventoryEndpoints
{
    public static void MapInventoryEndpoints(this WebApplication app)
    {
        app.MapGet("/api/inventory", async (HttpRequest request, ISessionStore sessions,
            InventoryRepository repository, ILoggerFactory logs, CancellationToken ct) =>
        {
            var header = request.Headers.Authorization.ToString();
            var token = header.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase) ? header[7..] : null;
            var user = sessions.Get(token);
            if (user is null) return Results.Unauthorized();
            request.HttpContext.Response.Headers.CacheControl = "no-store";
            try { return Results.Ok(await repository.LoadAsync(user.UserId, ct)); }
            catch (Exception ex)
            {
                logs.CreateLogger("Inventory").LogError(ex, "Inventory request failed");
                return Results.Json(new InventoryError("SERVER_ERROR", "Máy chủ đang gặp lỗi. Vui lòng thử lại."),
                    statusCode: 500);
            }
        });
    }
}
