using Npgsql;
using NpgsqlTypes;

namespace Bcv.Api.Modules.StatsNotify.Infrastructure;

/// <summary>Event notices written in the same transaction as the event, so they only reflect recorded facts.</summary>
public static class Notifications
{
    public const string JourneyCompleted = "JOURNEY_COMPLETED";
    public const string QuestCompleted = "QUEST_COMPLETED";
    public const string ItemReceived = "ITEM_RECEIVED";

    public static async Task AddAsync(NpgsqlConnection connection, NpgsqlTransaction transaction, long userId,
        string type, string title, string content, CancellationToken ct)
    {
        await using var command = new NpgsqlCommand("""
            INSERT INTO public.notification (user_id, notification_type, title, content)
            VALUES ($1, $2, $3, $4)
            """, connection, transaction);
        command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
        command.Parameters.Add(new NpgsqlParameter<string> { TypedValue = type, NpgsqlDbType = NpgsqlDbType.Varchar });
        command.Parameters.Add(new NpgsqlParameter<string> { TypedValue = title, NpgsqlDbType = NpgsqlDbType.Varchar });
        command.Parameters.Add(new NpgsqlParameter<string> { TypedValue = content, NpgsqlDbType = NpgsqlDbType.Text });
        await command.ExecuteNonQueryAsync(ct);
    }
}
