using System.Globalization;
using Bcv.Api.Modules.Account.Application;
using Bcv.Api.Modules.Explore.Infrastructure;
using Npgsql;
using NpgsqlTypes;

namespace Bcv.Api.Modules.Inventory;

public sealed record SpeedShoesJourney(long JourneyId, string DestinationName, int RecordedSteps, int RequiredSteps);

/// <summary>A boost started on the current journey; RemainingSteps &gt; 0 while it still applies.</summary>
public sealed record SpeedShoesEffect(decimal Multiplier, int Range, int RemainingSteps);

public sealed record SpeedShoesStatus(long ItemId, string Name, bool Active, int Quantity, decimal Multiplier,
    int Range, SpeedShoesJourney? Journey, SpeedShoesEffect? Effect);

public sealed record RegionOption(long RegionId, string Name, int PlaceCount);

public sealed record FlightTicketStatus(long ItemId, string Name, bool Active, int Quantity, bool HasActiveJourney,
    string? CurrentRegion, long? CurrentRegionId, IReadOnlyList<RegionOption> Regions);

public sealed record FlightPlace(long PlaceId, string Name, bool Discovered);

public sealed record UsePlaceRequest(long PlaceId);

public sealed record FlightResult(string Region, string Place, int Quantity);

public sealed class ItemUseException(string code, string message) : Exception(message)
{
    public string Code { get; } = code;
}

/// <summary>
/// Speed-shoe boosts are kept without new tables: activation writes a zero-step STEP_DATA row whose
/// source id records the journey, the journey's raw step count at activation and the item's effect,
/// e.g. "speed-shoes:journey:42:from:1300:x2.00:r5000". Step sync multiplies the raw steps that fall
/// in [from, from + range) — boosted steps only count toward that journey, never toward step totals.
/// </summary>
public static class SpeedShoes
{
    public const string Type = "SPEED_SHOES";
    private const string Prefix = "speed-shoes:journey:";

    public static string SourceId(long journeyId, int from, decimal multiplier, int range) =>
        $"{Prefix}{journeyId}:from:{from}:x{multiplier.ToString("0.00", CultureInfo.InvariantCulture)}:r{range}";

    public static string HealthSourceId(long journeyId) => $"health-connect:journey:{journeyId}";

    public sealed record Boost(int From, decimal Multiplier, int Range);

    public static async Task<List<Boost>> LoadBoostsAsync(NpgsqlConnection connection, NpgsqlTransaction? transaction,
        long userId, long journeyId, CancellationToken ct)
    {
        var boosts = new List<Boost>();
        await using var command = new NpgsqlCommand("""
            SELECT source_data_id FROM public.step_data WHERE user_id = $1 AND source_data_id LIKE $2
            """, connection, transaction);
        command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
        command.Parameters.Add(new NpgsqlParameter<string> { TypedValue = $"{Prefix}{journeyId}:%", NpgsqlDbType = NpgsqlDbType.Varchar });
        await using var reader = await command.ExecuteReaderAsync(ct);
        while (await reader.ReadAsync(ct))
        {
            var parts = reader.GetString(0)[(Prefix.Length)..].Split(':');
            // {journeyId}:from:{from}:x{multiplier}:r{range}
            if (parts.Length == 5 && int.TryParse(parts[2], out var from)
                && decimal.TryParse(parts[3].TrimStart('x'), NumberStyles.Number, CultureInfo.InvariantCulture, out var multiplier)
                && int.TryParse(parts[4].TrimStart('r'), out var range))
                boosts.Add(new Boost(from, multiplier, range));
        }
        return boosts;
    }

    /// <summary>Extra journey steps earned when raw steps move from <paramref name="previous"/> to <paramref name="current"/>.</summary>
    public static int Bonus(IEnumerable<Boost> boosts, int previous, int current)
    {
        decimal bonus = 0;
        foreach (var boost in boosts)
        {
            var boosted = Math.Min(current, boost.From + boost.Range) - Math.Max(previous, boost.From);
            if (boosted > 0) bonus += boosted * (boost.Multiplier - 1m);
        }
        return (int)Math.Floor(Math.Max(0, bonus));
    }

