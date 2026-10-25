using System.Collections.Concurrent;
using System.Security.Cryptography;
using System.Text;
using Bcv.Api.Modules.Account.Application;
using Bcv.Api.Modules.Account.Domain;

namespace Bcv.Api.Modules.Account.Infrastructure;

// Like sessions, pending codes live in this Backend process; a restart invalidates them.
public sealed class InMemoryPasswordResetStore : IPasswordResetStore
{
    private const int MaxAttempts = 5;
    private static readonly TimeSpan ResetTokenLifetime = TimeSpan.FromMinutes(15);

    private sealed record PendingCode(long UserId, byte[] CodeHash, DateTimeOffset SentAt,
        DateTimeOffset ExpiresAt, int Attempts);

    private readonly ConcurrentDictionary<string, PendingCode> codes = new();
    private readonly ConcurrentDictionary<string, (long UserId, DateTimeOffset ExpiresAt)> tokens = new();

    public int SecondsUntilResend(string email)
    {
        if (!codes.TryGetValue(email, out var pending)) return 0;
        var ready = pending.SentAt.AddSeconds(PasswordResetService.ResendSeconds);
        return (int)Math.Ceiling(Math.Max(0, (ready - DateTimeOffset.UtcNow).TotalSeconds));
    }

    public string IssueCode(string email, long userId)
    {
        var code = RandomNumberGenerator.GetInt32(0, 1_000_000).ToString("D6");
        var now = DateTimeOffset.UtcNow;
        codes[email] = new PendingCode(userId, Hash(code), now,
            now.AddSeconds(PasswordResetService.CodeLifetimeSeconds), 0);
        return code;
    }

    public long Verify(string email, string code)
    {
        if (!codes.TryGetValue(email, out var pending))
            throw new AccountException("CODE_EXPIRED", "Mã xác minh đã hết hạn. Vui lòng gửi lại mã.", "code");
        if (pending.ExpiresAt <= DateTimeOffset.UtcNow)
        {
            codes.TryRemove(email, out _);
            throw new AccountException("CODE_EXPIRED", "Mã xác minh đã hết hạn. Vui lòng gửi lại mã.", "code");
        }
        if (pending.Attempts >= MaxAttempts)
            throw new AccountException("TOO_MANY_ATTEMPTS", "Bạn đã nhập sai quá nhiều lần. Vui lòng gửi lại mã mới.", "code");

        // User id 0 marks an email with no account: it never matches, but fails exactly like a wrong code.
        if (pending.UserId != 0 && CryptographicOperations.FixedTimeEquals(pending.CodeHash, Hash(code)))
        {
            codes.TryRemove(email, out _);
            return pending.UserId;
        }
        var attempts = pending.Attempts + 1;
        codes[email] = pending with { Attempts = attempts };
        var left = MaxAttempts - attempts;
        throw left > 0
            ? new AccountException("INVALID_CODE", $"Mã xác minh không đúng. Bạn còn {left} lần thử.", "code")
            : new AccountException("TOO_MANY_ATTEMPTS", "Bạn đã nhập sai quá nhiều lần. Vui lòng gửi lại mã mới.", "code");
    }

    public string IssueResetToken(long userId)
    {
        var token = Convert.ToBase64String(RandomNumberGenerator.GetBytes(32))
            .TrimEnd('=').Replace('+', '-').Replace('/', '_');
        tokens[token] = (userId, DateTimeOffset.UtcNow.Add(ResetTokenLifetime));
        return token;
    }

    public long? ConsumeResetToken(string token)
    {
        if (!tokens.TryRemove(token, out var entry)) return null;
        return entry.ExpiresAt > DateTimeOffset.UtcNow ? entry.UserId : null;
    }

    private static byte[] Hash(string code) => SHA256.HashData(Encoding.UTF8.GetBytes(code));
}
