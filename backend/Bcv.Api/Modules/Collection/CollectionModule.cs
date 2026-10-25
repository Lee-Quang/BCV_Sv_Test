using Bcv.Api.Modules.Account.Application;
using Npgsql;
using NpgsqlTypes;

namespace Bcv.Api.Modules.Collection;

public sealed record CollectionSummary(long CollectionId, string Name, string? Description,
    int OwnedCards, int TotalCards, IReadOnlyList<string> PreviewImageUrls);

public sealed record CollectionOverview(int OwnedCards, int TotalCards, IReadOnlyList<CollectionSummary> Collections);

public sealed record CollectionError(string Code, string Message);

/// <summary>A card in a collection. ImageUrl is only sent once owned, like a locked place's content.</summary>
public sealed record CollectionCard(long CardId, long PlaceId, string Name, string? ImageUrl, bool Owned);

public sealed record CollectionDetail(long CollectionId, string Name, string? Description,
    int OwnedCards, int TotalCards, IReadOnlyList<CollectionCard> Cards);

/// <summary>
/// "Bộ sưu tập" tab. A card counts as owned when the user holds it in USER_CARD or has discovered
/// its place, so places unlocked before cards were granted still show up.
/// </summary>
public sealed class CollectionRepository(NpgsqlDataSource db)
{
    private const string Owned = """
        (EXISTS (SELECT 1 FROM public.user_card uc WHERE uc.user_id = $1 AND uc.card_id = c.card_id)
         OR EXISTS (SELECT 1 FROM public.user_discovered_place d
                    WHERE d.user_id = $1 AND d.place_id = c.place_id))
        """;

    public async Task<CollectionOverview> LoadAsync(long userId, CancellationToken ct)
    {
        await using var connection = await db.OpenConnectionAsync(ct);
        int ownedCards, totalCards;
        await using (var command = new NpgsqlCommand($"""
            SELECT count(*) FILTER (WHERE {Owned}), count(*)
            FROM public.card c WHERE c.status <> 'INACTIVE'
            """, connection))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            await using var reader = await command.ExecuteReaderAsync(ct);
            await reader.ReadAsync(ct);
            ownedCards = (int)reader.GetInt64(0);
            totalCards = (int)reader.GetInt64(1);
        }

        var collections = new List<CollectionSummary>();
        await using (var command = new NpgsqlCommand($"""
            SELECT col.collection_id, col.name, col.description,
                   count(c.card_id) FILTER (WHERE {Owned}),
                   count(c.card_id),
                   (array_agg(c.image_key ORDER BY cc.collection_card_id)
                        FILTER (WHERE {Owned} AND c.image_key IS NOT NULL))[1:4]
            FROM public.collection col
            JOIN public.collection_card cc ON cc.collection_id = col.collection_id
            JOIN public.card c ON c.card_id = cc.card_id AND c.status <> 'INACTIVE'
            WHERE col.status <> 'INACTIVE'
            GROUP BY col.collection_id, col.name, col.description
            ORDER BY col.collection_id
            """, connection))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            await using var reader = await command.ExecuteReaderAsync(ct);
            while (await reader.ReadAsync(ct))
            {
                collections.Add(new CollectionSummary(reader.GetInt64(0), reader.GetString(1),
                    reader.IsDBNull(2) ? null : reader.GetString(2),
                    (int)reader.GetInt64(3), (int)reader.GetInt64(4),
                    reader.IsDBNull(5) ? [] : reader.GetFieldValue<string[]>(5)));
            }
        }
        return new CollectionOverview(ownedCards, totalCards, collections);
    }

    /// <summary>One collection with all its cards, owned ones first; null when it does not exist.</summary>
    public async Task<CollectionDetail?> LoadDetailAsync(long userId, long collectionId, CancellationToken ct)
    {
        await using var connection = await db.OpenConnectionAsync(ct);
        string name;
        string? description;
        await using (var command = new NpgsqlCommand("""
            SELECT name, description FROM public.collection
            WHERE collection_id = $1 AND status <> 'INACTIVE'
            """, connection))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = collectionId, NpgsqlDbType = NpgsqlDbType.Bigint });
            await using var reader = await command.ExecuteReaderAsync(ct);
            if (!await reader.ReadAsync(ct)) return null;
            name = reader.GetString(0);
            description = reader.IsDBNull(1) ? null : reader.GetString(1);
        }

        var cards = new List<CollectionCard>();
        await using (var command = new NpgsqlCommand($"""
            SELECT c.card_id, c.place_id, c.name, c.image_key, {Owned} AS owned
            FROM public.collection_card cc
            JOIN public.card c ON c.card_id = cc.card_id AND c.status <> 'INACTIVE'
            WHERE cc.collection_id = $2
            ORDER BY owned DESC, cc.collection_card_id
            """, connection))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = collectionId, NpgsqlDbType = NpgsqlDbType.Bigint });
            await using var reader = await command.ExecuteReaderAsync(ct);
            while (await reader.ReadAsync(ct))
            {
                var owned = reader.GetBoolean(4);
                cards.Add(new CollectionCard(reader.GetInt64(0), reader.GetInt64(1), reader.GetString(2),
                    owned && !reader.IsDBNull(3) ? reader.GetString(3) : null, owned));
            }
        }
        return new CollectionDetail(collectionId, name, description, cards.Count(c => c.Owned), cards.Count, cards);
    }
}

public static class CollectionEndpoints
{
    public static void MapCollectionEndpoints(this WebApplication app)
    {
        app.MapGet("/api/collections", async (HttpRequest request, ISessionStore sessions,
            CollectionRepository repository, ILoggerFactory logs, CancellationToken ct) =>
        {
            var header = request.Headers.Authorization.ToString();
            var token = header.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase) ? header[7..] : null;
            var user = sessions.Get(token);
            if (user is null) return Results.Unauthorized();
            // Per-user ownership: never cached by intermediaries.
            request.HttpContext.Response.Headers.CacheControl = "no-store";
            try { return Results.Ok(await repository.LoadAsync(user.UserId, ct)); }
            catch (Exception ex)
            {
                logs.CreateLogger("Collection").LogError(ex, "Collection request failed");
                return Results.Json(new CollectionError("SERVER_ERROR", "Máy chủ đang gặp lỗi. Vui lòng thử lại."),
                    statusCode: 500);
            }
        });
        app.MapGet("/api/collections/{collectionId:long}", async (long collectionId, HttpRequest request,
            ISessionStore sessions, CollectionRepository repository, ILoggerFactory logs, CancellationToken ct) =>
        {
            var header = request.Headers.Authorization.ToString();
            var token = header.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase) ? header[7..] : null;
            var user = sessions.Get(token);
            if (user is null) return Results.Unauthorized();
            request.HttpContext.Response.Headers.CacheControl = "no-store";
            try
            {
                var detail = await repository.LoadDetailAsync(user.UserId, collectionId, ct);
                return detail is null
                    ? Results.Json(new CollectionError("COLLECTION_NOT_FOUND", "Không tìm thấy bộ sưu tập."), statusCode: 404)
                    : Results.Ok(detail);
            }
            catch (Exception ex)
            {
                logs.CreateLogger("Collection").LogError(ex, "Collection detail request failed");
                return Results.Json(new CollectionError("SERVER_ERROR", "Máy chủ đang gặp lỗi. Vui lòng thử lại."),
                    statusCode: 500);
            }
        });
    }
}
