namespace Bcv.Api.Modules.Account.Domain;

public sealed record AccountUser(
    long UserId,
    string FullName,
    string Email,
    string Role,
    string Status,
    string PasswordHash)
{
    public PublicAccount ToPublic() => new(UserId, FullName, Email, Role);
}

public sealed record PublicAccount(long UserId, string FullName, string Email, string Role);

public sealed class AccountException(string code, string message, string? field = null) : Exception(message)
{
    public string Code { get; } = code;
    public string? Field { get; } = field;
}
