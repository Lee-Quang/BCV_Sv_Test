using System.Collections.Concurrent;
using System.Security.Cryptography;
using Bcv.Api.Modules.Account.Application;
using Bcv.Api.Modules.Account.Domain;

namespace Bcv.Api.Modules.Account.Infrastructure;

// Sessions are scoped to this Backend process; restarting it signs everyone out.
public sealed class InMemorySessionStore : ISessionStore
{
    private readonly ConcurrentDictionary<string, (PublicAccount User, DateTimeOffset ExpiresAt)> sessions = new();

    public AccountSession Create(AccountUser account)
    {
        var user = account.ToPublic();
        var token = Convert.ToBase64String(RandomNumberGenerator.GetBytes(32))
            .TrimEnd('=').Replace('+', '-').Replace('/', '_');
        var expiresAt = DateTimeOffset.UtcNow.AddHours(12);
        sessions[token] = (user, expiresAt);
        return new AccountSession(token, expiresAt, user);
    }

    public PublicAccount? Get(string? token)
    {
        if (token is null || !sessions.TryGetValue(token, out var session)) return null;
        if (session.ExpiresAt > DateTimeOffset.UtcNow) return session.User;
        sessions.TryRemove(token, out _);
        return null;
    }

    public void Revoke(string? token)
    {
        if (token is not null) sessions.TryRemove(token, out _);
    }

    public void RevokeAll(long userId)
    {
        foreach (var (token, session) in sessions)
            if (session.User.UserId == userId) sessions.TryRemove(token, out _);
    }
}
