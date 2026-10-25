using Bcv.Api.Modules.Account.Application;
using Bcv.Api.Modules.Quest;
using Npgsql;
using NpgsqlTypes;

namespace Bcv.Api.Modules.Profile;

public sealed record QuestTally(int Completed, int Total);

public sealed record ProfileStats(long TotalSteps, int DiscoveredPlaces, int OwnedCards, QuestTally Daily, QuestTally Weekly);

public sealed record JourneyHistoryEntry(long JourneyId, string Status, string StartName, long DestinationPlaceId,
    string DestinationName, DateTime StartedAt, DateTime? CompletedAt);

public sealed record NotificationView(long NotificationId, string Type, string Title, string Content, bool Read,
    DateTime CreatedAt);

public sealed record NotificationList(int UnreadCount, IReadOnlyList<NotificationView> Items);

public sealed record ProfileError(string Code, string Message, string? Field = null);

/// <summary>Avatar is the image URL (Cloudflare R2), or a JPEG data URI when no cloud storage is set; null when none.</summary>
public sealed record AccountProfile(string FullName, string Email, string? Avatar);

/// <summary>Avatar is a new JPEG as a data URI; omitted (null) keeps the current one.</summary>
public sealed record UpdateProfileRequest(string? FullName, string? Avatar);

public sealed class ProfileException(string code, string message, string? field = null) : Exception(message)
{
    public string Code { get; } = code;
    public string? Field { get; } = field;
}

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

public sealed class ProfileRepository(NpgsqlDataSource db, IAvatarStorage avatars)
{
    public async Task<ProfileStats> StatsAsync(long userId, CancellationToken ct)
    {
        var (daily, weekly) = QuestCycles.Current();
        await using var connection = await db.OpenConnectionAsync(ct);
        await using var command = new NpgsqlCommand("""
            SELECT
              (SELECT COALESCE(SUM(step_count), 0)::bigint FROM public.step_data WHERE user_id = $1),
              (SELECT COUNT(*)::int FROM public.user_discovered_place WHERE user_id = $1),
              (SELECT COUNT(*)::int FROM public.card c WHERE c.status <> 'INACTIVE' AND (
                  EXISTS (SELECT 1 FROM public.user_card uc WHERE uc.user_id = $1 AND uc.card_id = c.card_id)
                  OR EXISTS (SELECT 1 FROM public.user_discovered_place d WHERE d.user_id = $1 AND d.place_id = c.place_id))),
              (SELECT COUNT(*)::int FROM public.quest q JOIN public.quest_progress qp
                 ON qp.quest_id = q.quest_id AND qp.user_id = $1 AND qp.cycle_key = $2
               WHERE q.cycle_type = 'DAILY' AND q.status <> 'INACTIVE' AND qp.completion_status),
              (SELECT COUNT(*)::int FROM public.quest WHERE cycle_type = 'DAILY' AND status <> 'INACTIVE'),
              (SELECT COUNT(*)::int FROM public.quest q JOIN public.quest_progress qp
                 ON qp.quest_id = q.quest_id AND qp.user_id = $1 AND qp.cycle_key = $3
               WHERE q.cycle_type = 'WEEKLY' AND q.status <> 'INACTIVE' AND qp.completion_status),
              (SELECT COUNT(*)::int FROM public.quest WHERE cycle_type = 'WEEKLY' AND status <> 'INACTIVE')
            """, connection);
        command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
        command.Parameters.Add(new NpgsqlParameter<string> { TypedValue = daily, NpgsqlDbType = NpgsqlDbType.Varchar });
        command.Parameters.Add(new NpgsqlParameter<string> { TypedValue = weekly, NpgsqlDbType = NpgsqlDbType.Varchar });
        await using var reader = await command.ExecuteReaderAsync(ct);
        await reader.ReadAsync(ct);
        return new ProfileStats(reader.GetInt64(0), reader.GetInt32(1), reader.GetInt32(2),
            new QuestTally(reader.GetInt32(3), reader.GetInt32(4)), new QuestTally(reader.GetInt32(5), reader.GetInt32(6)));
    }

