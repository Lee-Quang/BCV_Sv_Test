using Npgsql;
using NpgsqlTypes;
using Bcv.Api.Modules.Account.Application;

namespace Bcv.Api.Modules.Account.Infrastructure;

public sealed class PostgresProfileRepository(NpgsqlDataSource db) : IProfileRepository
{
    public async Task<AccountProfile?> FindAsync(long userId, CancellationToken ct)
    {
        await using var connection = await db.OpenConnectionAsync(ct);
        await using var command = new NpgsqlCommand("""
            SELECT full_name, email, avatar FROM public.app_user WHERE user_id = $1
            """, connection);
        command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
        await using var reader = await command.ExecuteReaderAsync(ct);
        if (!await reader.ReadAsync(ct)) return null;
        return new AccountProfile(reader.GetString(0), reader.GetString(1), reader.IsDBNull(2) ? null : reader.GetString(2));
    }

    /// <summary>A null avatar keeps the current one.</summary>
    public async Task UpdateAsync(long userId, string fullName, string? avatar, CancellationToken ct)
    {
        await using var connection = await db.OpenConnectionAsync(ct);
        await using var command = new NpgsqlCommand("""
            UPDATE public.app_user SET full_name = $2, avatar = COALESCE($3, avatar) WHERE user_id = $1
            """, connection);
        command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
        command.Parameters.Add(new NpgsqlParameter<string> { TypedValue = fullName, NpgsqlDbType = NpgsqlDbType.Varchar });
        command.Parameters.Add(new NpgsqlParameter { Value = (object?)avatar ?? DBNull.Value, NpgsqlDbType = NpgsqlDbType.Text });
        await command.ExecuteNonQueryAsync(ct);
    }

    public async Task CloseAccountAsync(long userId, CancellationToken ct)
    {
        await using var connection = await db.OpenConnectionAsync(ct);
        await using var command = new NpgsqlCommand("""
            UPDATE public.app_user SET status = 'DELETED' WHERE user_id = $1 AND status = 'ACTIVE'
            """, connection);
        command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
        await command.ExecuteNonQueryAsync(ct);
    }
}
