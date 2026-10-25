using Bcv.Api.Modules.Quest;
using Npgsql;
using NpgsqlTypes;

namespace Bcv.Api.Modules.Explore.Infrastructure;

/// <summary>
/// Arriving at a place, by finishing a journey or by flight ticket: the place is discovered (counting
/// toward DISCOVER_PLACE quests the first time) and its collectible card, if any, is earned.
/// </summary>
public static class PlaceUnlock
{
    public static async Task UnlockAsync(NpgsqlConnection connection, NpgsqlTransaction transaction,
        long userId, long placeId, CancellationToken ct)
    {
        await using (var discovery = new NpgsqlCommand("""
            INSERT INTO public.user_discovered_place (user_id, place_id)
            VALUES ($1, $2)
            ON CONFLICT (user_id, place_id) DO NOTHING
            """, connection, transaction))
        {
            discovery.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            discovery.Parameters.Add(new NpgsqlParameter<long> { TypedValue = placeId, NpgsqlDbType = NpgsqlDbType.Bigint });
            if (await discovery.ExecuteNonQueryAsync(ct) > 0)
                await QuestProgress.RecordAsync(connection, transaction, userId, QuestProgress.DiscoverPlace, 1, ct);
        }

        await using var card = new NpgsqlCommand("""
            INSERT INTO public.user_card (user_id, card_id)
            SELECT $1, c.card_id FROM public.card c
            WHERE c.place_id = $2 AND c.status <> 'INACTIVE'
            ON CONFLICT (user_id, card_id) DO NOTHING
            """, connection, transaction);
        card.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
        card.Parameters.Add(new NpgsqlParameter<long> { TypedValue = placeId, NpgsqlDbType = NpgsqlDbType.Bigint });
        await card.ExecuteNonQueryAsync(ct);
    }
}
