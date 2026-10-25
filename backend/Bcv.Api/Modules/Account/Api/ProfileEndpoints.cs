using Bcv.Api.Modules.Account.Application;
using Bcv.Api.Modules.Shared.Api;

namespace Bcv.Api.Modules.Account.Api;

public static class ProfileEndpoints
{
    public static void MapProfileEndpoints(this WebApplication app)
    {
        app.MapGet("/api/account/profile", (HttpRequest request, ISessionStore sessions, ProfileService profile,
            ILoggerFactory logs, CancellationToken ct) => Run(request, sessions, logs, user => profile.LoadAsync(user, ct)));
        app.MapPut("/api/account/profile", (UpdateProfileRequest body, HttpRequest request, ISessionStore sessions,
            ProfileService profile, ILoggerFactory logs, CancellationToken ct) =>
            Run(request, sessions, logs, user => profile.UpdateAsync(user, body, ct)));
        app.MapPost("/api/account/delete", (HttpRequest request, ISessionStore sessions, ProfileService profile,
            ILoggerFactory logs, CancellationToken ct) => Run(request, sessions, logs, async user =>
        {
            await profile.CloseAccountAsync(user, ct);
            return new { deleted = true };
        }));
    }

    private static async Task<IResult> Run<T>(HttpRequest request, ISessionStore sessions, ILoggerFactory logs,
        Func<long, Task<T>> action)
    {
        var user = CurrentUser.Get(request, sessions);
        if (user is null) return Results.Unauthorized();
        request.HttpContext.Response.Headers.CacheControl = "no-store";
        try { return Results.Ok(await action(user.UserId)); }
        catch (ProfileException ex)
        {
            return Results.Json(new ApiError(ex.Code, ex.Message, ex.Field), statusCode: ex.Code == "NOT_FOUND" ? 404 : 400);
        }
        catch (Exception ex)
        {
            logs.CreateLogger("Profile").LogError(ex, "Profile request failed");
            return Results.Json(new ApiError("SERVER_ERROR", "Máy chủ đang gặp lỗi. Vui lòng thử lại."), statusCode: 500);
        }
    }
}