    public static async Task<int> RawStepsAsync(NpgsqlConnection connection, NpgsqlTransaction? transaction,
        long userId, long journeyId, CancellationToken ct)
    {
        await using var command = new NpgsqlCommand("""
            SELECT step_count FROM public.step_data WHERE user_id = $1 AND source_data_id = $2
            """, connection, transaction);
        command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
        command.Parameters.Add(new NpgsqlParameter<string> { TypedValue = HealthSourceId(journeyId), NpgsqlDbType = NpgsqlDbType.Varchar });
        return await command.ExecuteScalarAsync(ct) is int steps ? steps : 0;
    }
}

public sealed class ItemUseRepository(NpgsqlDataSource db)
{
    private const string FlightType = "FLIGHT_TICKET";

    private sealed record OwnedItem(long ItemId, string Name, bool Active, int Quantity, decimal? EffectValue, int? Range);

    private static async Task<OwnedItem> LoadItemAsync(NpgsqlConnection connection, NpgsqlTransaction? transaction,
        long userId, string type, bool forUpdate, CancellationToken ct)
    {
        await using var command = new NpgsqlCommand($"""
            SELECT i.item_id, i.name, i.status <> 'INACTIVE', COALESCE(ui.quantity, 0), i.effect_value, i.effective_range
            FROM public.item i
            LEFT JOIN public.user_item ui ON ui.item_id = i.item_id AND ui.user_id = $1
            WHERE i.item_type = $2
            ORDER BY i.item_id LIMIT 1
            {(forUpdate ? "FOR UPDATE OF i" : "")}
            """, connection, transaction);
        command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
        command.Parameters.Add(new NpgsqlParameter<string> { TypedValue = type, NpgsqlDbType = NpgsqlDbType.Varchar });
        await using var reader = await command.ExecuteReaderAsync(ct);
        if (!await reader.ReadAsync(ct)) throw new ItemUseException("ITEM_NOT_FOUND", "Không tìm thấy vật phẩm.");
        return new OwnedItem(reader.GetInt64(0), reader.GetString(1), reader.GetBoolean(2), reader.GetInt32(3),
            reader.IsDBNull(4) ? null : reader.GetDecimal(4), reader.IsDBNull(5) ? null : reader.GetInt32(5));
    }