    public async Task<IReadOnlyList<JourneyHistoryEntry>> HistoryAsync(long userId, CancellationToken ct)
    {
        await using var connection = await db.OpenConnectionAsync(ct);
        await using var command = new NpgsqlCommand("""
            SELECT j.journey_id, j.status, COALESCE(s.name, 'Vị trí ảo'), d.place_id, d.name, j.started_at, j.completed_at
            FROM public.journey j
            JOIN public.place d ON d.place_id = j.destination_place_id
            LEFT JOIN public.place s ON s.place_id = j.start_place_id
            WHERE j.user_id = $1
            ORDER BY j.started_at DESC, j.journey_id DESC
            """, connection);
        command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
        var entries = new List<JourneyHistoryEntry>();
        await using var reader = await command.ExecuteReaderAsync(ct);
        while (await reader.ReadAsync(ct))
            entries.Add(new JourneyHistoryEntry(reader.GetInt64(0), reader.GetString(1), reader.GetString(2),
                reader.GetInt64(3), reader.GetString(4), reader.GetDateTime(5), reader.IsDBNull(6) ? null : reader.GetDateTime(6)));
        return entries;
    }

    public async Task<NotificationList> NotificationsAsync(long userId, CancellationToken ct)
    {
        await using var connection = await db.OpenConnectionAsync(ct);
        await using var command = new NpgsqlCommand("""
            SELECT notification_id, notification_type, title, content, read_status, created_at
            FROM public.notification WHERE user_id = $1
            ORDER BY created_at DESC, notification_id DESC
            LIMIT 100
            """, connection);
        command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
        var items = new List<NotificationView>();
        await using var reader = await command.ExecuteReaderAsync(ct);
        while (await reader.ReadAsync(ct))
            items.Add(new NotificationView(reader.GetInt64(0), reader.GetString(1), reader.GetString(2),
                reader.GetString(3), reader.GetBoolean(4), reader.GetDateTime(5)));
        return new NotificationList(items.Count(n => !n.Read), items);
    }

    public async Task<NotificationList> MarkReadAsync(long userId, long notificationId, CancellationToken ct)
    {
        await using (var connection = await db.OpenConnectionAsync(ct))
        await using (var command = new NpgsqlCommand("""
            UPDATE public.notification SET read_status = TRUE WHERE user_id = $1 AND notification_id = $2
            """, connection))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = notificationId, NpgsqlDbType = NpgsqlDbType.Bigint });
            await command.ExecuteNonQueryAsync(ct);
        }
        return await NotificationsAsync(userId, ct);
    }

    public async Task<AccountProfile> ProfileAsync(long userId, CancellationToken ct)
    {
        await using var connection = await db.OpenConnectionAsync(ct);
        await using var command = new NpgsqlCommand("""
            SELECT full_name, email, avatar FROM public.app_user WHERE user_id = $1
            """, connection);
        command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
        await using var reader = await command.ExecuteReaderAsync(ct);
        if (!await reader.ReadAsync(ct)) throw new ProfileException("NOT_FOUND", "Không tìm thấy tài khoản.");
        return new AccountProfile(reader.GetString(0), reader.GetString(1), reader.IsDBNull(2) ? null : reader.GetString(2));
    }

    public async Task<AccountProfile> UpdateProfileAsync(long userId, UpdateProfileRequest request, CancellationToken ct)
    {
        var name = string.Join(' ', (request.FullName ?? "").Split(' ', StringSplitOptions.RemoveEmptyEntries));
        if (name.Length is < 2 or > 100)
            throw new ProfileException("INVALID_NAME", "Họ và tên cần từ 2 đến 100 ký tự.", "fullName");
        byte[]? jpeg = null;
        if (request.Avatar is { } avatar)
        {
            const string prefix = "data:image/jpeg;base64,";
            try { jpeg = avatar.StartsWith(prefix, StringComparison.Ordinal) ? Convert.FromBase64String(avatar[prefix.Length..]) : null; }
            catch (FormatException) { jpeg = null; }
            if (jpeg is not { Length: > 2 and <= 300_000 } || jpeg[0] != 0xFF || jpeg[1] != 0xD8)
                throw new ProfileException("INVALID_AVATAR", "Ảnh đại diện không hợp lệ hoặc quá lớn.", "avatar");
        }

        var previous = (await ProfileAsync(userId, ct)).Avatar;
        var stored = jpeg is null ? null : await avatars.SaveAsync(userId, jpeg, ct);
        await using (var connection = await db.OpenConnectionAsync(ct))
        await using (var command = new NpgsqlCommand("""
            UPDATE public.app_user SET full_name = $2, avatar = COALESCE($3, avatar) WHERE user_id = $1
            """, connection))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            command.Parameters.Add(new NpgsqlParameter<string> { TypedValue = name, NpgsqlDbType = NpgsqlDbType.Varchar });
            command.Parameters.Add(new NpgsqlParameter { Value = (object?)stored ?? DBNull.Value, NpgsqlDbType = NpgsqlDbType.Text });
            await command.ExecuteNonQueryAsync(ct);
        }
        if (stored is not null) await avatars.DeleteAsync(previous, ct);
        return await ProfileAsync(userId, ct);
    }

    /// <summary>Closes the account (status DELETED); login already refuses accounts that are not ACTIVE.</summary>
    public async Task DeleteAccountAsync(long userId, CancellationToken ct)
    {
        await using var connection = await db.OpenConnectionAsync(ct);
        await using var command = new NpgsqlCommand("""
            UPDATE public.app_user SET status = 'DELETED' WHERE user_id = $1 AND status = 'ACTIVE'
            """, connection);
        command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
        await command.ExecuteNonQueryAsync(ct);
    }
}

