using System.Globalization;
using Bcv.Api.Modules.Account.Application;
using Npgsql;
using NpgsqlTypes;

namespace Bcv.Api.Modules.Quest;

public sealed record QuestView(long QuestId, string Name, string CycleType, string ConditionType,
    int TargetValue, int ProgressValue, bool Completed, bool RewardReceived,
    string? RewardName, string? RewardType, int? RewardQuantity);

public sealed record QuestOverview(string DailyCycle, string WeeklyCycle, DateTimeOffset UpdatedAt,
    IReadOnlyList<QuestView> Daily, IReadOnlyList<QuestView> Weekly);

public sealed record QuestError(string Code, string Message);

/// <summary>Daily ("2026-10-25") and ISO-weekly ("2026-W43") cycle keys in Vietnam time, as seeded in QUEST_PROGRESS.</summary>
public static class QuestCycles
{
    private static readonly TimeZoneInfo Vietnam = FindVietnam();

    public static (string Daily, string Weekly) Current()
    {
        var local = TimeZoneInfo.ConvertTime(DateTimeOffset.UtcNow, Vietnam).Date;
        return (local.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture),
            $"{ISOWeek.GetYear(local)}-W{ISOWeek.GetWeekOfYear(local):00}");
    }

    private static TimeZoneInfo FindVietnam()
    {
        foreach (var id in new[] { "Asia/Ho_Chi_Minh", "SE Asia Standard Time" })
        {
            try { return TimeZoneInfo.FindSystemTimeZoneById(id); }
            catch (TimeZoneNotFoundException) { }
        }
        return TimeZoneInfo.CreateCustomTimeZone("ICT", TimeSpan.FromHours(7), "ICT", "ICT");
    }
}

/// <summary>
/// Adds progress to every active quest of a condition for the current cycle. Called inside the
/// transaction that records the event (step sync, journey completion), so progress and event agree.
/// </summary>
public static class QuestProgress
{
    public const string StepCount = "STEP_COUNT";
    public const string DiscoverPlace = "DISCOVER_PLACE";
    public const string CompleteJourney = "COMPLETE_JOURNEY";

    public static async Task RecordAsync(NpgsqlConnection connection, NpgsqlTransaction transaction,
        long userId, string conditionType, int amount, CancellationToken ct)
    {
        if (amount <= 0) return;
        var (daily, weekly) = QuestCycles.Current();
        await using var command = new NpgsqlCommand("""
            INSERT INTO public.quest_progress
                (user_id, quest_id, cycle_key, progress_value, completion_status, completed_at)
            SELECT $1, q.quest_id,
                   CASE q.cycle_type WHEN 'DAILY' THEN $3 WHEN 'WEEKLY' THEN $4 ELSE 'ONCE' END,
                   $2, $2 >= q.target_value,
                   CASE WHEN $2 >= q.target_value THEN CURRENT_TIMESTAMP END
            FROM public.quest q
            WHERE q.condition_type = $5 AND q.status <> 'INACTIVE'
            ON CONFLICT (user_id, quest_id, cycle_key) DO UPDATE
            SET progress_value = quest_progress.progress_value + EXCLUDED.progress_value,
                completion_status = quest_progress.completion_status
                    OR quest_progress.progress_value + EXCLUDED.progress_value >=
                       (SELECT target_value FROM public.quest WHERE quest_id = quest_progress.quest_id),
                completed_at = COALESCE(quest_progress.completed_at,
                    CASE WHEN quest_progress.progress_value + EXCLUDED.progress_value >=
                              (SELECT target_value FROM public.quest WHERE quest_id = quest_progress.quest_id)
                         THEN CURRENT_TIMESTAMP END)
            """, connection, transaction);
        command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
        command.Parameters.Add(new NpgsqlParameter<int> { TypedValue = amount, NpgsqlDbType = NpgsqlDbType.Integer });
        command.Parameters.Add(new NpgsqlParameter<string> { TypedValue = daily, NpgsqlDbType = NpgsqlDbType.Varchar });
        command.Parameters.Add(new NpgsqlParameter<string> { TypedValue = weekly, NpgsqlDbType = NpgsqlDbType.Varchar });
        command.Parameters.Add(new NpgsqlParameter<string> { TypedValue = conditionType, NpgsqlDbType = NpgsqlDbType.Varchar });
        await command.ExecuteNonQueryAsync(ct);

        // completed_at = CURRENT_TIMESTAMP only for quests this transaction completed (it is the
        // transaction start time); NOT EXISTS keeps a later call in the same transaction from repeating it.
        await using var notify = new NpgsqlCommand("""
            INSERT INTO public.notification (user_id, notification_type, title, content, created_at)
            SELECT $1, 'QUEST_COMPLETED', 'Nhiệm vụ hoàn thành', q.name || ' đã đạt mục tiêu.', CURRENT_TIMESTAMP
            FROM public.quest_progress qp JOIN public.quest q ON q.quest_id = qp.quest_id
            WHERE qp.user_id = $1 AND q.condition_type = $2 AND qp.completed_at = CURRENT_TIMESTAMP
              AND NOT EXISTS (SELECT 1 FROM public.notification n
                              WHERE n.user_id = $1 AND n.notification_type = 'QUEST_COMPLETED'
                                AND n.created_at = CURRENT_TIMESTAMP AND n.content = q.name || ' đã đạt mục tiêu.')
            """, connection, transaction);
        notify.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
        notify.Parameters.Add(new NpgsqlParameter<string> { TypedValue = conditionType, NpgsqlDbType = NpgsqlDbType.Varchar });
        await notify.ExecuteNonQueryAsync(ct);
    }
}