    /// <summary>Takes one item; fails rather than going below zero when another request used it first.</summary>
    private static async Task ConsumeOneAsync(NpgsqlConnection connection, NpgsqlTransaction transaction,
        long userId, long itemId, CancellationToken ct)
    {
        await using var command = new NpgsqlCommand("""
            UPDATE public.user_item SET quantity = quantity - 1, updated_at = CURRENT_TIMESTAMP
            WHERE user_id = $1 AND item_id = $2 AND quantity > 0
            """, connection, transaction);
        command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
        command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = itemId, NpgsqlDbType = NpgsqlDbType.Bigint });
        if (await command.ExecuteNonQueryAsync(ct) == 0)
            throw new ItemUseException("NOT_OWNED", "Bạn không còn vật phẩm này.");
    }

    public async Task<SpeedShoesStatus> ShoesAsync(long userId, CancellationToken ct)
    {
        await using var connection = await db.OpenConnectionAsync(ct);
        return await ShoesAsync(connection, null, userId, ct);
    }

    private static async Task<SpeedShoesStatus> ShoesAsync(NpgsqlConnection connection, NpgsqlTransaction? transaction,
        long userId, CancellationToken ct)
    {
        var item = await LoadItemAsync(connection, transaction, userId, SpeedShoes.Type, false, ct);
        SpeedShoesJourney? journey = null;
        await using (var command = new NpgsqlCommand("""
            SELECT j.journey_id, p.name, j.recorded_steps, j.required_steps
            FROM public.journey j JOIN public.place p ON p.place_id = j.destination_place_id
            WHERE j.user_id = $1 AND j.status = 'IN_PROGRESS'
            """, connection, transaction))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            await using var reader = await command.ExecuteReaderAsync(ct);
            if (await reader.ReadAsync(ct))
                journey = new SpeedShoesJourney(reader.GetInt64(0), reader.GetString(1), reader.GetInt32(2), reader.GetInt32(3));
        }

        SpeedShoesEffect? effect = null;
        if (journey is not null)
        {
            var boosts = await SpeedShoes.LoadBoostsAsync(connection, transaction, userId, journey.JourneyId, ct);
            var latest = boosts.MaxBy(b => b.From);
            if (latest is not null)
            {
                var raw = await SpeedShoes.RawStepsAsync(connection, transaction, userId, journey.JourneyId, ct);
                effect = new SpeedShoesEffect(latest.Multiplier, latest.Range,
                    Math.Max(0, latest.Range - Math.Max(0, raw - latest.From)));
            }
        }
        return new SpeedShoesStatus(item.ItemId, item.Name, item.Active, item.Quantity,
            item.EffectValue ?? 1m, item.Range ?? 0, journey, effect);
    }

    public async Task<SpeedShoesStatus> UseShoesAsync(long userId, CancellationToken ct)
    {
        await using var connection = await db.OpenConnectionAsync(ct);
        await using var transaction = await connection.BeginTransactionAsync(ct);
        var item = await LoadItemAsync(connection, transaction, userId, SpeedShoes.Type, true, ct);
        if (!item.Active) throw new ItemUseException("ITEM_INACTIVE", "Vật phẩm đang tạm ngưng hoạt động.");
        if (item.Quantity <= 0) throw new ItemUseException("NOT_OWNED", "Bạn chưa sở hữu giày tăng tốc.");
        if (item.EffectValue is not > 1m || item.Range is not > 0)
            throw new ItemUseException("ITEM_MISCONFIGURED", "Vật phẩm chưa được cấu hình hiệu ứng.");

        var status = await ShoesAsync(connection, transaction, userId, ct);
        if (status.Journey is null)
            throw new ItemUseException("NO_ACTIVE_JOURNEY", "Cần có hành trình đang thực hiện để dùng giày.");
        if (status.Effect is { RemainingSteps: > 0 })
            throw new ItemUseException("EFFECT_ACTIVE", "Hiệu ứng tăng tốc trước vẫn đang áp dụng.");

        var raw = await SpeedShoes.RawStepsAsync(connection, transaction, userId, status.Journey.JourneyId, ct);
        await ConsumeOneAsync(connection, transaction, userId, item.ItemId, ct);
        await using (var command = new NpgsqlCommand("""
            INSERT INTO public.step_data (user_id, source_data_id, step_count, start_time, end_time)
            VALUES ($1, $2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id, source_data_id) DO NOTHING
            """, connection, transaction))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            command.Parameters.Add(new NpgsqlParameter<string>
            {
                TypedValue = SpeedShoes.SourceId(status.Journey.JourneyId, raw, item.EffectValue.Value, item.Range.Value),
                NpgsqlDbType = NpgsqlDbType.Varchar
            });
            if (await command.ExecuteNonQueryAsync(ct) == 0)
                throw new ItemUseException("EFFECT_ACTIVE", "Hiệu ứng tăng tốc trước vẫn đang áp dụng.");
        }
        var result = await ShoesAsync(connection, transaction, userId, ct);
        await transaction.CommitAsync(ct);
        return result;
    }

    public async Task<FlightTicketStatus> FlightAsync(long userId, CancellationToken ct)
    {
        await using var connection = await db.OpenConnectionAsync(ct);
        var item = await LoadItemAsync(connection, null, userId, FlightType, false, ct);
        bool hasJourney;
        await using (var command = new NpgsqlCommand("""
            SELECT EXISTS (SELECT 1 FROM public.journey WHERE user_id = $1 AND status = 'IN_PROGRESS')
            """, connection))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            hasJourney = (bool)(await command.ExecuteScalarAsync(ct))!;
        }
        string? currentName = null;
        long? currentId = null;
        await using (var command = new NpgsqlCommand("""
            SELECT r.region_id, r.name FROM public.virtual_location v
            JOIN public.region r ON r.region_id = v.region_id WHERE v.user_id = $1
            """, connection))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            await using var reader = await command.ExecuteReaderAsync(ct);
            if (await reader.ReadAsync(ct)) { currentId = reader.GetInt64(0); currentName = reader.GetString(1); }
        }
        var regions = new List<RegionOption>();
        await using (var command = new NpgsqlCommand("""
            SELECT r.region_id, r.name, COUNT(p.place_id)::int
            FROM public.region r
            JOIN public.place p ON p.region_id = r.region_id AND p.status <> 'INACTIVE'
            WHERE r.status <> 'INACTIVE' AND r.region_id IS DISTINCT FROM $1
            GROUP BY r.region_id, r.name
            ORDER BY r.name
            """, connection))
        {
            command.Parameters.Add(new NpgsqlParameter<long?> { TypedValue = currentId, NpgsqlDbType = NpgsqlDbType.Bigint });
            await using var reader = await command.ExecuteReaderAsync(ct);
            while (await reader.ReadAsync(ct))
                regions.Add(new RegionOption(reader.GetInt64(0), reader.GetString(1), reader.GetInt32(2)));
        }
        return new FlightTicketStatus(item.ItemId, item.Name, item.Active, item.Quantity, hasJourney,
            currentName, currentId, regions);
    }

    public async Task<IReadOnlyList<FlightPlace>> RegionPlacesAsync(long userId, long regionId, CancellationToken ct)
    {
        await using var connection = await db.OpenConnectionAsync(ct);
        await using var command = new NpgsqlCommand("""
            SELECT p.place_id, p.name,
                   EXISTS (SELECT 1 FROM public.user_discovered_place d WHERE d.user_id = $1 AND d.place_id = p.place_id)
            FROM public.place p
            JOIN public.region r ON r.region_id = p.region_id AND r.status <> 'INACTIVE'
            WHERE p.region_id = $2 AND p.status <> 'INACTIVE'
            ORDER BY p.rating_count DESC, p.name
            """, connection);
        command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
        command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = regionId, NpgsqlDbType = NpgsqlDbType.Bigint });
        var places = new List<FlightPlace>();
        await using var reader = await command.ExecuteReaderAsync(ct);
        while (await reader.ReadAsync(ct))
            places.Add(new FlightPlace(reader.GetInt64(0), reader.GetString(1), reader.GetBoolean(2)));
        return places;
    }

    /// <summary>Moves the virtual location to the place and unlocks it (discovery, card, quest progress).</summary>
    public async Task<FlightResult> UseFlightAsync(long userId, long placeId, CancellationToken ct)
    {
        await using var connection = await db.OpenConnectionAsync(ct);
        await using var transaction = await connection.BeginTransactionAsync(ct);
        var item = await LoadItemAsync(connection, transaction, userId, FlightType, true, ct);
        if (!item.Active) throw new ItemUseException("ITEM_INACTIVE", "Vật phẩm đang tạm ngưng hoạt động.");
        if (item.Quantity <= 0) throw new ItemUseException("NOT_OWNED", "Bạn chưa sở hữu vé máy bay.");
        await using (var command = new NpgsqlCommand("""
            SELECT EXISTS (SELECT 1 FROM public.journey WHERE user_id = $1 AND status = 'IN_PROGRESS')
            """, connection, transaction))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            if ((bool)(await command.ExecuteScalarAsync(ct))!)
                throw new ItemUseException("JOURNEY_ACTIVE", "Hãy kết thúc hành trình hiện tại trước khi dùng vé.");
        }

        string region, place;
        await using (var command = new NpgsqlCommand("""
            UPDATE public.virtual_location v
            SET region_id = p.region_id, nearest_place_id = p.place_id,
                latitude = p.latitude, longitude = p.longitude, updated_at = CURRENT_TIMESTAMP
            FROM public.place p JOIN public.region r ON r.region_id = p.region_id
            WHERE v.user_id = $1 AND p.place_id = $2 AND p.status <> 'INACTIVE' AND r.status <> 'INACTIVE'
              AND p.region_id <> v.region_id
            RETURNING r.name, p.name
            """, connection, transaction))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = placeId, NpgsqlDbType = NpgsqlDbType.Bigint });
            await using var reader = await command.ExecuteReaderAsync(ct);
            if (!await reader.ReadAsync(ct))
                throw new ItemUseException("INVALID_PLACE", "Địa danh này không hợp lệ cho vé máy bay.");
            region = reader.GetString(0);
            place = reader.GetString(1);
        }
        await ConsumeOneAsync(connection, transaction, userId, item.ItemId, ct);
        // Landing counts as arriving: the place is unlocked just as at the end of a journey.
        await PlaceUnlock.UnlockAsync(connection, transaction, userId, placeId, ct);
        await transaction.CommitAsync(ct);
        return new FlightResult(region, place, item.Quantity - 1);
    }
}