public static class ProfileEndpoints
{
    public static void MapProfileEndpoints(this WebApplication app)
    {
        app.MapGet("/api/profile/stats", (HttpRequest request, ISessionStore sessions, ProfileRepository profile,
            ILoggerFactory logs, CancellationToken ct) => Run(request, sessions, logs, user => profile.StatsAsync(user, ct)));
        app.MapGet("/api/journeys/history", (HttpRequest request, ISessionStore sessions, ProfileRepository profile,
            ILoggerFactory logs, CancellationToken ct) =>
            Run(request, sessions, logs, async user => new { journeys = await profile.HistoryAsync(user, ct) }));
        app.MapGet("/api/notifications", (HttpRequest request, ISessionStore sessions, ProfileRepository profile,
            ILoggerFactory logs, CancellationToken ct) => Run(request, sessions, logs, user => profile.NotificationsAsync(user, ct)));
        app.MapPost("/api/notifications/{notificationId:long}/read", (long notificationId, HttpRequest request,
            ISessionStore sessions, ProfileRepository profile, ILoggerFactory logs, CancellationToken ct) =>
            Run(request, sessions, logs, user => profile.MarkReadAsync(user, notificationId, ct)));
        app.MapGet("/api/account/profile", (HttpRequest request, ISessionStore sessions, ProfileRepository profile,
            ILoggerFactory logs, CancellationToken ct) => Run(request, sessions, logs, user => profile.ProfileAsync(user, ct)));
        app.MapPut("/api/account/profile", (UpdateProfileRequest body, HttpRequest request, ISessionStore sessions,
            ProfileRepository profile, ILoggerFactory logs, CancellationToken ct) =>
            Run(request, sessions, logs, user => profile.UpdateProfileAsync(user, body, ct)));
        app.MapPost("/api/account/delete", (HttpRequest request, ISessionStore sessions, ProfileRepository profile,
            ILoggerFactory logs, CancellationToken ct) => Run(request, sessions, logs, async user =>
        {
            await profile.DeleteAccountAsync(user, ct);
            sessions.RevokeAll(user);
            return new { deleted = true };
        }));
    }

    private static async Task<IResult> Run<T>(HttpRequest request, ISessionStore sessions, ILoggerFactory logs,
        Func<long, Task<T>> action)
    {
        var header = request.Headers.Authorization.ToString();
        var user = sessions.Get(header.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase) ? header[7..] : null);
        if (user is null) return Results.Unauthorized();
        request.HttpContext.Response.Headers.CacheControl = "no-store";
        try { return Results.Ok(await action(user.UserId)); }
        catch (ProfileException ex)
        {
            return Results.Json(new ProfileError(ex.Code, ex.Message, ex.Field),
                statusCode: ex.Code == "NOT_FOUND" ? 404 : 400);
        }
        catch (Exception ex)
        {
            logs.CreateLogger("Profile").LogError(ex, "Profile request failed");
            return Results.Json(new ProfileError("SERVER_ERROR", "Máy chủ đang gặp lỗi. Vui lòng thử lại."),
                statusCode: 500);
        }
    }
}