public sealed class QuestRepository(NpgsqlDataSource db)
{
    /// <summary>
    /// Current daily and weekly quests with progress. Completed quests whose reward is still unpaid
    /// are paid here (item added to USER_ITEM) in the same transaction, so a reward is granted once.
    /// </summary>
    public async Task<QuestOverview> LoadAsync(long userId, CancellationToken ct)
    {
        var (daily, weekly) = QuestCycles.Current();
        await using var connection = await db.OpenConnectionAsync(ct);
        await using var transaction = await connection.BeginTransactionAsync(ct);

        await using (var grant = new NpgsqlCommand("""
            WITH due AS (
                UPDATE public.quest_progress qp SET reward_status = TRUE
                FROM public.quest q
                WHERE qp.quest_id = q.quest_id AND qp.user_id = $1
                  AND qp.cycle_key IN ($2, $3, 'ONCE')
                  AND qp.completion_status AND NOT qp.reward_status
                  AND q.reward_item_id IS NOT NULL
                RETURNING q.reward_item_id AS item_id, q.reward_quantity AS quantity
            ), totals AS (
                SELECT item_id, SUM(quantity)::int AS quantity FROM due GROUP BY item_id
            ), granted AS (
                INSERT INTO public.user_item (user_id, item_id, quantity)
                SELECT $1, item_id, quantity FROM totals
                ON CONFLICT (user_id, item_id) DO UPDATE
                SET quantity = user_item.quantity + EXCLUDED.quantity, updated_at = CURRENT_TIMESTAMP
            )
            INSERT INTO public.notification (user_id, notification_type, title, content)
            SELECT $1, 'ITEM_RECEIVED', 'Đã nhận vật phẩm',
                   t.quantity || ' ' || lower(i.name) || ' đã được thêm vào kho.'
            FROM totals t JOIN public.item i ON i.item_id = t.item_id
            """, connection, transaction))
        {
            grant.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            grant.Parameters.Add(new NpgsqlParameter<string> { TypedValue = daily, NpgsqlDbType = NpgsqlDbType.Varchar });
            grant.Parameters.Add(new NpgsqlParameter<string> { TypedValue = weekly, NpgsqlDbType = NpgsqlDbType.Varchar });
            await grant.ExecuteNonQueryAsync(ct);
        }

        var dailyQuests = new List<QuestView>();
        var weeklyQuests = new List<QuestView>();
        await using (var command = new NpgsqlCommand("""
            SELECT q.quest_id, q.name, q.cycle_type, q.condition_type, q.target_value,
                   COALESCE(qp.progress_value, 0), COALESCE(qp.completion_status, FALSE),
                   COALESCE(qp.reward_status, FALSE), i.name, i.item_type, q.reward_quantity
            FROM public.quest q
            LEFT JOIN public.quest_progress qp
              ON qp.quest_id = q.quest_id AND qp.user_id = $1
             AND qp.cycle_key = CASE q.cycle_type WHEN 'DAILY' THEN $2 WHEN 'WEEKLY' THEN $3 ELSE 'ONCE' END
            LEFT JOIN public.item i ON i.item_id = q.reward_item_id
            WHERE q.status <> 'INACTIVE' AND q.cycle_type IN ('DAILY', 'WEEKLY')
            ORDER BY q.target_value, q.quest_id
            """, connection, transaction))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            command.Parameters.Add(new NpgsqlParameter<string> { TypedValue = daily, NpgsqlDbType = NpgsqlDbType.Varchar });
            command.Parameters.Add(new NpgsqlParameter<string> { TypedValue = weekly, NpgsqlDbType = NpgsqlDbType.Varchar });
            await using var reader = await command.ExecuteReaderAsync(ct);
            while (await reader.ReadAsync(ct))
            {
                var quest = new QuestView(reader.GetInt64(0), reader.GetString(1), reader.GetString(2),
                    reader.GetString(3), reader.GetInt32(4), reader.GetInt32(5), reader.GetBoolean(6),
                    reader.GetBoolean(7), reader.IsDBNull(8) ? null : reader.GetString(8),
                    reader.IsDBNull(9) ? null : reader.GetString(9), reader.IsDBNull(10) ? null : reader.GetInt32(10));
                (quest.CycleType == "DAILY" ? dailyQuests : weeklyQuests).Add(quest);
            }
        }
        await transaction.CommitAsync(ct);
        return new QuestOverview(daily, weekly, DateTimeOffset.UtcNow, dailyQuests, weeklyQuests);
    }
}

public static class QuestEndpoints
{
    public static void MapQuestEndpoints(this WebApplication app)
    {
        app.MapGet("/api/quests", async (HttpRequest request, ISessionStore sessions,
            QuestRepository repository, ILoggerFactory logs, CancellationToken ct) =>
        {
            var header = request.Headers.Authorization.ToString();
            var token = header.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase) ? header[7..] : null;
            var user = sessions.Get(token);
            if (user is null) return Results.Unauthorized();
            request.HttpContext.Response.Headers.CacheControl = "no-store";
            try { return Results.Ok(await repository.LoadAsync(user.UserId, ct)); }
            catch (Exception ex)
            {
                logs.CreateLogger("Quest").LogError(ex, "Quest request failed");
                return Results.Json(new QuestError("SERVER_ERROR", "Máy chủ đang gặp lỗi. Vui lòng thử lại."),
                    statusCode: 500);
            }
        });
    }
}
