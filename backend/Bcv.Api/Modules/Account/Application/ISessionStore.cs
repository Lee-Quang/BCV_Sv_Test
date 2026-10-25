using Bcv.Api.Modules.Account.Domain;

namespace Bcv.Api.Modules.Account.Application;

public sealed record AccountSession(
    string Token,
    DateTimeOffset ExpiresAt,
    PublicAccount User,
    bool NeedsInterestSelection = false,
    bool NeedsActivityLevel = false,
    bool NeedsVirtualLocation = false);

public interface ISessionStore
{
    AccountSession Create(AccountUser account);
    PublicAccount? Get(string? token);
    void Revoke(string? token);
    void RevokeAll(long userId);
}
