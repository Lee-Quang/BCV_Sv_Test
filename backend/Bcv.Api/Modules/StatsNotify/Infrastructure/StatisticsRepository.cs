using Npgsql;
using NpgsqlTypes;
using Bcv.Api.Modules.QuestItem.Domain;
using Bcv.Api.Modules.StatsNotify.Application;

namespace Bcv.Api.Modules.StatsNotify.Infrastructure;

public sealed class StatisticsRepository(NpgsqlDataSource db)
{
    public async Task<ProfileStats> StatsAsync(long userId, CancellationToken ct)
    {
        var (daily, weekly) = QuestCycles.Current();
        await using var connection = await db.OpenConnectionAsync(ct);
        await using var command = new NpgsqlCommand("""
            SELECT
              (SELECT COALESCE(SUM(step_count), 0)::bigint FROM public.step_data WHERE user_id = $1),
              (SELECT COUNT(*)::int FROM public.user_discovered_place WHERE user_id = $1),
              (SELECT COUNT(*)::int FROM public.card c WHERE c.status = 'ACTIVE' AND (
                  EXISTS (SELECT 1 FROM public.user_card uc WHERE uc.user_id = $1 AND uc.card_id = c.card_id)
                  OR EXISTS (SELECT 1 FROM public.user_discovered_place d WHERE d.user_id = $1 AND d.place_id = c.place_id))),
              (SELECT COUNT(*)::int FROM public.quest q JOIN public.quest_progress qp
                 ON qp.quest_id = q.quest_id AND qp.user_id = $1 AND qp.cycle_key = $2
               WHERE q.cycle_type = 'DAILY' AND q.status = 'ACTIVE' AND qp.completion_status),
              (SELECT COUNT(*)::int FROM public.quest WHERE cycle_type = 'DAILY' AND status = 'ACTIVE'),
              (SELECT COUNT(*)::int FROM public.quest q JOIN public.quest_progress qp
                 ON qp.quest_id = q.quest_id AND qp.user_id = $1 AND qp.cycle_key = $3
               WHERE q.cycle_type = 'WEEKLY' AND q.status = 'ACTIVE' AND qp.completion_status),
              (SELECT COUNT(*)::int FROM public.quest WHERE cycle_type = 'WEEKLY' AND status = 'ACTIVE')
            """, connection);
        command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
        command.Parameters.Add(new NpgsqlParameter<string> { TypedValue = daily, NpgsqlDbType = NpgsqlDbType.Varchar });
        command.Parameters.Add(new NpgsqlParameter<string> { TypedValue = weekly, NpgsqlDbType = NpgsqlDbType.Varchar });
        await using var reader = await command.ExecuteReaderAsync(ct);
        await reader.ReadAsync(ct);
        return new ProfileStats(reader.GetInt64(0), reader.GetInt32(1), reader.GetInt32(2),
            new QuestTally(reader.GetInt32(3), reader.GetInt32(4)), new QuestTally(reader.GetInt32(5), reader.GetInt32(6)));
    }
}
