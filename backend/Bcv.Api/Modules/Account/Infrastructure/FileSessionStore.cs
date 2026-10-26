using System.Collections.Concurrent;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Bcv.Api.Modules.Account.Application;
using Bcv.Api.Modules.Account.Domain;

namespace Bcv.Api.Modules.Account.Infrastructure;

/// <summary>
/// Sessions that survive a Backend restart: kept in memory and mirrored to a JSON file outside the
/// repository. Only a SHA-256 of each token is stored, so the file cannot be used to sign in.
/// A session lasts <see cref="Lifetime"/> and is extended while it is used, so an active user
/// stays signed in; logout, password changes and account locks still revoke it at once.
/// </summary>
public sealed class FileSessionStore : ISessionStore
{
    public static readonly TimeSpan Lifetime = TimeSpan.FromDays(30);
    /// <summary>Using a session extends it at most this often, to keep file writes rare.</summary>
    private static readonly TimeSpan RenewAfter = TimeSpan.FromDays(1);

    private readonly ConcurrentDictionary<string, StoredSession> sessions = new();
    private readonly string path;
    private readonly ILogger<FileSessionStore> logger;
    private readonly object writeLock = new();

    private sealed record StoredSession(PublicAccount User, DateTimeOffset ExpiresAt);

    public FileSessionStore(string path, ILogger<FileSessionStore> logger)
    {
        this.path = path;
        this.logger = logger;
        Load();
    }

    public AccountSession Create(AccountUser account)
    {
        var user = account.ToPublic();
        var token = Convert.ToBase64String(RandomNumberGenerator.GetBytes(32))
            .TrimEnd('=').Replace('+', '-').Replace('/', '_');
        var expiresAt = DateTimeOffset.UtcNow.Add(Lifetime);
        sessions[Hash(token)] = new StoredSession(user, expiresAt);
        Save();
        return new AccountSession(token, expiresAt, user);
    }

    public PublicAccount? Get(string? token)
    {
        if (token is null) return null;
        var key = Hash(token);
        if (!sessions.TryGetValue(key, out var session)) return null;
        var now = DateTimeOffset.UtcNow;
        if (session.ExpiresAt <= now)
        {
            if (sessions.TryRemove(key, out _)) Save();
            return null;
        }
        if (session.ExpiresAt - now < Lifetime - RenewAfter)
        {
            sessions[key] = session with { ExpiresAt = now.Add(Lifetime) };
            Save();
        }
        return session.User;
    }

    public void Revoke(string? token)
    {
        if (token is not null && sessions.TryRemove(Hash(token), out _)) Save();
    }

    public void RevokeAll(long userId)
    {
        var removed = false;
        foreach (var (key, session) in sessions)
            if (session.User.UserId == userId) removed |= sessions.TryRemove(key, out _);
        if (removed) Save();
    }

    private static string Hash(string token) =>
        Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(token)));

    private void Load()
    {
        try
        {
            if (!File.Exists(path)) return;
            var stored = JsonSerializer.Deserialize<Dictionary<string, StoredSession>>(File.ReadAllText(path));
            var now = DateTimeOffset.UtcNow;
            foreach (var (key, session) in stored ?? [])
                if (session.ExpiresAt > now) sessions[key] = session;
            logger.LogInformation("Restored {Count} sign-in sessions from {Path}", sessions.Count, path);
        }
        catch (Exception ex)
        {
            // A damaged file only signs people out; it must not stop the API from starting.
            logger.LogWarning(ex, "Could not read saved sessions from {Path}; starting with none", path);
        }
    }

    private void Save()
    {
        lock (writeLock)
        {
            try
            {
                Directory.CreateDirectory(Path.GetDirectoryName(path)!);
                var temp = path + ".tmp";
                File.WriteAllText(temp, JsonSerializer.Serialize(sessions.ToDictionary(p => p.Key, p => p.Value)));
                File.Move(temp, path, overwrite: true);
            }
            catch (Exception ex)
            {
                logger.LogWarning(ex, "Could not save sessions to {Path}; they last until the next restart", path);
            }
        }
    }
}
