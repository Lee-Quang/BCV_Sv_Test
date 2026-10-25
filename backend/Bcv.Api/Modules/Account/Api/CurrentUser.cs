using Bcv.Api.Modules.Account.Application;
using Bcv.Api.Modules.Account.Domain;

namespace Bcv.Api.Modules.Account.Api;

/// <summary>The signed-in user behind a request's "Authorization: Bearer" token, for every module's endpoints.</summary>
public static class CurrentUser
{
    public static string? Token(HttpRequest request)
    {
        var value = request.Headers.Authorization.ToString();
        return value.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase) ? value[7..] : null;
    }

    public static PublicAccount? Get(HttpRequest request, ISessionStore sessions) => sessions.Get(Token(request));
}
