using Bcv.Api.Modules.Account.Domain;

namespace Bcv.Api.Modules.Account.Application;

public sealed record PasswordResetRequested(int ResendAfterSeconds, int ExpiresInSeconds);
public sealed record PasswordResetVerified(string ResetToken);
public sealed record PasswordResetCompleted(bool Success);

public interface IPasswordResetStore
{
    /// <summary>Seconds until another code may be sent, or 0 when sending is allowed now.</summary>
    int SecondsUntilResend(string email);
    string IssueCode(string email, long userId);
    /// <summary>Returns the user id when the code matches; throws AccountException otherwise.</summary>
    long Verify(string email, string code);
    string IssueResetToken(long userId);
    long? ConsumeResetToken(string token);
}

public interface IPasswordResetMailer
{
    Task SendCodeAsync(string email, string code, CancellationToken cancellationToken);
}

public sealed class PasswordResetService(
    IAccountRepository repository,
    IPasswordResetStore store,
    IPasswordResetMailer mailer,
    ISessionStore sessions)
{
    public const int ResendSeconds = 60;
    public const int CodeLifetimeSeconds = 600;

    public async Task<PasswordResetRequested> Request(string? rawEmail, CancellationToken cancellationToken)
    {
        var email = AccountService.NormalizeEmail(rawEmail);
        var wait = store.SecondsUntilResend(email);
        if (wait > 0)
            throw new AccountException("RESEND_TOO_SOON", $"Vui lòng chờ {wait} giây trước khi gửi lại mã.");

        // Unknown or inactive emails get the same answer so the endpoint cannot be used to probe accounts.
        var account = await repository.FindByEmailAsync(email, cancellationToken);
        if (account is not null && account.Status == "ACTIVE")
            await mailer.SendCodeAsync(email, store.IssueCode(email, account.UserId), cancellationToken);
        else
            store.IssueCode(email, 0);
        return new PasswordResetRequested(ResendSeconds, CodeLifetimeSeconds);
    }

    public Task<PasswordResetVerified> Verify(string? rawEmail, string? rawCode, CancellationToken cancellationToken)
    {
        var email = AccountService.NormalizeEmail(rawEmail);
        var code = rawCode?.Trim() ?? "";
        if (code.Length != 6 || !code.All(char.IsAsciiDigit))
            throw new AccountException("INVALID_CODE_FORMAT", "Mã xác minh gồm 6 chữ số.", "code");
        var userId = store.Verify(email, code);
        return Task.FromResult(new PasswordResetVerified(store.IssueResetToken(userId)));
    }

    public async Task<PasswordResetCompleted> Complete(string? token, string? password, string? confirmPassword,
        CancellationToken cancellationToken)
    {
        var value = password ?? "";
        AccountService.ValidateNewPassword(value, confirmPassword);
        var userId = string.IsNullOrEmpty(token) ? null : store.ConsumeResetToken(token);
        if (userId is null)
            throw new AccountException("RESET_EXPIRED", "Phiên khôi phục đã hết hạn. Vui lòng yêu cầu mã mới.");
        await repository.UpdatePasswordAsync(userId.Value, PasswordHasher.Hash(value), cancellationToken);
        sessions.RevokeAll(userId.Value);
        return new PasswordResetCompleted(true);
    }
}
