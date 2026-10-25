using System.Globalization;
using Npgsql;
using NpgsqlTypes;
using Bcv.Api.Modules.QuestItem.Domain;

namespace Bcv.Api.Modules.QuestItem.Infrastructure;

/// <summary>Reads the speed-shoe boost markers and raw journey steps kept in STEP_DATA (see <see cref="SpeedShoes"/>).</summary>
public static class SpeedShoesStore
{
    public static async Task<List<SpeedShoes.Boost>> LoadBoostsAsync(NpgsqlConnection connection, NpgsqlTransaction? transaction,
        long userId, long journeyId, CancellationToken ct)
    {
        var boosts = new List<SpeedShoes.Boost>();
        await using var command = new NpgsqlCommand("""
            SELECT source_data_id FROM public.step_data WHERE user_id = $1 AND source_data_id LIKE $2
            """, connection, transaction);
        command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
        command.Parameters.Add(new NpgsqlParameter<string> { TypedValue = $"{SpeedShoes.Prefix}{journeyId}:%", NpgsqlDbType = NpgsqlDbType.Varchar });
        await using var reader = await command.ExecuteReaderAsync(ct);
        while (await reader.ReadAsync(ct))
        {
            var parts = reader.GetString(0)[(SpeedShoes.Prefix.Length)..].Split(':');
            // {journeyId}:from:{from}:x{multiplier}:r{range}
            if (parts.Length == 5 && int.TryParse(parts[2], out var from)
                && decimal.TryParse(parts[3].TrimStart('x'), NumberStyles.Number, CultureInfo.InvariantCulture, out var multiplier)
                && int.TryParse(parts[4].TrimStart('r'), out var range))
                boosts.Add(new SpeedShoes.Boost(from, multiplier, range));
        }
        return boosts;
    }

    public static async Task<int> RawStepsAsync(NpgsqlConnection connection, NpgsqlTransaction? transaction,
        long userId, long journeyId, CancellationToken ct)
    {
        await using var command = new NpgsqlCommand("""
            SELECT step_count FROM public.step_data WHERE user_id = $1 AND source_data_id = $2
            """, connection, transaction);
        command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
        command.Parameters.Add(new NpgsqlParameter<string> { TypedValue = SpeedShoes.HealthSourceId(journeyId), NpgsqlDbType = NpgsqlDbType.Varchar });
        return await command.ExecuteScalarAsync(ct) is int steps ? steps : 0;
    }
}