public static class ItemUseEndpoints
{
    public static void MapItemUseEndpoints(this WebApplication app)
    {
        var group = app.MapGroup("/api/items");
        group.MapGet("/speed-shoes", (HttpRequest request, ISessionStore sessions, ItemUseRepository items,
            ILoggerFactory logs, CancellationToken ct) => Run(request, sessions, logs, user => items.ShoesAsync(user, ct)));
        group.MapPost("/speed-shoes/use", (HttpRequest request, ISessionStore sessions, ItemUseRepository items,
            ILoggerFactory logs, CancellationToken ct) => Run(request, sessions, logs, user => items.UseShoesAsync(user, ct)));
        group.MapGet("/flight-ticket", (HttpRequest request, ISessionStore sessions, ItemUseRepository items,
            ILoggerFactory logs, CancellationToken ct) => Run(request, sessions, logs, user => items.FlightAsync(user, ct)));
        group.MapGet("/flight-ticket/regions/{regionId:long}/places", (long regionId, HttpRequest request,
            ISessionStore sessions, ItemUseRepository items, ILoggerFactory logs, CancellationToken ct) =>
            Run(request, sessions, logs, async user => new { places = await items.RegionPlacesAsync(user, regionId, ct) }));
        group.MapPost("/flight-ticket/use", (UsePlaceRequest body, HttpRequest request, ISessionStore sessions,
            ItemUseRepository items, ILoggerFactory logs, CancellationToken ct) =>
            Run(request, sessions, logs, user => items.UseFlightAsync(user, body.PlaceId, ct)));
    }

    private static async Task<IResult> Run<T>(HttpRequest request, ISessionStore sessions, ILoggerFactory logs,
        Func<long, Task<T>> action)
    {
        var header = request.Headers.Authorization.ToString();
        var user = sessions.Get(header.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase) ? header[7..] : null);
        if (user is null) return Results.Unauthorized();
        request.HttpContext.Response.Headers.CacheControl = "no-store";
        try { return Results.Ok(await action(user.UserId)); }
        catch (ItemUseException ex)
        {
            return Results.Json(new InventoryError(ex.Code, ex.Message), statusCode: 409);
        }
        catch (Exception ex)
        {
            logs.CreateLogger("ItemUse").LogError(ex, "Item request failed");
            return Results.Json(new InventoryError("SERVER_ERROR", "Máy chủ đang gặp lỗi. Vui lòng thử lại."),
                statusCode: 500);
        }
    }
}
